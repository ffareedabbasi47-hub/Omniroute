package com.omni.assistant

import com.omni.assistant.net.ApiParsing
import com.omni.assistant.net.ChatTurn
import com.omni.assistant.net.Redactor
import com.omni.assistant.net.SseEvent
import com.omni.assistant.net.UrlUtils
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiParsingTest {

    @Test
    fun normalizeBaseUrl_stripsSlashesAndPastedEndpoints() {
        assertEquals("http://127.0.0.1:20128/v1", UrlUtils.normalizeBaseUrl("  http://127.0.0.1:20128/v1/ "))
        assertEquals("http://h/v1", UrlUtils.normalizeBaseUrl("http://h/v1/chat/completions"))
        assertEquals("http://h/v1", UrlUtils.normalizeBaseUrl("http://h/v1/models/"))
    }

    @Test
    fun validate_rejectsEmptyAndNonUrls() {
        assertNotNull(UrlUtils.validate(""))
        assertNotNull(UrlUtils.validate("not a url"))
        assertNotNull(UrlUtils.validate("ftp://example.com"))
        assertNull(UrlUtils.validate("https://example.com/v1"))
        assertNull(UrlUtils.validate("http://192.168.1.5:20128/v1"))
    }

    @Test
    fun insecureRemote_onlyFlagsPlainHttpToOtherHosts() {
        assertFalse(UrlUtils.isInsecureRemote("http://127.0.0.1:20128/v1"))
        assertFalse(UrlUtils.isInsecureRemote("http://localhost:20128/v1"))
        assertFalse(UrlUtils.isInsecureRemote("https://example.com/v1"))
        assertTrue(UrlUtils.isInsecureRemote("http://192.168.1.5:20128/v1"))
        assertTrue(UrlUtils.isInsecureRemote("http://example.com/v1"))
    }

    @Test
    fun sse_parsesDeltasDoneAndIgnoresNoise() {
        val delta = ApiParsing.parseSseLine("""data: {"choices":[{"delta":{"content":"Hel"}}]}""")
        assertEquals(SseEvent.Delta("Hel"), delta)
        assertEquals(SseEvent.Done, ApiParsing.parseSseLine("data: [DONE]"))
        assertEquals(SseEvent.Ignore, ApiParsing.parseSseLine(": keep-alive"))
        assertEquals(SseEvent.Ignore, ApiParsing.parseSseLine("event: ping"))
        assertEquals(SseEvent.Ignore, ApiParsing.parseSseLine("data: "))
    }

    @Test
    fun sse_roleOnlyAndNullContentChunksAreIgnored() {
        assertEquals(SseEvent.Ignore, ApiParsing.parseSseLine("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""))
        assertEquals(SseEvent.Ignore, ApiParsing.parseSseLine("""data: {"choices":[{"delta":{"content":null}}]}"""))
        assertEquals(SseEvent.Ignore, ApiParsing.parseSseLine("""data: {"choices":[]}"""))
    }

    @Test
    fun sse_malformedJsonIsIgnoredNotFatal() {
        assertEquals(SseEvent.Ignore, ApiParsing.parseSseLine("data: {not json"))
    }

    @Test
    fun sse_errorPayloadIsSurfaced() {
        val ev = ApiParsing.parseSseLine("""data: {"error":{"message":"quota exceeded"}}""")
        assertEquals(SseEvent.Error("quota exceeded"), ev)
    }

    @Test
    fun sse_keepsHindiAndEmoji() {
        val ev = ApiParsing.parseSseLine("""data: {"choices":[{"delta":{"content":"नमस्ते 👋"}}]}""")
        assertEquals(SseEvent.Delta("नमस्ते 👋"), ev)
    }

    @Test
    fun completion_extractsContent_andHandlesEmpty() {
        assertEquals("Hi", ApiParsing.parseCompletion("""{"choices":[{"message":{"content":"Hi"}}]}"""))
        assertNull(ApiParsing.parseCompletion("""{"choices":[{"message":{"content":""}}]}"""))
        assertNull(ApiParsing.parseCompletion("""{"choices":[]}"""))
        assertNull(ApiParsing.parseCompletion("""{"choices":[{"message":{"content":null}}]}"""))
    }

    @Test(expected = JSONException::class)
    fun completion_malformedJsonThrows() {
        ApiParsing.parseCompletion("<html>502</html>")
    }

    @Test(expected = IllegalStateException::class)
    fun completion_errorObjectThrows() {
        ApiParsing.parseCompletion("""{"error":{"message":"bad model"}}""")
    }

    @Test
    fun models_areSortedAndDeduplicated() {
        val body = """{"data":[{"id":"b/model"},{"id":"a/model"},{"id":"a/model"},{"id":""}]}"""
        assertEquals(listOf("a/model", "b/model"), ApiParsing.parseModels(body))
        assertEquals(emptyList<String>(), ApiParsing.parseModels("""{"object":"list"}"""))
    }

    @Test
    fun errorMessage_readsCommonShapes() {
        assertEquals("Invalid key", ApiParsing.errorMessage("""{"error":{"message":"Invalid key"}}"""))
        assertEquals("plain", ApiParsing.errorMessage("""{"error":"plain"}"""))
        assertEquals("oops", ApiParsing.errorMessage("""{"message":"oops"}"""))
        assertNull(ApiParsing.errorMessage(""))
        assertNull(ApiParsing.errorMessage("<html>Bad gateway</html>"))
    }

    @Test
    fun chatRequest_hasModelMessagesAndStreamFlag() {
        val json = JSONObject(
            ApiParsing.buildChatRequest("my-model", listOf(ChatTurn("user", "hello \"quoted\"")), stream = true)
        )
        assertEquals("my-model", json.getString("model"))
        assertTrue(json.getBoolean("stream"))
        val m = json.getJSONArray("messages").getJSONObject(0)
        assertEquals("user", m.getString("role"))
        assertEquals("hello \"quoted\"", m.getString("content"))
    }

    @Test
    fun redactor_removesKeysTokensAndExplicitSecrets() {
        val text = "Authorization: Bearer abc.DEF-123 and api_key=sk-LIVE1234567890 plus mysecretvalue"
        val out = Redactor.redact(text, listOf("mysecretvalue"))
        assertFalse(out.contains("abc.DEF-123"))
        assertFalse(out.contains("sk-LIVE1234567890"))
        assertFalse(out.contains("mysecretvalue"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun redactor_leavesOrdinaryTextAlone() {
        assertEquals("Compiling 12 files...", Redactor.redact("Compiling 12 files..."))
    }
}
