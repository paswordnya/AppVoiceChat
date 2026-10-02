package com.pip.shared.memory

import com.pip.shared.domain.model.Conversation
import com.pip.shared.domain.model.SessionId

/**
 * Local scrollback cache — closes the real gap noted in PRD §4.5 (chat
 * history is currently re-fetched from REST every time the screen opens).
 * In-memory only, capped per §16's NFR; promoted to SQLDelight only if
 * product requires surviving a full app kill (§5.3's open question, §22).
 */
class ConversationCache(private val maxConversations: Int = 20) {
    private val cache = LinkedHashMap<SessionId, Conversation>()

    fun get(sessionId: SessionId): Conversation? = cache[sessionId]

    fun put(
        sessionId: SessionId,
        conversation: Conversation,
    ) {
        cache.remove(sessionId)
        cache[sessionId] = conversation
        while (cache.size > maxConversations) {
            val oldest = cache.keys.firstOrNull() ?: break
            cache.remove(oldest)
        }
    }

    fun clear() = cache.clear()
}
