package com.omni.assistant.ui

sealed class MdBlock {
    data class Paragraph(val text: String) : MdBlock()
    data class Code(val language: String, val content: String, val fileName: String? = null) : MdBlock()
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Bullet(val marker: String, val text: String) : MdBlock()
}

object MarkdownParser {

    fun parse(markdown: String): List<MdBlock> {
        return try {
            val lines = markdown.lines()
            val blocks = mutableListOf<MdBlock>()
            var i = 0

            val headingRegex = Regex("^\\s*(#{1,6})\\s+(.+?)\\s*#*\\s*$")
            val bulletRegex = Regex("^\\s*(?:([-*+])\\s+|(\\d+[.)])\\s+)(.+?)\\s*$")

            while (i < lines.size) {
                val raw = lines[i]
                val line = if (raw.length > 2000) raw.take(2000) + " …" else raw

                if (line.isBlank()) {
                    i++
                    continue
                }

                if (line.trimStart().startsWith("```")) {
                    val header = line.trim().removePrefix("```").trim()
                    var language = header
                    var fileName: String? = null
                    try {
                        if (header.contains(":")) {
                            val parts = header.split(":", limit = 2)
                            language = parts[0].trim()
                            fileName = parts.getOrNull(1)?.trim()
                        } else if (header.contains(" ")) {
                            val parts = header.split(" ", limit = 2)
                            language = parts[0].trim()
                            fileName = parts.getOrNull(1)?.trim()
                        } else if (header.contains(".")) {
                            fileName = header
                            language = header.substringAfterLast(".", "txt").lowercase()
                        }
                    } catch (_: Exception) {
                        language = "txt"
                    }
                    i++

                    val codeLines = mutableListOf<String>()
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        val cRaw = lines[i]
                        codeLines.add(if (cRaw.length > 2000) cRaw.take(2000) + " …" else cRaw)
                        i++
                    }

                    if (i < lines.size) i++

                    blocks.add(MdBlock.Code(language, codeLines.joinToString("\n"), fileName))
                    continue
                }

                val heading = try { headingRegex.matchEntire(line) } catch (_: Exception) { null }
                if (heading != null) {
                    blocks.add(
                        MdBlock.Heading(
                            heading.groupValues[1].length,
                            heading.groupValues[2]
                        )
                    )
                    i++
                    continue
                }

                val bullet = try { bulletRegex.matchEntire(line) } catch (_: Exception) { null }
                if (bullet != null) {
                    val marker = bullet.groupValues[2].ifEmpty { "•" }
                    blocks.add(MdBlock.Bullet(marker, bullet.groupValues[3]))
                    i++
                    continue
                }

                val paragraph = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotBlank()) {
                    val current = lines[i]
                    if (current.trimStart().startsWith("```") ||
                        (try { headingRegex.matches(current) } catch (_: Exception) { false }) ||
                        (try { bulletRegex.matches(current) } catch (_: Exception) { false })) {
                        break
                    }
                    paragraph.add(if (current.length > 2000) current.take(2000) + " …" else current)
                    i++
                }

                if (paragraph.isNotEmpty()) {
                    blocks.add(MdBlock.Paragraph(paragraph.joinToString("\n")))
                }
            }

            blocks
        } catch (_: Exception) {
            listOf(MdBlock.Paragraph(markdown))
        }
    }

    fun toSpeakable(markdown: String): String {
        return markdown
            .replace(Regex("(?s)```.*?```"), " ")
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
            .replace(Regex("[*_]{1,2}([^*_]+)[*_]{1,2}"), "$1")
            .replace(Regex("^#+\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("`+([^`]+)`+"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
