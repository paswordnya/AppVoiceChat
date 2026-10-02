package com.pip.shared.data.repository

import com.pip.shared.core.config.AppConfig
import com.pip.shared.network.dto.VoiceControlEvent
import com.pip.shared.network.ws.ReconnectingWebSocketClient
import com.pip.shared.voice.VoiceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Backs Voice UseCases (PRD §6 Phase 3/§7). One [VoiceSession] per app session id. */
class VoiceRepository(
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
) {
    private var session: VoiceSession? = null

    /** For fire-and-forget control frames (stop/push-to-talk/ping) that must not block callers. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun startSession(): VoiceSession {
        session?.let { return it }
        val fresh =
            VoiceSession(ReconnectingWebSocketClient(appConfig), sessionRepository.sessionId.value, authRepository.token)
        session = fresh
        fresh.start()
        return fresh
    }

    /** Sends the `stop` control frame before disconnecting, matching the pre-migration iOS client. */
    fun stopSession() {
        val current = session
        session = null
        scope.launch {
            current?.sendControl(VoiceControlEvent(event = "stop"))
            current?.stop()
        }
    }

    suspend fun sendAudioFrame(bytes: ByteArray) {
        session?.sendAudioFrame(bytes)
    }

    suspend fun submitFeedback(
        requestId: String,
        positive: Boolean,
    ) {
        session?.sendControl(VoiceControlEvent(event = "feedback", requestId = requestId, positive = positive))
    }

    fun sendPushToTalkStart() {
        scope.launch { session?.sendControl(VoiceControlEvent(event = "push_to_talk_start")) }
    }

    fun sendPushToTalkEnd() {
        scope.launch { session?.sendControl(VoiceControlEvent(event = "push_to_talk_end")) }
    }

    fun sendPing(clientSentAt: Long) {
        scope.launch { session?.sendControl(VoiceControlEvent(event = "ping", clientSentAt = clientSentAt)) }
    }
}
