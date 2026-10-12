package com.omni.assistant.net

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Extra content sent alongside a turn's text, using OpenAI-compatible chat-completions parts. */
sealed interface ApiPart {
    /** An image as a data: URL (also used for sampled video frames). */
    data class Image(val dataUrl: String) : ApiPart

    /** A document (currently PDF) as a data: URL. Only some backends/models accept this. */
    data class Document(val filename: String, val dataUrl: String) : ApiPart
}

data class ChatTurn(val role: String, val content: String, val parts: List<ApiPart> = emptyList())

sealed interface SseEvent {
    data class Delta(val text: String) : SseEvent
    data object Done : SseEvent
    data object Ignore : SseEvent
    data class Error(val message: String) : SseEvent
}

object UrlUtils {
    private val loopbackHosts = setOf("localhost", "127.0.0.1", "::1", "[::1]", "10.0.2.2")

    /** Trims, drops trailing slashes and a pasted-in endpoint suffix. */
    fun normalizeBaseUrl(input: String): String {
        var s = input.trim()
        while (s.endsWith("/")) s = s.dropLast(1)
        for (suffix in listOf("/chat/completions", "/models")) {
            if (s.endsWith(suffix)) s = s.removeSuffix(suffix)
        }
        while (s.endsWith("/")) s = s.dropLast(1)
        return s
    }

    /** Returns an error message, or null if the URL is usable. */
    fun validate(input: String): String? {
        val s = normalizeBaseUrl(input)
        if (s.isEmpty()) return "Base URL is required."
        if (s.toHttpUrlOrNull() == null) return "Enter a full http:// or https:// URL."
        return null
    }

    /** True for plain http:// to a host that is not on this device/emulator. */
    fun isInsecureRemote(input: String): Boolean {
        val u = normalizeBaseUrl(input).toHttpUrlOrNull() ?: return false
        return u.scheme == "http" && u.host !in loopbackHosts
    }
}

object Redactor {
    private val bearer = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+")
    private val skKey = Regex("sk-[A-Za-z0-9_-]{8,}")
    private val assignment = Regex("(?i)(api[_-]?key|token|secret|password|authorization)(\"?\\s*[:=]\\s*\"?)[^\\s\",}]+")

    fun redact(text: String, secrets: Collection<String> = emptyList()): String {
        var out = text
        for (s in secrets) {
            if (s.length >= 4) out = out.replace(s, "[REDACTED]")
        }
        out = bearer.replace(out, "Bearer [REDACTED]")
        out = skKey.replace(out, "[REDACTED]")
        out = assignment.replace(out) { m -> m.groupValues[1] + m.groupValues[2] + "[REDACTED]" }
        return out
    }
}

object ApiParsing {

    fun buildChatRequest(model: String, turns: List<ChatTurn>, stream: Boolean): String {
        val messages = JSONArray()
        for (t in turns) {
            val msg = JSONObject().put("role", t.role)
            if (t.parts.isEmpty()) {
                msg.put("content", t.content)
            } else {
                val content = JSONArray()
                content.put(
                    JSONObject().put("type", "text").put("text", t.content.ifBlank { "(see the attachments)" })
                )
                for (part in t.parts) {
                    when (part) {
                        is ApiPart.Image -> content.put(
                            JSONObject().put("type", "image_url")
                                .put("image_url", JSONObject().put("url", part.dataUrl))
                        )
                        is ApiPart.Document -> content.put(
                            JSONObject().put("type", "file")
                                .put("file", JSONObject().put("filename", part.filename).put("file_data", part.dataUrl))
                        )
                    }
                }
                msg.put("content", content)
            }
            messages.put(msg)
        }
        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("stream", stream)
            .toString()
    }

    /** Parses one line of a server-sent-events stream (OpenAI chat-completions format). */
    fun parseSseLine(line: String): SseEvent {
        if (!line.startsWith("data:")) return SseEvent.Ignore
        val payload = line.substring(5).trim()
        if (payload.isEmpty()) return SseEvent.Ignore
        if (payload == "[DONE]") return SseEvent.Done
        return try {
            parsePayload(payload)
        } catch (e: JSONException) {
            SseEvent.Ignore
        }
    }

    private fun parsePayload(payload: String): SseEvent {
        val obj = JSONObject(payload)
        if (obj.has("error") && !obj.isNull("error")) {
            return SseEvent.Error(errorText(obj.get("error")))
        }
        val choices = obj.optJSONArray("choices") ?: return SseEvent.Ignore
        if (choices.length() == 0) return SseEvent.Ignore
        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return SseEvent.Ignore
        if (!delta.has("content") || delta.isNull("content")) return SseEvent.Ignore
        val text = delta.getString("content")
        return if (text.isEmpty()) SseEvent.Ignore else SseEvent.Delta(text)
    }

    /** Non-streaming reply: choices[0].message.content, or null if absent/blank. */
    fun parseCompletion(body: String): String? {
        val obj = JSONObject(body)
        if (obj.has("error") && !obj.isNull("error")) {
            throw IllegalStateException(errorText(obj.get("error")))
        }
        val choices = obj.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val message = choices.optJSONObject(0)?.optJSONObject("message") ?: return null
        if (!message.has("content") || message.isNull("content")) return null
        val text = message.getString("content")
        return text.ifBlank { null }
    }

    /** Model ids from an OpenAI-style GET /models response. */
    fun parseModels(body: String): List<String> {
        val obj = JSONObject(body)
        val data = obj.optJSONArray("data") ?: return emptyList()
        val ids = ArrayList<String>()
        for (i in 0 until data.length()) {
            val id = data.optJSONObject(i)?.optString("id", "").orEmpty()
            if (id.isNotBlank()) ids.add(id)
        }
        return ids.distinct().sorted()
    }

    /** Best-effort human message from an error response body; null if nothing useful. */
    fun errorMessage(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        try {
            val obj = JSONObject(trimmed)
            if (obj.has("error") && !obj.isNull("error")) return errorText(obj.get("error")).take(300)
            if (obj.has("message") && !obj.isNull("message")) return obj.getString("message").take(300)
            return null
        } catch (e: JSONException) {
            if (trimmed.startsWith("<")) return null // HTML error page
            return trimmed.take(200)
        }
    }

    private fun errorText(error: Any): String {
        return if (error is JSONObject) {
            if (error.has("message") && !error.isNull("message")) error.getString("message") else error.toString()
        } else {
            error.toString()
        }
    }
}
