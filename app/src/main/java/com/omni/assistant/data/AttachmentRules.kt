package com.omni.assistant.data

/** Pure (Android-free) rules about which attachments are accepted, so they can be unit-tested. */
object AttachmentRules {
    const val MAX_ATTACHMENTS_PER_MESSAGE = 10
    const val MAX_FILE_BYTES = 50L * 1024 * 1024
    const val MAX_IMAGE_SOURCE_BYTES = 50L * 1024 * 1024
    const val MAX_TEXT_CHARS = 100_000
    const val MAX_IMAGE_EDGE = 1600
    const val MAX_FRAME_EDGE = 768
    const val MAX_VIDEO_FRAMES = 6

    /** How many most-recent attachment-bearing user messages are re-sent in full on each request. */
    const val RESEND_LAST_N_MESSAGES = 2

    val imageExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "svg", "ico", "tiff"
    )

    val videoExtensions = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v"
    )

    val textExtensions = setOf(
        "txt", "md", "markdown", "json", "csv", "tsv", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf",
        "log", "html", "htm", "css", "js", "mjs", "ts", "tsx", "jsx", "kt", "kts", "java", "py", "rb", "go",
        "rs", "c", "h", "cpp", "hpp", "cs", "swift", "php", "sh", "bash", "zsh", "sql", "gradle", "properties",
        "env", "gitignore", "dockerfile", "lua", "r", "dart", "scala", "jsonl", "vue", "svelte", "proto",
        "lock", "asm", "s", "bat", "cmd", "ps1", "diff", "patch", "tex", "rst", "graphql", "gql", "sol",
        "zig", "nim", "erl", "ex", "exs", "clj", "cljs", "lisp", "el", "pl", "pm", "awk", "sed", "cmake",
        "make", "mk", "makefile", "dockerignore", "editorconfig", "gitattributes", "strings"
    )

    private val textMimes = setOf(
        "application/json", "application/xml", "application/javascript", "application/x-yaml",
        "application/yaml", "application/sql", "application/x-sh", "application/toml"
    )

    val documentExtensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "epub",
        "zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "apk", "jar", "bin", "dat", "db", "sqlite", "sqlite3"
    )

    fun isImage(name: String, mime: String): Boolean {
        val m = mime.lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()
        return m.startsWith("image/") || ext in imageExtensions
    }

    /** Returns "image", "video", "pdf", "text", "file", or null if the type is not supported. */
    fun classify(name: String, mime: String): String? {
        val m = mime.lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            isImage(name, mime) -> "image"
            m.startsWith("video/") || ext in videoExtensions -> "video"
            m == "application/pdf" || ext == "pdf" -> "pdf"
            m.startsWith("text/") || m in textMimes || ext in textExtensions -> "text"
            ext in documentExtensions || m.startsWith("application/") -> "file"
            ext == "exe" || ext == "dll" -> null
            ext.isNotEmpty() -> "file"
            else -> null
        }
    }

    fun unsupportedMessage(name: String): String =
        "\"$name\" isn't supported. You can attach images, videos, PDFs and text or code files."

    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }

    /** Number of evenly spaced frames to sample from a video of the given length. */
    fun frameCount(durationMs: Long): Int {
        val seconds = (durationMs / 1000).toInt()
        return ((seconds + 1) / 2).coerceIn(2, MAX_VIDEO_FRAMES)
    }

    /** Midpoint timestamps (microseconds) of n equal slices of the video. */
    fun frameTimesUs(durationMs: Long, n: Int): List<Long> =
        (0 until n).map { i -> durationMs * 1000L * (2 * i + 1) / (2L * n) }

    data class ThinkingSplit(
        val thinking: String?,
        val cleanText: String
    )

    /**
     * Splits raw LLM response into thinking content and the clean user-facing response.
     * Supports <thinking>...</thinking> as well as unclosed <thinking> while streaming.
     */
    fun splitThinking(markdown: String): ThinkingSplit {
        if (!markdown.contains("<thinking>")) {
            return ThinkingSplit(null, markdown)
        }
        val start = markdown.indexOf("<thinking>")
        val end = markdown.indexOf("</thinking>")
        return if (end != -1) {
            val thinking = markdown.substring(start + 10, end).trim()
            val before = markdown.substring(0, start)
            val after = markdown.substring(end + 11)
            val clean = (before + after).trimStart('\r', '\n')
            ThinkingSplit(thinking.ifBlank { null }, clean)
        } else {
            val thinking = markdown.substring(start + 10).trim()
            val clean = markdown.substring(0, start).trimStart('\r', '\n')
            ThinkingSplit(thinking.ifBlank { null }, clean)
        }
    }
}
