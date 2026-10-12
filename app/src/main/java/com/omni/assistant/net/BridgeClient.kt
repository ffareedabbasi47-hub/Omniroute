package com.omni.assistant.net

import com.omni.assistant.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class BridgeLine(val kind: String, val text: String)

data class BridgeTask(
    val status: String,
    val lines: List<BridgeLine>,
    val next: Int,
    val exitCode: Int?,
    val result: String?
) {
    val finished: Boolean get() = status != "running"
}

data class BridgeCommand(val id: String, val label: String, val display: String)

data class BridgeDiff(val diff: String, val status: String, val truncated: Boolean)

/**
 * Talks to the separate coding-agent bridge (agent-bridge/bridge.py), which runs next to Claude Code
 * (for example in Termux on this phone, or on a computer on your network). The app never runs shell
 * commands itself; it only sends requests to the bridge, authenticated with a bearer token.
 */
class BridgeClient(private val settings: SettingsStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val s = settings.current
            val base = UrlUtils.normalizeBaseUrl(s.bridgeUrl)
            if (UrlUtils.validate(base) != null) {
                throw ApiException(ApiException.Kind.NOT_CONFIGURED, "Set the bridge URL in Settings.")
            }
            if (s.bridgeToken.isBlank()) {
                throw ApiException(ApiException.Kind.NOT_CONFIGURED, "Set the bridge token in Settings.")
            }
            val b = try {
                Request.Builder().url(base + path)
            } catch (e: IllegalArgumentException) {
                throw ApiException(ApiException.Kind.NOT_CONFIGURED, "Bridge URL is not valid.")
            }
            b.header("Authorization", "Bearer " + s.bridgeToken.trim())
            if (method == "POST") {
                val payload = (body ?: JSONObject()).toString()
                b.post(payload.toRequestBody("application/json".toMediaType()))
            } else {
                b.get()
            }
            http.newCall(b.build()).awaitResponse().use { resp ->
                val text = try {
                    resp.body?.string().orEmpty()
                } catch (e: IOException) {
                    throw mapIoException(e)
                }
                if (!resp.isSuccessful) {
                    val err = ApiParsing.errorMessage(text)
                    if (resp.code == 401) {
                        throw ApiException(ApiException.Kind.AUTH, "The bridge rejected the token.")
                    }
                    throw ApiException(
                        ApiException.Kind.CLIENT,
                        "Bridge error (HTTP ${resp.code})" + (if (err.isNullOrBlank()) "" else ": " + Redactor.redact(err))
                    )
                }
                try {
                    JSONObject(text)
                } catch (e: JSONException) {
                    throw ApiException(ApiException.Kind.BAD_RESPONSE, "The bridge sent an invalid reply.")
                }
            }
        }

    suspend fun health(): String {
        val o = call("GET", "/health")
        val claude = if (o.isNull("claude")) "not found on the bridge host" else o.optString("claude")
        return "Bridge OK. Claude Code: $claude"
    }

    suspend fun workspaces(): List<String> {
        val arr = call("GET", "/workspaces").optJSONArray("workspaces") ?: return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    suspend fun commands(): List<BridgeCommand> {
        val arr = call("GET", "/commands").optJSONArray("commands") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONObject(i) ?: return@mapNotNull null
            BridgeCommand(c.optString("id"), c.optString("label"), c.optString("display"))
        }
    }

    /** mode is "plan" (read-only analysis) or "edit" (file edits only, no shell). */
    suspend fun startTask(workspace: String, prompt: String, mode: String): String {
        val o = call(
            "POST", "/tasks",
            JSONObject().put("workspace", workspace).put("prompt", prompt).put("mode", mode)
        )
        return o.getString("id")
    }

    suspend fun runCommand(workspace: String, commandId: String): String {
        val o = call("POST", "/run", JSONObject().put("workspace", workspace).put("command_id", commandId))
        return o.getString("id")
    }

    suspend fun poll(taskId: String, since: Int): BridgeTask {
        val o = call("GET", "/tasks/$taskId?since=$since")
        val arr = o.optJSONArray("lines")
        val lines = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
            val l = arr.optJSONObject(i) ?: return@mapNotNull null
            BridgeLine(l.optString("kind", "text"), l.optString("text"))
        }
        return BridgeTask(
            status = o.optString("status", "running"),
            lines = lines,
            next = o.optInt("next", since + lines.size),
            exitCode = if (o.isNull("exit_code")) null else o.optInt("exit_code"),
            result = if (o.isNull("result")) null else o.optString("result")
        )
    }

    suspend fun cancel(taskId: String) {
        call("POST", "/tasks/$taskId/cancel")
    }

    suspend fun diff(workspace: String): BridgeDiff {
        val name = java.net.URLEncoder.encode(workspace, "UTF-8")
        val o = call("GET", "/diff?workspace=$name")
        return BridgeDiff(o.optString("diff"), o.optString("status"), o.optBoolean("truncated", false))
    }
}
