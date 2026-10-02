package com.pip.shared.chat

/**
 * Platform-agnostic parsed-markdown span model (PRD §8) — each native
 * layer renders these with its own text component (`AttributedString` on
 * iOS, `AnnotatedString`/Compose on Android); parsing shared, rendering
 * native, matching the "UI stays native" principle throughout the PRD.
 *
 * Feature set matches iOS's `KeyboardSessionView.formattedText(_:linkColor:)`
 * (bold/italic/inline-code/markdown-links/bare-URL detection, plus
 * bullet-line normalization) — iOS gets this for free from
 * `AttributedString(markdown:)` + `NSDataDetector`; Android has no
 * equivalent built-in, so this parser exists specifically to give
 * Android's chat bubble the same rendering, not just "bold and link" as
 * this file's very first (Phase 1) version scoped it to.
 */
enum class MarkdownSpanKind { PLAIN, BOLD, ITALIC, CODE, LINK }

data class MarkdownSpan(
    val text: String,
    val kind: MarkdownSpanKind,
    val url: String? = null,
)

data class MarkdownDocument(val spans: List<MarkdownSpan>)

object MarkdownParser {
    // One combined regex, alternatives tried in order at each scan position —
    // avoids the overlap bugs a "find each pattern separately, then merge"
    // approach hits (e.g. a lone `*` inside `**bold**` false-matching italic,
    // or two identical bold matches getting de-duped by text equality). Bold
    // listed before italic so `**x**` resolves to bold, not italic-of-italic.
    private val tokenRegex = Regex(
        "\\*\\*(.+?)\\*\\*" + // group 1: bold
            "|`(.+?)`" + // group 2: inline code
            "|\\[(.+?)]\\((\\S+?)\\)" + // groups 3,4: markdown link text, url
            "|\\*(.+?)\\*" + // group 5: italic
            "|(https?://\\S+)", // group 6: bare url
    )

    /** `* item`/`- item` lines -> `• item` — done as a pre-pass on raw text
     * (before markdown tokenizing), same order iOS's `normalizeBullets`
     * runs in, so a bullet's leading `*` never gets mistaken for italic. */
    private fun normalizeBullets(text: String): String =
        text.lineSequence().joinToString("\n") { line ->
            if (line.startsWith("* ") || line.startsWith("- ")) "•" + line.drop(1) else line
        }

    fun parse(raw: String): MarkdownDocument {
        val text = normalizeBullets(raw)
        val spans = mutableListOf<MarkdownSpan>()
        var cursor = 0

        for (match in tokenRegex.findAll(text)) {
            if (match.range.first > cursor) {
                spans.add(MarkdownSpan(text.substring(cursor, match.range.first), MarkdownSpanKind.PLAIN))
            }
            val groups = match.groupValues
            when {
                groups[1].isNotEmpty() -> spans.add(MarkdownSpan(groups[1], MarkdownSpanKind.BOLD))
                groups[2].isNotEmpty() -> spans.add(MarkdownSpan(groups[2], MarkdownSpanKind.CODE))
                groups[3].isNotEmpty() -> spans.add(MarkdownSpan(groups[3], MarkdownSpanKind.LINK, url = groups[4]))
                groups[5].isNotEmpty() -> spans.add(MarkdownSpan(groups[5], MarkdownSpanKind.ITALIC))
                groups[6].isNotEmpty() -> spans.add(MarkdownSpan(groups[6], MarkdownSpanKind.LINK, url = groups[6]))
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            spans.add(MarkdownSpan(text.substring(cursor), MarkdownSpanKind.PLAIN))
        }
        return MarkdownDocument(spans)
    }
}
