package com.omni.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun MarkdownView(
    text: String,
    modifier: Modifier = Modifier,
    onSaveCode: ((fileName: String, content: String) -> Unit)? = null,
    onRunHtml: ((html: String) -> Unit)? = null
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Heading -> HeadingBlock(block.level, block.text)
                is MdBlock.Paragraph -> ParagraphBlock(block.text)
                is MdBlock.Bullet -> BulletBlock(block.marker, block.text)
                is MdBlock.Code -> CodeBlock(
                    language = block.language,
                    content = block.content,
                    fileName = block.fileName,
                    onSaveCode = onSaveCode,
                    onRunHtml = onRunHtml
                )
            }
        }
    }
}

@Composable
private fun HeadingBlock(level: Int, text: String) {
    val fontScale = LocalFontSizeScale.current
    val style = when (level) {
        1 -> MaterialTheme.typography.titleLarge.copy(fontSize = (22f * fontScale).sp, lineHeight = (28f * fontScale).sp)
        2 -> MaterialTheme.typography.titleMedium.copy(fontSize = (18f * fontScale).sp, lineHeight = (24f * fontScale).sp)
        else -> MaterialTheme.typography.titleSmall.copy(fontSize = (15f * fontScale).sp, lineHeight = (20f * fontScale).sp)
    }
    Text(
        text = text,
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun ParagraphBlock(text: String) {
    val fontScale = LocalFontSizeScale.current
    Text(
        text = formatInlineMarkdown(text),
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = (15.5f * fontScale).sp,
            lineHeight = (22f * fontScale).sp
        ),
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun BulletBlock(marker: String, text: String) {
    val fontScale = LocalFontSizeScale.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = marker,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = (15.5f * fontScale).sp,
                lineHeight = (22f * fontScale).sp
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = formatInlineMarkdown(text),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = (15.5f * fontScale).sp,
                lineHeight = (22f * fontScale).sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CodeBlock(
    language: String,
    content: String,
    fileName: String? = null,
    onSaveCode: ((fileName: String, content: String) -> Unit)? = null,
    onRunHtml: ((html: String) -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    val resolvedName = fileName ?: run {
        val ext = com.omni.assistant.data.FileExport.extensionFor(language)
        if (ext == "html") "index.html" else "file.$ext"
    }
    val isHtml = language.equals("html", ignoreCase = true) || resolvedName.endsWith(".html", ignoreCase = true)

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    LaunchedEffect(saved) {
        if (saved) {
            delay(2000)
            saved = false
        }
    }

    var expanded by remember { mutableStateOf(true) }
    val lines = remember(content) { content.lines() }
    val isHuge = lines.size > 120
    val displayContent = remember(content, expanded) {
        if (!expanded) {
            lines.take(80).joinToString("\n")
        } else {
            content
        }
    }

    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = resolvedName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isHtml && onRunHtml != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onRunHtml(content) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run / Preview Game",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "Run",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (onSaveCode != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                onSaveCode(resolvedName, content)
                                saved = true
                            }
                        ) {
                            Icon(
                                imageVector = if (saved) Icons.Default.Check else Icons.Default.Download,
                                contentDescription = "Save file",
                                tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = if (saved) "Saved" else "Save",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString(content))
                            copied = true
                        }
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = if (copied) "Copied" else "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = displayContent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )
            }
            if (isHuge) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "▲ Collapse code (showing all ${lines.size} lines)" else "▼ Expand code (showing 80 of ${lines.size} lines)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun formatInlineMarkdown(text: String): AnnotatedString {
    if (text.length > 10000) {
        return AnnotatedString(text)
    }
    return try {
        buildAnnotatedString {
            val regex = Regex("(`[^`]+`)|(\\*\\*[^*]+\\*\\*)")
            var lastIdx = 0
            for (m in regex.findAll(text)) {
                val range = m.range
                if (range.first > lastIdx && range.first <= text.length) {
                    append(text.substring(lastIdx, range.first))
                }
                val matchStr = m.value
                if (matchStr.startsWith("`") && matchStr.endsWith("`") && matchStr.length > 2) {
                    val code = matchStr.substring(1, matchStr.length - 1)
                    val start = length
                    append(code)
                    if (length >= start) {
                        addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x33888888)
                            ),
                            start,
                            length
                        )
                    }
                } else if (matchStr.startsWith("**") && matchStr.endsWith("**") && matchStr.length > 4) {
                    val bold = matchStr.substring(2, matchStr.length - 2)
                    val start = length
                    append(bold)
                    if (length >= start) {
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                    }
                } else {
                    append(matchStr)
                }
                lastIdx = range.last + 1
            }
            if (lastIdx < text.length) {
                append(text.substring(lastIdx))
            }
        }
    } catch (_: Exception) {
        AnnotatedString(text)
    }
}
