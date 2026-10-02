package com.pip.shared.chat

/**
 * Assembles streamed `typing` token deltas into renderable text — ports
 * `KeyboardSessionView`'s existing token-accumulation pattern into shared
 * code (PRD §8).
 */
class StreamingParser {
    private val buffer = StringBuilder()

    val text: String
        get() = buffer.toString()

    fun append(token: String): String {
        buffer.append(token)
        return buffer.toString()
    }

    fun reset() {
        buffer.clear()
    }
}
