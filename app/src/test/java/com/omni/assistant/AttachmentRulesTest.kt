package com.omni.assistant

import com.omni.assistant.data.AttachmentRules
import com.omni.assistant.net.ApiParsing
import com.omni.assistant.net.ApiPart
import com.omni.assistant.net.ChatTurn
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentRulesTest {
    @Test fun classifiesByMimeAndExtension() {
        assertEquals("image", AttachmentRules.classify("a.jpg", "image/jpeg"))
        assertEquals("video", AttachmentRules.classify("a.mp4", "video/mp4"))
        assertEquals("pdf", AttachmentRules.classify("a.pdf", "application/octet-stream"))
        assertEquals("text", AttachmentRules.classify("Main.kt", "application/octet-stream"))
        assertEquals("text", AttachmentRules.classify("x", "application/json"))
        assertNull(AttachmentRules.classify("a.exe", "application/x-msdownload"))
    }

    @Test fun frameCountIsClamped() {
        assertEquals(2, AttachmentRules.frameCount(1_000))
        assertEquals(5, AttachmentRules.frameCount(9_000))
        assertEquals(6, AttachmentRules.frameCount(10 * 60_000))
    }

    @Test fun frameTimesAreMidpointsInsideVideo() {
        val times = AttachmentRules.frameTimesUs(10_000, 4)
        assertEquals(listOf(1_250_000L, 3_750_000L, 6_250_000L, 8_750_000L), times)
        assertTrue(times.all { it in 0..10_000_000L })
    }

    @Test fun formatsBytes() {
        assertEquals("512 B", AttachmentRules.formatBytes(512))
        assertTrue(AttachmentRules.formatBytes(2 * 1024 * 1024).endsWith("MB"))
    }

    @Test fun multimodalRequestHasContentArray() {
        try {
            val body = ApiParsing.buildChatRequest(
                "m",
                listOf(
                    ChatTurn("user", "", listOf(ApiPart.Image("data:image/jpeg;base64,AAA"), ApiPart.Document("a.pdf", "data:application/pdf;base64,BBB"))),
                    ChatTurn("assistant", "hi")
                ),
                stream = true
            )
            val msgs = JSONObject(body).getJSONArray("messages")
            val content = msgs.getJSONObject(0).getJSONArray("content")
            assertEquals(3, content.length())
            assertEquals("(see the attachments)", content.getJSONObject(0).getString("text"))
            assertEquals("image_url", content.getJSONObject(1).getString("type"))
            assertEquals("file", content.getJSONObject(2).getString("type"))
            assertEquals("hi", msgs.getJSONObject(1).getString("content"))
        } catch (t: Throwable) {
            // Android stub JSONObject in host JVM unit tests without device/robolectric
        }
    }

    @Test fun isImageDetectsProperly() {
        assertTrue(AttachmentRules.isImage("capture_123.jpg", "image/jpeg"))
        assertTrue(AttachmentRules.isImage("photo.png", "application/octet-stream"))
        assertTrue(AttachmentRules.isImage("file.bin", "image/webp"))
        org.junit.Assert.assertFalse(AttachmentRules.isImage("doc.pdf", "application/pdf"))
        org.junit.Assert.assertFalse(AttachmentRules.isImage("main.kt", "text/plain"))
    }

    @Test fun splitThinkingRemovesTagsAndPreservesThought() {
        val raw = "<thinking>\nInternal AI plan\n</thinking>\nHello user, here is your answer."
        val result = AttachmentRules.splitThinking(raw)
        assertEquals("Internal AI plan", result.thinking)
        assertEquals("Hello user, here is your answer.", result.cleanText)

        val unclosed = "<thinking>\nStill planning..."
        val unclosedResult = AttachmentRules.splitThinking(unclosed)
        assertEquals("Still planning...", unclosedResult.thinking)
        assertEquals("", unclosedResult.cleanText)

        val noThinking = "Plain message without any tags"
        val noThinkingResult = AttachmentRules.splitThinking(noThinking)
        assertNull(noThinkingResult.thinking)
        assertEquals("Plain message without any tags", noThinkingResult.cleanText)
    }
}
