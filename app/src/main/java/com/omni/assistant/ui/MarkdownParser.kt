package com.omni.assistant.ui

object MarkdownParser {
    /**
     * Converts markdown text to plain text suitable for text-to-speech.
     * Removes formatting while keeping readable content.
     */
    fun toSpeakable(markdown: String): String {
        return markdown
            // [label](url) -> label
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
            // **bold**, __bold__, *italic*, _italic_ -> plain text
            .replace(Regex("[*_]{1,2}([^*_]+)[*_]{1,2}"), "$1")
            // # Header -> Header
            .replace(Regex("^#+\\s+", RegexOption.MULTILINE), "")
            // `code` -> code
            .replace(Regex("`+([^`]+)`+"), "$1")
            // Collapse whitespace to a single space
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
