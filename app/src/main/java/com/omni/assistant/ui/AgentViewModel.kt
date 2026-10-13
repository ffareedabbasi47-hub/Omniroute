package com.omni.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omni.assistant.OmniApp
import com.omni.assistant.net.ApiException
import com.omni.assistant.net.BridgeCommand
import com.omni.assistant.net.BridgeLine
import com.omni.assistant.net.BridgeTask
import com.omni.assistant.net.Redactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class AgentPhase { SETUP, PLANNING, PLAN_READY, APPLYING, REVIEW }

data class AgentUi(
    val connectionOk: Boolean? = null,
    val connectionMessage: String? = null,
    val workspaces: List<String> = emptyList(),
    val commands: List<BridgeCommand> = emptyList(),
    val workspace: String = "",
    val prompt: String = "",
    val phase: AgentPhase = AgentPhase.SETUP,
    val busy: Boolean = false,
    val busyLabel: String = "",
    val lines: List<BridgeLine> = emptyList(),
    val plan: String = "",
    val diff: String? = null,
    val diffStatus: String = "",
    val diffTruncated: Boolean = false,
    /** Command waiting for the user's explicit approval. */
    val pendingCommand: BridgeCommand? = null,
    val error: String? = null
)

/**
 * Drives the coding-agent workflow against the bridge. Nothing is executed without a user tap:
 *  1. PLAN    - Claude Code runs read-only (plan mode) and proposes changes.
 *  2. APPLY   - only after "Approve", Claude Code runs again allowed to edit files (no shell).
 *  3. REVIEW  - the git diff is fetched and shown.
 *  4. COMMAND - tests/builds are run only from the bridge's allowlist, one approval dialog each.
 */
class AgentViewModel(app: Application) : AndroidViewModel(app) {
    private val omni = app as OmniApp
    private val bridge = omni.bridge

    private val _ui = MutableStateFlow(AgentUi(workspace = omni.settings.current.lastWorkspace))
    val ui: StateFlow<AgentUi> = _ui.asStateFlow()

    private var job: Job? = null
    private var currentTaskId: String? = null

    private fun secrets(): List<String> {
        val s = omni.settings.current
        return listOf(s.apiKey, s.bridgeToken)
    }

    private fun friendly(e: Exception): String = when (e) {
        is ApiException -> e.message.orEmpty()
        else -> "Unexpected error (${e.javaClass.simpleName})."
    }

    fun setPrompt(text: String) = _ui.update { it.copy(prompt = text) }

    fun selectWorkspace(name: String) {
        omni.settings.update { it.copy(lastWorkspace = name) }
        _ui.update { it.copy(workspace = name) }
    }

    fun dismissError() = _ui.update { it.copy(error = null) }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, busyLabel = "Contacting bridge…", error = null) }
            try {
                val info = bridge.health()
                val ws = bridge.workspaces()
                val cmds = bridge.commands()
                val saved = omni.settings.current.lastWorkspace
                val chosen = if (saved in ws) saved else ws.firstOrNull().orEmpty()
                _ui.update {
                    it.copy(
                        connectionOk = true, connectionMessage = info, workspaces = ws,
                        commands = cmds, workspace = chosen, busy = false
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(connectionOk = false, connectionMessage = friendly(e), busy = false) }
            }
        }
    }

    // ---- 1. Plan ------------------------------------------------------------------------------

    fun startPlan() {
        val st = _ui.value
        if (st.busy || st.workspace.isBlank() || st.prompt.isBlank()) return
        _ui.update {
            it.copy(
                phase = AgentPhase.PLANNING, busy = true, busyLabel = "Claude Code is reading the project (read-only)…",
                lines = emptyList(), plan = "", diff = null, error = null
            )
        }
        job = viewModelScope.launch {
            try {
                val id = bridge.startTask(st.workspace, st.prompt, "plan")
                val result = follow(id)
                val planText = (result.result ?: "").ifBlank { lastTextLines() }
                if (result.status == "done" && planText.isNotBlank()) {
                    _ui.update { it.copy(phase = AgentPhase.PLAN_READY, busy = false, plan = planText) }
                } else {
                    val why = when (result.status) {
                        "cancelled" -> "Cancelled."
                        "timeout" -> "The task timed out."
                        else -> "Claude Code did not produce a plan (status: ${result.status}). See the log."
                    }
                    _ui.update { it.copy(phase = AgentPhase.SETUP, busy = false, error = why) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(phase = AgentPhase.SETUP, busy = false, error = friendly(e)) }
            }
        }
    }

    fun discardPlan() {
        _ui.update { it.copy(phase = AgentPhase.SETUP, plan = "", lines = emptyList(), error = null) }
    }

    // ---- 2. Apply (only after approval) -------------------------------------------------------

    fun approveAndApply() {
        val st = _ui.value
        if (st.busy || st.phase != AgentPhase.PLAN_READY) return
        _ui.update {
            it.copy(phase = AgentPhase.APPLYING, busy = true, busyLabel = "Applying the approved plan…", error = null)
        }
        val applyPrompt = "Carry out this request, following the approved plan. Edit only files inside this " +
            "project and change only what is needed. Do not run shell commands.\n\nRequest:\n${st.prompt}\n\n" +
            "Approved plan:\n${st.plan}"
        job = viewModelScope.launch {
            try {
                val id = bridge.startTask(st.workspace, applyPrompt, "edit")
                val result = follow(id)
                val d = bridge.diff(st.workspace)
                _ui.update {
                    it.copy(
                        phase = AgentPhase.REVIEW, busy = false, diff = d.diff,
                        diffStatus = d.status, diffTruncated = d.truncated,
                        error = if (result.status == "done") null else "Editing ended with status: ${result.status}."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(phase = AgentPhase.REVIEW, busy = false, error = friendly(e)) }
            }
        }
    }

    fun reloadDiff() {
        val ws = _ui.value.workspace
        viewModelScope.launch {
            try {
                val d = bridge.diff(ws)
                _ui.update { it.copy(diff = d.diff, diffStatus = d.status, diffTruncated = d.truncated) }
            } catch (e: Exception) {
                _ui.update { it.copy(error = friendly(e)) }
            }
        }
    }

    // ---- 3. Approved commands -------------------------------------------------------------------

    fun requestCommand(c: BridgeCommand) {
        if (_ui.value.busy) return
        _ui.update { it.copy(pendingCommand = c) }
    }

    fun denyCommand() {
        _ui.update { it.copy(pendingCommand = null) }
    }

    /** Called only from the approval dialog's confirm button. */
    fun approveCommand() {
        val st = _ui.value
        val cmd = st.pendingCommand ?: return
        _ui.update {
            it.copy(pendingCommand = null, busy = true, busyLabel = "Running ${cmd.label}…", error = null)
        }
        job = viewModelScope.launch {
            try {
                append(BridgeLine("info", "$ ${cmd.display}   (approved by you)"))
                val id = bridge.runCommand(st.workspace, cmd.id)
                val result = follow(id)
                val verdict = when {
                    result.status == "done" && result.exitCode == 0 -> "PASSED (exit code 0)"
                    result.status == "timeout" -> "TIMED OUT"
                    result.status == "cancelled" -> "CANCELLED"
                    else -> "FAILED (exit code ${result.exitCode ?: "unknown"})"
                }
                append(BridgeLine("info", "${cmd.label}: $verdict"))
                _ui.update { it.copy(busy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, error = friendly(e)) }
            }
        }
    }

    // ---- Cancel / reset -------------------------------------------------------------------------

    fun cancelTask() {
        val id = currentTaskId
        viewModelScope.launch {
            if (id != null) {
                try {
                    bridge.cancel(id)
                } catch (_: Exception) {
                }
            }
        }
        // The polling loop sees the "cancelled" status and finishes on its own; if the bridge is
        // unreachable, stop waiting locally as well.
        job?.cancel()
        _ui.update {
            it.copy(
                busy = false,
                phase = if (it.phase == AgentPhase.PLANNING) AgentPhase.SETUP else if (it.phase == AgentPhase.APPLYING) AgentPhase.REVIEW else it.phase
            )
        }
    }

    fun newTask() {
        _ui.update {
            it.copy(phase = AgentPhase.SETUP, prompt = "", plan = "", diff = null, lines = emptyList(), error = null)
        }
    }

    fun pushToGit() {
        val st = _ui.value
        val ws = st.workspace.ifBlank { "OmniAssistant" }
        _ui.update { it.copy(busy = true, busyLabel = "Pushing changes to GitHub repository…", error = null) }
        viewModelScope.launch {
            try {
                append(BridgeLine("info", "$ git push origin main (triggered from Omni Assistant)"))
                val res = bridge.gitPush(ws)
                val ok = res.optBoolean("ok", false)
                val commit = res.optString("commit", "")
                val push = res.optString("push", "")
                if (commit.isNotBlank()) append(BridgeLine("info", commit))
                if (push.isNotBlank()) append(BridgeLine("info", push))
                if (ok) {
                    append(BridgeLine("info", "✅ Successfully pushed to GitHub repository!"))
                } else {
                    val err = res.optString("error", "Push failed")
                    append(BridgeLine("error", "❌ Git push error: $err"))
                }
                _ui.update { it.copy(busy = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, error = friendly(e)) }
            }
        }
    }

    // ---- Polling --------------------------------------------------------------------------------

    private fun append(line: BridgeLine) {
        val safe = BridgeLine(line.kind, Redactor.redact(line.text, secrets()))
        _ui.update { it.copy(lines = (it.lines + safe).takeLast(MAX_LINES)) }
    }

    private fun lastTextLines(): String =
        _ui.value.lines.filter { it.kind == "text" }.joinToString("\n") { it.text }

    /** Polls until the task finishes, appending (redacted) log lines. Gives up after the timeout. */
    private suspend fun follow(taskId: String): BridgeTask {
        currentTaskId = taskId
        var since = 0
        var last: BridgeTask? = null
        try {
            val finished = withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
                while (true) {
                    val t = bridge.poll(taskId, since)
                    for (l in t.lines) append(l)
                    since = t.next
                    last = t
                    if (t.finished) break
                    delay(POLL_MS)
                }
                true
            }
            if (finished == null) {
                try {
                    bridge.cancel(taskId)
                } catch (_: Exception) {
                }
                return BridgeTask("timeout", emptyList(), since, null, null)
            }
            return last ?: BridgeTask("failed", emptyList(), since, null, null)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                try {
                    bridge.cancel(taskId)
                } catch (_: Exception) {
                }
            }
            throw e
        } finally {
            currentTaskId = null
        }
    }

    private companion object {
        const val POLL_MS = 1000L
        const val OVERALL_TIMEOUT_MS = 15 * 60 * 1000L
        const val MAX_LINES = 600
    }
}
