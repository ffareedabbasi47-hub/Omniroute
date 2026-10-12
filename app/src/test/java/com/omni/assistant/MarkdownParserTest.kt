package com.omni.assistant

import com.omni.assistant.ui.MarkdownParser
import com.omni.assistant.ui.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun parsesFencedCodeWithLanguage() {
        val blocks = MarkdownParser.parse("Intro\n```kotlin\nval x = 1\nprintln(x)\n```\nDone")
        assertEquals(3, blocks.size)
        assertEquals(MdBlock.Paragraph("Intro"), blocks[0])
        assertEquals(MdBlock.Code("kotlin", "val x = 1\nprintln(x)"), blocks[1])
        assertEquals(MdBlock.Paragraph("Done"), blocks[2])
    }

    @Test
    fun unclosedFenceDuringStreamingBecomesCodeBlock() {
        val blocks = MarkdownParser.parse("```python\nprint('hi')")
        assertEquals(listOf<MdBlock>(MdBlock.Code("python", "print('hi')")), blocks)
    }

    @Test
    fun parsesHeadingsAndLists() {
        val blocks = MarkdownParser.parse("# Title\n- one\n* two\n1. first\n2) second")
        assertEquals(MdBlock.Heading(1, "Title"), blocks[0])
        assertEquals(MdBlock.Bullet("•", "one"), blocks[1])
        assertEquals(MdBlock.Bullet("•", "two"), blocks[2])
        assertEquals(MdBlock.Bullet("1.", "first"), blocks[3])
        assertEquals(MdBlock.Bullet("2)", "second"), blocks[4])
    }

    @Test
    fun boldTextIsNotMistakenForABullet() {
        val blocks = MarkdownParser.parse("**Note** this matters")
        assertEquals(listOf<MdBlock>(MdBlock.Paragraph("**Note** this matters")), blocks)
    }

    @Test
    fun speakableTextDropsCodeAndMarkup() {
        val spoken = MarkdownParser.toSpeakable("## Hi\nUse `foo` and **bar**.\n```\nsecret code\n```")
        assertFalse(spoken.contains("secret code"))
        assertFalse(spoken.contains("**"))
        assertFalse(spoken.contains("`"))
        assertTrue(spoken.contains("foo"))
    }

    @Test
    fun hindiTextSurvivesParsing() {
        val blocks = MarkdownParser.parse("नमस्ते दोस्त")
        assertEquals(listOf<MdBlock>(MdBlock.Paragraph("नमस्ते दोस्त")), blocks)
    }
}
