package com.pip.shared.chat

/**
 * Recognizes a leading `"/"` (PRD §8, new in v2.1) — ports
 * `KeyboardSessionView.send()`'s real `text.hasPrefix("/")` check
 * (`/start`, `/model gemini`, etc.). Parsing/dispatch only; command
 * *behavior* stays server-side, unchanged (same split as §9's AI Router).
 */
object CommandParser {
    fun isCommand(text: String): Boolean = text.startsWith("/")
}
