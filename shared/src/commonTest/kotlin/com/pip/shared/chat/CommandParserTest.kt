package com.pip.shared.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandParserTest {
    @Test
    fun isCommand_detectsLeadingSlash() {
        assertTrue(CommandParser.isCommand("/start"))
        assertTrue(CommandParser.isCommand("/model gemini"))
    }

    @Test
    fun isCommand_falseForNormalMessages() {
        assertFalse(CommandParser.isCommand("hello Pip"))
        assertFalse(CommandParser.isCommand("what's up"))
    }
}
