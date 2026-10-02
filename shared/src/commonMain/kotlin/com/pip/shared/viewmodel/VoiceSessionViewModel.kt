package com.pip.shared.viewmodel

import com.pip.shared.analytics.AnalyticsEmitter
import com.pip.shared.analytics.AnalyticsEvent
import com.pip.shared.domain.model.Conversation
import com.pip.shared.domain.usecase.GetChatHistory
import com.pip.shared.domain.usecase.SendAudioFrame
import com.pip.shared.domain.usecase.SendPushToTalkEnd
import com.pip.shared.domain.usecase.SendPushToTalkStart
import com.pip.shared.domain.usecase.SendVoicePing
import com.pip.shared.domain.usecase.SetListeningMode
import com.pip.shared.domain.usecase.SetVoiceMode
import com.pip.shared.domain.usecase.StartVoiceSession
import com.pip.shared.domain.usecase.StopVoiceSession
import com.pip.shared.domain.usecase.SubmitFeedback
import com.pip.shared.network.dto.VoiceServerEvent
import com.pip.shared.voice.ListeningMode
import com.pip.shared.voice.VoiceTurnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Composes the Voice UseCases into ui state for both platforms' UI layer
 * (PRD §7) — the missing screen ViewModel identified in §4.3.
 */
class VoiceSessionViewModel(
    private val startVoiceSessionUseCase: StartVoiceSession,
    private val stopVoiceSessionUseCase: StopVoiceSession,
    private val sendAudioFrameUseCase: SendAudioFrame,
    private val submitFeedbackUseCase: SubmitFeedback,
    private val setListeningModeUseCase: SetListeningMode,
    private val setVoiceModeUseCase: SetVoiceMode,
    private val sendPushToTalkStartUseCase: SendPushToTalkStart,
    private val sendPushToTalkEndUseCase: SendPushToTalkEnd,
    private val sendVoicePingUseCase: SendVoicePing,
    private val getChatHistoryUseCase: GetChatHistory,
    private val analytics: AnalyticsEmitter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(VoiceTurnState.IDLE)
    val state: StateFlow<VoiceTurnState> = _state.asStateFlow()

    // Same session_id/messages table as ChatRepository's history (voice
    // turns are persisted via session_store.add_message in mode_b_pipeline.py
    // /voice_mode_a.py, same as chat) — reusing GetChatHistory here instead
    // of a separate voice-specific use case, since it's already scoped to
    // the current session (and therefore, via the backend's ownership
    // check, to the logged-in user) with no voice-specific behavior needed.
    private val _history = MutableStateFlow<Conversation?>(null)
    val history: StateFlow<Conversation?> = _history.asStateFlow()

    suspend fun loadHistory() {
        getChatHistoryUseCase().onSuccess { _history.value = it }
    }

    private val _events = MutableSharedFlow<VoiceServerEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<VoiceServerEvent> = _events.asSharedFlow()

    /** TTS reply audio (PCM16 mono 24kHz, PRD §4.4) — native playback consumes this. */
    private val _audioFrames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val audioFrames: SharedFlow<ByteArray> = _audioFrames.asSharedFlow()

    fun start() {
        analytics.emit(AnalyticsEvent("voice_session_started"))
        val session = startVoiceSessionUseCase()
        scope.launch { session.state.collect { _state.value = it } }
        scope.launch { session.events.collect { _events.emit(it) } }
        scope.launch { session.audioFrames.collect { _audioFrames.emit(it) } }
    }

    fun stop() {
        stopVoiceSessionUseCase()
        _state.value = VoiceTurnState.IDLE
        analytics.emit(AnalyticsEvent("voice_session_stopped"))
    }

    suspend fun sendAudio(bytes: ByteArray) = sendAudioFrameUseCase(bytes)

    suspend fun sendFeedback(
        requestId: String,
        positive: Boolean,
    ) {
        submitFeedbackUseCase(requestId, positive)
        analytics.emit(AnalyticsEvent("voice_feedback_given", mapOf("positive" to positive.toString())))
    }

    suspend fun updateListeningMode(mode: ListeningMode) = setListeningModeUseCase(mode)

    suspend fun updateVoiceMode(mode: String) = setVoiceModeUseCase(mode)

    fun sendPushToTalkStart() = sendPushToTalkStartUseCase()

    fun sendPushToTalkEnd() = sendPushToTalkEndUseCase()

    fun sendPing(clientSentAt: Long) = sendVoicePingUseCase(clientSentAt)
}
