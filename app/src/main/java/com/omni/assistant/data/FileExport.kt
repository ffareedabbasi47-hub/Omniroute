package com.omni.assistant.data

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream

/** Turns reply text into files the user can save or share. Pure helpers are testable without Android. */
object FileExport {
    /** File extension for a Markdown code-fence language ("kotlin" -> "kt"); "txt" when unknown. */
    fun extensionFor(lang: String): String = when (lang.trim().lowercase()) {
        "kotlin", "kt" -> "kt"
        "python", "py" -> "py"
        "javascript", "js" -> "js"
        "typescript", "ts" -> "ts"
        "java" -> "java"
        "html" -> "html"
        "css" -> "css"
        "json" -> "json"
        "xml" -> "xml"
        "yaml", "yml" -> "yml"
        "bash", "sh", "shell" -> "sh"
        "sql" -> "sql"
        "markdown", "md" -> "md"
        "csv" -> "csv"
        "c" -> "c"
        "cpp", "c++" -> "cpp"
        "go" -> "go"
        "rust", "rs" -> "rs"
        "swift" -> "swift"
        "php" -> "php"
        "ruby", "rb" -> "rb"
        else -> "txt"
    }

    fun mimeFor(ext: String): String = when (ext) {
        "html" -> "text/html"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "csv" -> "text/csv"
        "md" -> "text/markdown"
        "pdf" -> "application/pdf"
        else -> "text/plain"
    }

    /** A file name for a code block: a "name.ext" found on the line(s) just before it, else generated. */
    fun fileNameFor(lang: String, textBefore: String?, index: Int): String {
        val ext = extensionFor(lang)
        val candidate = textBefore?.let { Regex("([A-Za-z0-9_\\-]{1,40}\\.[A-Za-z0-9]{1,6})").findAll(it).lastOrNull()?.value }
        return candidate ?: "snippet_${index + 1}.$ext"
    }

    /** Strips the most common Markdown markers so a PDF/text export reads cleanly. */
    fun markdownToPlain(md: String): String {
        val out = StringBuilder()
        var inCode = false
        for (line in md.lines()) {
            if (line.trimStart().startsWith("```")) {
                inCode = !inCode
                continue
            }
            if (inCode) {
                out.append("    ").append(line).append('\n')
                continue
            }
            var l = line.replace(Regex("^#{1,6}\\s+"), "")
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("(?<!\\*)\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\*)"), "$1")
                .replace(Regex("`([^`]+)`"), "$1")
            l = l.replace(Regex("^\\s*[-*]\\s+"), "• ")
            out.append(l).append('\n')
        }
        return out.toString().trimEnd()
    }

    /** Renders plain text into an A4 PDF with simple wrapping and pagination. */
    fun textToPdf(title: String, text: String): ByteArray {
        val pageW = 595
        val pageH = 842
        val margin = 48
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            typeface = Typeface.DEFAULT
            color = 0xFF111111.toInt()
        }
        val width = pageW - 2 * margin
        val doc = PdfDocument()
        val full = (if (title.isNotBlank()) title.trim() + "\n\n" else "") + text
        val paragraphs = full.split('\n')
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        var y = margin.toFloat()
        for (para in paragraphs) {
            val layout = StaticLayout.Builder.obtain(para.ifEmpty { " " }, 0, para.ifEmpty { " " }.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1f)
                .build()
            // Draw line by line so long paragraphs can continue across pages.
            for (i in 0 until layout.lineCount) {
                val top = layout.getLineTop(i)
                val bottom = layout.getLineBottom(i)
                val h = (bottom - top).toFloat()
                if (y + h > pageH - margin) {
                    doc.finishPage(page)
                    pageNo += 1
                    page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
                    y = margin.toFloat()
                }
                val c = page.canvas
                c.save()
                c.translate(margin.toFloat(), y - top)
                c.clipRect(0f, top.toFloat(), width.toFloat(), bottom.toFloat())
                layout.draw(c)
                c.restore()
                y += h
            }
        }
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    data class CodeFile(val name: String, val lang: String, val content: String)

    /** Extracts all code blocks from markdown into distinct files. */
    fun extractCodeFiles(markdown: String): List<CodeFile> {
        val lines = markdown.lines()
        val files = mutableListOf<CodeFile>()
        var inBlock = false
        var currentLang = ""
        var currentFileName: String? = null
        val currentContent = StringBuilder()
        var lastTextBefore = ""

        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                if (inBlock) {
                    val finalName = currentFileName ?: fileNameFor(currentLang, lastTextBefore, files.size)
                    files.add(CodeFile(finalName, currentLang, currentContent.toString()))
                    inBlock = false
                    currentContent.clear()
                    currentFileName = null
                    currentLang = ""
                } else {
                    inBlock = true
                    val header = trimmed.removePrefix("```").trim()
                    if (header.contains(":")) {
                        val parts = header.split(":", limit = 2)
                        currentLang = parts[0].trim()
                        currentFileName = parts[1].trim()
                    } else if (header.contains(" ")) {
                        val parts = header.split(" ", limit = 2)
                        currentLang = parts[0].trim()
                        currentFileName = parts[1].trim()
                    } else {
                        currentLang = header
                        if (header.contains(".")) {
                            currentFileName = header
                        }
                    }
                }
            } else if (inBlock) {
                if (currentContent.isNotEmpty()) currentContent.append('\n')
                currentContent.append(line)
            } else {
                if (line.isNotBlank()) lastTextBefore = line
            }
        }
        return files
    }

    /** Bundles code files into a clean .zip archive. */
    fun createZipArchive(files: List<CodeFile>, projectName: String = "project"): ByteArray {
        val baos = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zip ->
            for (f in files) {
                val entry = java.util.zip.ZipEntry(f.name)
                zip.putNextEntry(entry)
                zip.write(f.content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            if (files.none { it.name.equals("README.md", ignoreCase = true) }) {
                val readme = "# $projectName\n\nGenerated with Antigravity OmniAssistant.\n\nIncluded files:\n" +
                    files.joinToString("\n") { "- `${it.name}` (${it.lang})" }
                val entry = java.util.zip.ZipEntry("README.md")
                zip.putNextEntry(entry)
                zip.write(readme.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    /** Combines index.html with any companion CSS/JS into a single runnable HTML file for instant play. */
    fun findPlayableHtml(files: List<CodeFile>): String? {
        return try {
            val htmlFile = files.firstOrNull { it.name.endsWith(".html", ignoreCase = true) } ?: return null
            var html = htmlFile.content
            val cssFile = files.firstOrNull { it.name.endsWith(".css", ignoreCase = true) }
            val jsFile = files.firstOrNull { it.name.endsWith(".js", ignoreCase = true) }

            if (cssFile != null && cssFile.content.isNotBlank() && !html.contains(cssFile.content.take(30))) {
                val headIdx = html.indexOf("</head>", ignoreCase = true)
                html = if (headIdx != -1) {
                    html.substring(0, headIdx) + "<style>\n" + cssFile.content + "\n</style>\n" + html.substring(headIdx)
                } else {
                    "<style>\n" + cssFile.content + "\n</style>\n" + html
                }
            }
            if (jsFile != null && jsFile.content.isNotBlank() && !html.contains(jsFile.content.take(30))) {
                val bodyIdx = html.indexOf("</body>", ignoreCase = true)
                html = if (bodyIdx != -1) {
                    html.substring(0, bodyIdx) + "<script>\n" + jsFile.content + "\n</script>\n" + html.substring(bodyIdx)
                } else {
                    html + "\n<script>\n" + jsFile.content + "\n</script>"
                }
            }
            html
        } catch (_: Exception) {
            files.firstOrNull { it.name.endsWith(".html", ignoreCase = true) }?.content
        }
    }
}
