package com.pip.shared.domain.usecase

import com.pip.shared.ai.OfflineRouter
import com.pip.shared.ai.OfflineRoutingDecision
import com.pip.shared.core.time.currentTimeMillis
import com.pip.shared.data.repository.ChatRepository
import com.pip.shared.data.repository.SessionRepository
import com.pip.shared.memory.OfflineOutbox
import com.pip.shared.memory.OutboxEntry
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What actually happened to a send — the UI tags offline replies and queued messages differently (PRD §16). */
sealed interface ChatSendResult {
    data object SentToServer : ChatSendResult

    data class OfflineReply(val text: String) : ChatSendResult

    data object Queued : ChatSendResult
}

/**
 * Routes a chat send through the client AI Router (PRD §9) before falling
 * back to the server: reachable -> normal WS flow (server does ALL model
 * routing); unreachable + offline provider configured -> [OfflineRouter]
 * (Ollama/LM Studio only); unreachable + nothing configured -> queued in
 * [OfflineOutbox], never a fabricated reply (§16's Offline mode NFR).
 */
class SendChatMessage(
    private val chatRepository: ChatRepository,
    private val offlineRouter: OfflineRouter,
    private val offlineOutbox: OfflineOutbox,
    private val sessionRepository: SessionRepository,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend operator fun invoke(
        text: String,
        replySenderLabel: String? = null,
        replySnippet: String? = null,
    ): ChatSendResult {
        val reachable = chatRepository.isConnected.value
        return when (val decision = offlineRouter.decide(reachable)) {
            OfflineRoutingDecision.UseServer -> {
                chatRepository.sendMessage(text, replySenderLabel, replySnippet)
                ChatSendResult.SentToServer
            }
            is OfflineRoutingDecision.RouteToProvider -> {
                enqueue(text)
                val result = offlineRouter.generate(decision.provider, decision.model, text)
                var reply: ChatSendResult = ChatSendResult.Queued
                result.onSuccess { reply = ChatSendResult.OfflineReply(it) }
                reply
            }
            OfflineRoutingDecision.QueueForLater -> {
                enqueue(text)
                ChatSendResult.Queued
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun enqueue(text: String) {
        offlineOutbox.enqueue(
            OutboxEntry(
                id = Uuid.random().toString(),
                sessionId = sessionRepository.sessionId.value,
                text = text,
                queuedAtEpochMs = currentTimeMillis(),
            ),
        )
    }
}
