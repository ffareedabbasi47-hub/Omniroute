package com.omni.assistant

import com.omni.assistant.data.FileExport
import com.omni.assistant.net.ApiException
import com.omni.assistant.net.MediaApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MediaApiTest {
    @Test fun imageRequestHasDocumentedFields() {
        val o = JSONObject(MediaApi.buildImageRequest("m", "a cat", "512x512"))
        assertEquals("m", o.getString("model"))
        assertEquals("a cat", o.getString("prompt"))
        assertEquals(1, o.getInt("n"))
        assertEquals("b64_json", o.getString("response_format"))
        assertEquals("512x512", o.getString("size"))
    }

    @Test fun videoRequestOmitsDurationWhenUnset() {
        assertFalse(JSONObject(MediaApi.buildVideoRequest("m", "p", null)).has("duration"))
        assertEquals(5, JSONObject(MediaApi.buildVideoRequest("m", "p", 5)).getInt("duration"))
    }

    @Test fun parsesB64AndUrlItems() {
        val items = MediaApi.parseResponse(
            """{"created":1,"data":[{"b64_json":"data:image/png;base64,QUJD"},{"url":"https://x.test/a.png"}]}"""
        )
        assertEquals(2, items.size)
        assertEquals("QUJD", items[0].b64)
        assertEquals("https://x.test/a.png", items[1].url)
    }

    @Test fun parsesVideoUrlVariantsAndRejectsNonHttp() {
        assertEquals("https://v.test/a.mp4", MediaApi.parseResponse("""{"data":[{"video_url":"https://v.test/a.mp4"}]}""")[0].url)
        assertEquals("https://v.test/b.mp4", MediaApi.parseResponse("""{"url":"https://v.test/b.mp4"}""")[0].url)
        assertTrue(MediaApi.parseResponse("""{"data":[{"url":"file:///etc/passwd"}]}""").isEmpty())
    }

    @Test fun emptyOrErrorResponses() {
        assertTrue(MediaApi.parseResponse("""{"data":[]}""").isEmpty())
        try {
            MediaApi.parseResponse("""{"error":{"message":"content_policy_violation"}}""")
            fail("expected error")
        } catch (e: IllegalStateException) {
            assertEquals("content_policy_violation", e.message)
        }
        try {
            MediaApi.safeParse("<html>")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiException.Kind.BAD_RESPONSE, e.kind)
        }
    }

    @Test fun sniffsKnownFormats() {
        fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
        assertEquals("png", MediaApi.sniff(b(0x89, 0x50, 0x4E, 0x47, 0, 0))!!.ext)
        assertEquals("jpg", MediaApi.sniff(b(0xFF, 0xD8, 0xFF, 0xE0))!!.ext)
        assertEquals("mp4", MediaApi.sniff(b(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70))!!.ext)
        assertEquals("webp", MediaApi.sniff(b(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50))!!.ext)
        assertNull(MediaApi.sniff(b(1, 2, 3)))
        assertNull(MediaApi.sniff(ByteArray(0)))
    }

    @Test fun fileExportHelpers() {
        assertEquals("kt", FileExport.extensionFor("Kotlin"))
        assertEquals("txt", FileExport.extensionFor("brainfuck"))
        assertEquals("main.py", FileExport.fileNameFor("python", "Save this as main.py:", 0))
        assertEquals("snippet_2.sh", FileExport.fileNameFor("bash", null, 1))
        val plain = FileExport.markdownToPlain("# Title\n**bold** and `code`\n- item\n```py\nprint(1)\n```")
        assertTrue(plain.startsWith("Title"))
        assertTrue(plain.contains("bold and code"))
        assertTrue(plain.contains("• item"))
        assertTrue(plain.contains("    print(1)"))
        assertNotNull(plain)
    }
}
