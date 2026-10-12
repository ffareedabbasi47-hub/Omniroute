package com.omni.assistant.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

sealed interface MdBlock {
    data class Code(val lang: String, val code: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val marker: String, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
}

/** Small, dependency-free Markdown subset: fenced code, headings, lists, paragraphs, **bold**, `code`. */
object MarkdownParser {
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val unordered = Regex("^[-*•]\\s+(.*)$")
    private val ordered = Regex("^(\\d+[.)])\\s+(.*)$")

    fun parse(source: String): List<MdBlock> {
        val out = mutableListOf<MdBlock>()
        val lines = source.replace("\r\n", "\n").split("\n")
        val para = StringBuilder()

        fun flush() {
            if (para.isNotEmpty()) {
                out.add(MdBlock.Paragraph(para.toString().trim()))
                para.setLength(0)
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trimStart()
            if (t.startsWith("```")) {
                flush()
                val lang = t.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                var first = true
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (!first) code.append('\n')
                    code.append(lines[i])
                    first = false
                    i++
                }
                out.add(MdBlock.Code(lang, code.toString()))
                i++ // closing fence (an unclosed fence, as in a half-streamed reply, just ends the block)
                continue
            }
            val h = heading.find(t)
            val u = unordered.find(t)
            val o = ordered.find(t)
            if (h != null) {
                flush()
                out.add(MdBlock.Heading(h.groupValues[1].length, h.groupValues[2].trim()))
            } else if (u != null) {
                flush()
                out.add(MdBlock.Bullet("•", u.groupValues[1]))
            } else if (o != null) {
                flush()
                out.add(MdBlock.Bullet(o.groupValues[1], o.groupValues[2]))
            } else if (line.isBlank()) {
                flush()
            } else {
                if (para.isNotEmpty()) para.append('\n')
                para.append(line)
            }
            i++
        }
        flush()
        return out
    }

    /** Plain text suitable for text-to-speech: no code, no markup symbols. */
    fun toSpeakable(source: String): String {
        val sb = StringBuilder()
        for (b in parse(source)) {
            when (b) {
                is MdBlock.Code -> sb.append("(code block skipped). ")
                is MdBlock.Heading -> sb.append(strip(b.text)).append(". ")
                is MdBlock.Bullet -> sb.append(strip(b.text)).append(". ")
                is MdBlock.Paragraph -> sb.append(strip(b.text)).append(' ')
            }
        }
        return sb.toString().trim()
    }

    private fun strip(s: String): String = s.replace("**", "").replace("`", "")
}

private fun inlineStyled(text: String, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end != -1) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(text.substring(i + 2, end))
                pop()
                i = end + 2
                continue
            }
        }
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end != -1) {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground))
                append(text.substring(i + 1, end))
                pop()
                i = end + 1
                continue
            }
        }
        append(text[i])
        i++
    }
}

@Composable
fun MarkdownView(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in blocks) {
            when (b) {
                is MdBlock.Code -> CodeBlock(b.lang, b.code)
                is MdBlock.Heading -> Text(
                    text = inlineStyled(b.text, codeBg),
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold
                )
                is MdBlock.Bullet -> Row {
                    Text(b.marker, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(24.dp))
                    Text(inlineStyled(b.text, codeBg), style = MaterialTheme.typography.bodyLarge)
                }
                is MdBlock.Paragraph -> Text(
                    inlineStyled(b.text, codeBg),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun CodeBlock(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp)
            ) {
                Text(
                    text = lang.ifBlank { "code" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    scope.launch {
                        val omni = ctx.applicationContext as com.omni.assistant.OmniApp
                        val ext = com.omni.assistant.data.FileExport.extensionFor(lang)
                        try {
                            val f = omni.attachments.writeExport("snippet.$ext", code.toByteArray())
                            val uri = omni.attachments.uriFor(f)
                            if (uri != null) shareUris(ctx, listOf(uri), com.omni.assistant.data.FileExport.mimeFor(ext), "snippet.$ext")
                        } catch (_: Exception) {
                        }
                    }
                }) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Save code as a file")
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                }) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = if (copied) "Copied" else "Copy code"
                    )
                }
            }
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            )
            Spacer(Modifier.width(0.dp))
        }
    }
}
