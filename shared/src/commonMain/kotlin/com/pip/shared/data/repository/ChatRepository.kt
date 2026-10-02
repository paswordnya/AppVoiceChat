package com.pip.shared.data.repository

import com.pip.shared.chat.ChatSession
import com.pip.shared.core.config.AppConfig
import com.pip.shared.core.result.PipResult
import com.pip.shared.domain.model.Conversation
import com.pip.shared.domain.model.Message
import com.pip.shared.domain.model.MessageRole
import com.pip.shared.memory.ConversationCache
import com.pip.shared.network.dto.ChatHistoryDto
import com.pip.shared.network.http.ApiEndpoint
import com.pip.shared.network.http.KtorHttpClient
import com.pip.shared.network.ws.ReconnectingWebSocketClient
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Backs Chat UseCases (PRD §6 Phase 3/§8). One [ChatSession] per app session id. */
class ChatRepository(
    private val appConfig: AppConfig,
    private val httpClient: KtorHttpClient,
    private val cache: ConversationCache,
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
) {
    private val session: ChatSession by lazy {
        ChatSession(ReconnectingWebSocketClient(appConfig), sessionRepository.sessionId.value, authRepository.token)
    }

    val events: SharedFlow<com.pip.shared.network.dto.ChatServerEvent>
        get() = session.events

    /** Feeds the client AI Router's reachability check (PRD §9). */
    val isConnected: StateFlow<Boolean>
        get() = session.isConnected

    fun connect() = session.connect()

    fun disconnect() = session.disconnect()

    suspend fun sendMessage(
        text: String,
        replySenderLabel: String? = null,
        replySnippet: String? = null,
    ) = session.send(text, replySenderLabel, replySnippet)

    suspend fun getHistory(): PipResult<Conversation> {
        cache.get(sessionRepository.sessionId)?.let { return PipResult.Success(it) }
        val result = httpClient.request<ChatHistoryDto>(ApiEndpoint.History(sessionRepository.sessionId.value))
        return result.map { dto ->
            val conversation =
                Conversation(
                    sessionId = sessionRepository.sessionId,
                    messages =
                        dto.messages.mapNotNull { m ->
                            val role =
                                when (m.role) {
                                    "user" -> MessageRole.USER
                                    "assistant" -> MessageRole.ASSISTANT
                                    else -> return@mapNotNull null
                                }
                            Message(
                                id = "${m.createdAt}-${m.content.hashCode()}",
                                role = role,
                                content = m.content,
                                replySenderLabel = m.replySenderLabel,
                                replySnippet = m.replySnippet,
                                createdAt = m.createdAt,
                            )
                        },
                )
            cache.put(sessionRepository.sessionId, conversation)
            conversation
        }
    }
}
