package com.pip.shared.voice

import com.pip.shared.network.dto.VoiceControlEvent
import com.pip.shared.network.dto.VoiceEventParser
import com.pip.shared.network.dto.VoiceServerEvent
import com.pip.shared.network.ws.WebSocketTransport
import com.pip.shared.network.ws.WsFrame
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Ktor WebSocket session to `/ws/voice/{id}` (PRD §7): connect,
 * reconnect/backoff (via [WebSocketTransport]), JSON event parse +
 * dispatch. Explicitly NOT audio I/O — PCM bytes are handed in by the
 * native audio engine and forwarded as-is (PRD §10's pipeline). Takes the
 * [WebSocketTransport] interface rather than the concrete Ktor client so
 * tests can replay fixtures through a fake (PRD §17).
 */
class VoiceSession(
    private val socket: WebSocketTransport,
    private val sessionId: String,
    private val authToken: String? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(VoiceTurnState.IDLE)
    val state: StateFlow<VoiceTurnState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<VoiceServerEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<VoiceServerEvent> = _events.asSharedFlow()

    /**
     * Binary WS frames — TTS reply audio, PCM16 mono 24kHz (PRD §4.4). Was
     * previously dropped entirely (only `Frame.Text` was handled); native
     * audio playback consumes this to actually hear a reply.
     */
    private val _audioFrames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val audioFrames: SharedFlow<ByteArray> = _audioFrames.asSharedFlow()

    fun start() {
        // ws_voice.py requires a valid ?token= query param before accepting the
        // handshake (schema.sql section 3 / auth.py) — same reasoning as ChatSession.
        val tokenSuffix = authToken?.let { "?token=$it" } ?: ""
        socket.connect("/ws/voice/$sessionId$tokenSuffix")
        scope.launch {
            socket.incoming.collect { frame ->
                when (frame) {
                    is WsFrame.Text -> {
                        val event = VoiceEventParser.parse(frame.text) ?: return@collect
                        _events.emit(event)
                        if (event is VoiceServerEvent.State) {
                            VoiceTurnState.fromWireValue(event.state)?.let { _state.value = it }
                        }
                    }
                    is WsFrame.Binary -> _audioFrames.emit(frame.bytes)
                }
            }
        }
    }

    suspend fun sendAudioFrame(bytes: ByteArray) {
        socket.sendBytes(bytes)
    }

    suspend fun sendControl(event: VoiceControlEvent) {
        socket.sendText(json.encodeToString(event))
    }

    fun stop() {
        socket.disconnect()
        _state.value = VoiceTurnState.IDLE
    }
}
