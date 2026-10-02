package com.pip.shared.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamingParserTest {
    @Test
    fun append_accumulatesTokensInOrder() {
        val parser = StreamingParser()
        assertEquals("Hi", parser.append("Hi"))
        assertEquals("Hi there", parser.append(" there"))
        assertEquals("Hi there", parser.text)
    }

    @Test
    fun reset_clearsAccumulatedText() {
        val parser = StreamingParser()
        parser.append("partial")
        parser.reset()
        assertEquals("", parser.text)
    }
}
