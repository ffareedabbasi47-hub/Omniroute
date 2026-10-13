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
            val cleanMarkdown = com.omni.assistant.data.FileExport.splitThinking(markdown).cleanText
            val lines = cleanMarkdown.lines()
            val blocks = mutableListOf<MdBlock>()
            var i = 0

            val headingRegex = Regex("^\\s*(#{1,6})\\s+(.+?)\\s*#*\\s*$")
            val bulletRegex = Regex("^\\s*(?:([-*+])\\s+|(\\d+[.)])\\s+)(.+?)\\s*$")

            while (i < lines.size) {
                val line = lines[i]

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
                    } catch (e: Exception) {
                        language = "txt"
                    }
                    i++

                    val codeLines = mutableListOf<String>()
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }

                    if (i < lines.size) i++

                    blocks.add(MdBlock.Code(language, codeLines.joinToString("\n"), fileName))
                    continue
                }

                val heading = try { headingRegex.matchEntire(line) } catch (e: Exception) { null }
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

                val bullet = try { bulletRegex.matchEntire(line) } catch (e: Exception) { null }
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
                        (try { headingRegex.matches(current) } catch (e: Exception) { false }) ||
                        (try { bulletRegex.matches(current) } catch (e: Exception) { false })) {
                        break
                    }
                    paragraph.add(current)
                    i++
                }

                if (paragraph.isNotEmpty()) {
                    blocks.add(MdBlock.Paragraph(paragraph.joinToString("\n")))
                }
            }

            blocks
        } catch (e: Exception) {
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
