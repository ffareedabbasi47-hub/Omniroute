package com.omni.assistant.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omni.assistant.net.BridgeLine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(vm: AgentViewModel, onBack: () -> Unit, onSettings: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }

    AppBackground {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Claude Code", fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !ui.busy) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reconnect to bridge")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BridgeCard(ui, onSettings)

            ui.error?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = vm::dismissError) { Text("Dismiss") }
                    }
                }
            }

            if (ui.busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(ui.busyLabel, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = vm::cancelTask) { Text("Cancel") }
                }
            }

            when (ui.phase) {
                AgentPhase.SETUP -> SetupSection(ui, vm)
                AgentPhase.PLANNING -> StepTitle("1. Planning (read-only)")
                AgentPhase.PLAN_READY -> PlanSection(ui, vm)
                AgentPhase.APPLYING -> StepTitle("2. Applying approved plan")
                AgentPhase.REVIEW -> ReviewSection(ui, vm)
            }

            if (ui.lines.isNotEmpty()) {
                HorizontalDivider()
                Text("Log (secrets are redacted)", style = MaterialTheme.typography.titleSmall)
                LogView(ui.lines)
            }
        }
    }
    }

    ui.pendingCommand?.let { cmd ->
        AlertDialog(
            onDismissRequest = vm::denyCommand,
            title = { Text("Run this command?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Project: ${ui.workspace}")
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            cmd.display,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp).horizontalScroll(rememberScrollState())
                        )
                    }
                    Text(
                        "It runs on the bridge machine inside that project folder and may change files there.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = { TextButton(onClick = vm::approveCommand) { Text("Run") } },
            dismissButton = { TextButton(onClick = vm::denyCommand) { Text("Deny") } }
        )
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun BridgeCard(ui: AgentUi, onSettings: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val (label, color) = when (ui.connectionOk) {
                true -> "Bridge connected" to MaterialTheme.colorScheme.secondary
                false -> "Bridge not reachable" to MaterialTheme.colorScheme.error
                null -> "Not checked yet" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(label, style = MaterialTheme.typography.titleSmall, color = color)
            ui.connectionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (ui.connectionOk != true) {
                Text(
                    "This feature needs the agent bridge from the README running next to Claude Code, " +
                        "plus its URL and token in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onSettings) { Text("Open bridge settings") }
            }
        }
    }
}

@Composable
private fun SetupSection(ui: AgentUi, vm: AgentViewModel) {
    StepTitle("New coding task")
    if (ui.workspaces.isEmpty()) {
        Text(
            if (ui.connectionOk == true) "The bridge has no projects configured. Add one to its config.json."
            else "Connect to the bridge to choose a project.",
            style = MaterialTheme.typography.bodyMedium
        )
    } else {
        Text("Project (allowed folders only)", style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (w in ui.workspaces) {
                FilterChip(selected = ui.workspace == w, onClick = { vm.selectWorkspace(w) }, label = { Text(w) })
            }
        }
    }
    OutlinedTextField(
        value = ui.prompt,
        onValueChange = vm::setPrompt,
        label = { Text("What should Claude Code do?") },
        minLines = 3,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        "Step 1 only reads your project and proposes a plan. Nothing is changed until you approve it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    GradientButton(
        "Create plan",
        onClick = vm::startPlan,
        enabled = !ui.busy && ui.connectionOk == true && ui.workspace.isNotBlank() && ui.prompt.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PlanSection(ui: AgentUi, vm: AgentViewModel) {
    StepTitle("Proposed plan")
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp)) { MarkdownView(ui.plan) }
    }
    Text(
        "Approving lets Claude Code edit files inside \"${ui.workspace}\" only. It cannot run shell commands in this step. " +
            "You will see the diff before running anything.",
        style = MaterialTheme.typography.bodySmall
    )
    GradientButton("Approve and apply edits", onClick = vm::approveAndApply, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = vm::discardPlan, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Discard plan") }
}

@Composable
private fun ReviewSection(ui: AgentUi, vm: AgentViewModel) {
    StepTitle("3. Review changes")
    if (ui.diffStatus.isNotBlank()) {
        Text("Changed files:", style = MaterialTheme.typography.labelLarge)
        Text(ui.diffStatus, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
    val diff = ui.diff
    if (diff.isNullOrBlank()) {
        Text("No tracked-file differences were reported by git (new files appear in the list above).")
    } else {
        DiffView(diff)
        if (ui.diffTruncated) Text("Diff truncated for size. Review the rest on the bridge machine.", style = MaterialTheme.typography.bodySmall)
    }
    OutlinedButton(onClick = vm::reloadDiff, enabled = !ui.busy) { Text("Refresh diff") }

    HorizontalDivider()
    Text("Verify (each command needs your approval)", style = MaterialTheme.typography.titleSmall)
    if (ui.commands.isEmpty()) {
        Text(
            "The bridge has no verification commands configured, so nothing can be tested from here.",
            style = MaterialTheme.typography.bodySmall
        )
    }
    for (c in ui.commands) {
        OutlinedButton(
            onClick = { vm.requestCommand(c) },
            enabled = !ui.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(c.label) }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "To undo the edits, use git in the project (for example git restore) on the bridge machine; the app never runs destructive git commands.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(onClick = vm::newTask, enabled = !ui.busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text("Finish and start a new task")
    }
}

@Composable
private fun DiffView(diff: String) {
    val add = Color(0xFF2E7D32)
    val del = Color(0xFFC62828)
    val hunk = MaterialTheme.colorScheme.primary
    val text = remember(diff) {
        buildAnnotatedString {
            for (line in diff.lines()) {
                val color = when {
                    line.startsWith("+++") || line.startsWith("---") -> hunk
                    line.startsWith("+") -> add
                    line.startsWith("-") -> del
                    line.startsWith("@@") -> hunk
                    else -> Color.Unspecified
                }
                withStyle(SpanStyle(color = color)) { append(line) }
                append("\n")
            }
        }
    }
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF0B0A12), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)
        )
    }
}

@Composable
private fun LogView(lines: List<BridgeLine>) {
    val shown = lines.takeLast(200)
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF0B0A12), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (l in shown) {
                val color = when (l.kind) {
                    "error" -> MaterialTheme.colorScheme.error
                    "tool" -> MaterialTheme.colorScheme.secondary
                    "info" -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(l.text, color = color, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
