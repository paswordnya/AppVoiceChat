package com.pip.app.ui.chat

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.pip.shared.chat.MarkdownParser
import com.pip.shared.chat.MarkdownSpanKind

private const val URL_TAG = "url"

/**
 * Renders a chat reply's Markdown-ish formatting (bold/italic/inline-code/
 * links, bullet lists) via the shared `MarkdownParser` — Android's
 * counterpart to iOS's `KeyboardSessionView.formattedText(_:linkColor:)`,
 * which gets the same result for free from `AttributedString(markdown:)`.
 *
 * Links are tappable via `ClickableText` + a `url` string annotation — the
 * newer `LinkAnnotation`/`withLink` API isn't available on this project's
 * Compose BOM (2024.09.02), so this uses the older, broadly-compatible
 * pattern (`addStringAnnotation` + `getStringAnnotations` at tap offset).
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    linkColor: Color = color,
    style: TextStyle = LocalTextStyle.current,
) {
    val uriHandler = LocalUriHandler.current
    val annotated =
        buildAnnotatedString {
            for (span in MarkdownParser.parse(text).spans) {
                when (span.kind) {
                    MarkdownSpanKind.PLAIN -> append(span.text)
                    MarkdownSpanKind.BOLD -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
                    MarkdownSpanKind.ITALIC -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) }
                    MarkdownSpanKind.CODE ->
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = color.copy(alpha = 0.08f))) {
                            append(span.text)
                        }
                    MarkdownSpanKind.LINK -> {
                        val url = span.url ?: span.text
                        val start = length
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                            append(span.text)
                        }
                        addStringAnnotation(tag = URL_TAG, annotation = url, start = start, end = length)
                    }
                }
            }
        }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = color),
        onClick = { offset ->
            annotated.getStringAnnotations(URL_TAG, offset, offset).firstOrNull()?.let { uriHandler.openUri(it.item) }
        },
    )
}
