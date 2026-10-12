package com.omni.assistant.data

/** Pure (Android-free) rules about which attachments are accepted, so they can be unit-tested. */
object AttachmentRules {
    const val MAX_ATTACHMENTS_PER_MESSAGE = 6
    const val MAX_FILE_BYTES = 10L * 1024 * 1024
    const val MAX_IMAGE_SOURCE_BYTES = 40L * 1024 * 1024
    const val MAX_TEXT_CHARS = 60_000
    const val MAX_IMAGE_EDGE = 1600
    const val MAX_FRAME_EDGE = 768
    const val MAX_VIDEO_FRAMES = 6

    /** How many most-recent attachment-bearing user messages are re-sent in full on each request. */
    const val RESEND_LAST_N_MESSAGES = 2

    private val textExtensions = setOf(
        "txt", "md", "markdown", "json", "csv", "tsv", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf",
        "log", "html", "htm", "css", "js", "mjs", "ts", "tsx", "jsx", "kt", "kts", "java", "py", "rb", "go",
        "rs", "c", "h", "cpp", "hpp", "cs", "swift", "php", "sh", "bash", "zsh", "sql", "gradle", "properties",
        "env", "gitignore", "dockerfile", "lua", "r", "dart", "scala"
    )

    private val textMimes = setOf(
        "application/json", "application/xml", "application/javascript", "application/x-yaml",
        "application/yaml", "application/sql", "application/x-sh", "application/toml"
    )

    /** Returns "image", "video", "pdf", "text", or null if the type is not supported. */
    fun classify(name: String, mime: String): String? {
        val m = mime.lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            m.startsWith("image/") -> "image"
            m.startsWith("video/") -> "video"
            m == "application/pdf" || ext == "pdf" -> "pdf"
            m.startsWith("text/") || m in textMimes || ext in textExtensions -> "text"
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
}
