package com.omni.assistant.ui

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Renders markdown-formatted text as Compose UI.
 * Supports basic markdown: bold (**text**), italic (*text*), code (`text`), and code blocks.
 */
@Composable
fun MarkdownView(content: String) {
    val clean = com.omni.assistant.data.FileExport.splitThinking(content).cleanText
    SelectionContainer {
        Text(
            text = parseMarkdown(clean),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Bold: **text**
                i < text.length - 1 && text[i] == '*' && text[i + 1] == '*' -> {
                    i += 2
                    val start = length
                    while (i < text.length - 1) {
                        if (text[i] == '*' && text[i + 1] == '*') {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(text.substring(start, i))
                            }
                            i += 2
                            break
                        }
                        append(text[i])
                        i++
                    }
                }
                // Italic: *text* or _text_
                text[i] == '*' || text[i] == '_' -> {
                    val delimiter = text[i]
                    i++
                    val start = length
                    while (i < text.length) {
                        if (text[i] == delimiter) {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append(text.substring(start, i))
                            }
                            i++
                            break
                        }
                        append(text[i])
                        i++
                    }
                }
                // Code: `text`
                text[i] == '`' -> {
                    i++
                    val start = length
                    while (i < text.length && text[i] != '`') {
                        append(text[i])
                        i++
                    }
                    if (i < text.length) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)) {
                            addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, length)
                        }
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
