package com.pip.shared.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownParserTest {
    @Test
    fun parse_bold() {
        val doc = MarkdownParser.parse("Ini **bold** ya")
        assertEquals(
            listOf(
                MarkdownSpan("Ini ", MarkdownSpanKind.PLAIN),
                MarkdownSpan("bold", MarkdownSpanKind.BOLD),
                MarkdownSpan(" ya", MarkdownSpanKind.PLAIN),
            ),
            doc.spans,
        )
    }

    @Test
    fun parse_italic_notConfusedWithBold() {
        val doc = MarkdownParser.parse("**bold** lalu *italic*")
        assertEquals(
            listOf(
                MarkdownSpan("bold", MarkdownSpanKind.BOLD),
                MarkdownSpan(" lalu ", MarkdownSpanKind.PLAIN),
                MarkdownSpan("italic", MarkdownSpanKind.ITALIC),
            ),
            doc.spans,
        )
    }

    @Test
    fun parse_inlineCode() {
        val doc = MarkdownParser.parse("jalankan `npm install` dulu")
        assertEquals(
            listOf(
                MarkdownSpan("jalankan ", MarkdownSpanKind.PLAIN),
                MarkdownSpan("npm install", MarkdownSpanKind.CODE),
                MarkdownSpan(" dulu", MarkdownSpanKind.PLAIN),
            ),
            doc.spans,
        )
    }

    @Test
    fun parse_markdownLink() {
        val doc = MarkdownParser.parse("cari di [Google](https://google.com) ya")
        assertEquals(
            listOf(
                MarkdownSpan("cari di ", MarkdownSpanKind.PLAIN),
                MarkdownSpan("Google", MarkdownSpanKind.LINK, url = "https://google.com"),
                MarkdownSpan(" ya", MarkdownSpanKind.PLAIN),
            ),
            doc.spans,
        )
    }

    @Test
    fun parse_bareUrl() {
        val doc = MarkdownParser.parse("cek https://example.com/path ya")
        assertEquals(
            listOf(
                MarkdownSpan("cek ", MarkdownSpanKind.PLAIN),
                MarkdownSpan("https://example.com/path", MarkdownSpanKind.LINK, url = "https://example.com/path"),
                MarkdownSpan(" ya", MarkdownSpanKind.PLAIN),
            ),
            doc.spans,
        )
    }

    @Test
    fun parse_bulletList_normalizedBeforeItalicParsing() {
        // A naive per-line "* " -> italic-marker parse would misfire across
        // bullet lines (two "* item" lines look like an opened-then-closed
        // italic span) — bullets must be normalized to "•" BEFORE markdown
        // tokenizing, exactly like iOS's normalizeBullets ordering.
        val doc = MarkdownParser.parse("* item satu\n* item dua")
        assertEquals(
            listOf(MarkdownSpan("• item satu\n• item dua", MarkdownSpanKind.PLAIN)),
            doc.spans,
        )
    }

    @Test
    fun parse_plainTextUnaffected() {
        val doc = MarkdownParser.parse("cuma teks biasa aja")
        assertEquals(listOf(MarkdownSpan("cuma teks biasa aja", MarkdownSpanKind.PLAIN)), doc.spans)
    }
}
