package com.pip.shared.viewmodel

import com.pip.shared.analytics.AnalyticsEmitter
import com.pip.shared.analytics.AnalyticsEvent
import com.pip.shared.chat.StreamingParser
import com.pip.shared.data.repository.ChatRepository
import com.pip.shared.domain.model.Conversation
import com.pip.shared.domain.usecase.ChatSendResult
import com.pip.shared.domain.usecase.GetChatHistory
import com.pip.shared.domain.usecase.SendChatMessage
import com.pip.shared.network.dto.ChatServerEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Composes Chat UseCases + [StreamingParser] into ui state for both
 * platforms' UI layer (PRD §8) — the missing screen ViewModel identified
 * in §4.3. Connect/disconnect/[events] come straight from [ChatRepository]
 * rather than a UseCase — connection lifecycle isn't a user action the
 * way Send/GetHistory are, so it doesn't warrant its own UseCase wrapper.
 */
class ChatSessionViewModel(
    private val sendChatMessageUseCase: SendChatMessage,
    private val getChatHistoryUseCase: GetChatHistory,
    private val chatRepository: ChatRepository,
    private val analytics: AnalyticsEmitter,
) {
    private val streamingParser = StreamingParser()

    private val _history = MutableStateFlow<Conversation?>(null)
    val history: StateFlow<Conversation?> = _history.asStateFlow()

    val events: SharedFlow<ChatServerEvent> get() = chatRepository.events

    fun connect() = chatRepository.connect()

    fun disconnect() = chatRepository.disconnect()

    suspend fun loadHistory() {
        getChatHistoryUseCase().onSuccess { _history.value = it }
    }

    suspend fun send(
        text: String,
        replySenderLabel: String? = null,
        replySnippet: String? = null,
    ): ChatSendResult {
        val result = sendChatMessageUseCase(text, replySenderLabel, replySnippet)
        analytics.emit(AnalyticsEvent("chat_message_sent", mapOf("result" to result::class.simpleName.orEmpty())))
        return result
    }

    fun appendStreamedToken(token: String): String = streamingParser.append(token)

    fun resetStreaming() = streamingParser.reset()
}
