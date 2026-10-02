package com.pip.shared.chat

import com.pip.shared.network.dto.ChatEventParser
import com.pip.shared.network.dto.ChatOutgoingMessage
import com.pip.shared.network.dto.ChatServerEvent
import com.pip.shared.network.ws.WebSocketTransport
import com.pip.shared.network.ws.WsFrame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Ktor WS session to `/ws/chat/{id}` (PRD §8): connect, reconnect (via
 * [WebSocketTransport]), JSON event dispatch (`thinking`/`typing`/`done`/
 * `command`/`error`/`notice`). Takes the [WebSocketTransport] interface
 * rather than the concrete Ktor client so tests can replay fixtures
 * through a fake (PRD §17).
 */
class ChatSession(
    private val socket: WebSocketTransport,
    private val sessionId: String,
    private val authToken: String? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _events = MutableSharedFlow<ChatServerEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ChatServerEvent> = _events.asSharedFlow()

    /** Feeds the client AI Router's reachability check (PRD §9). */
    val isConnected: StateFlow<Boolean> = socket.isConnected

    fun connect() {
        // ws_chat.py requires a valid ?token= query param before accepting the
        // handshake (schema.sql section 3 / auth.py) — query param, not a
        // header, since WS-upgrade header support varies across Ktor engines.
        val tokenSuffix = authToken?.let { "?token=$it" } ?: ""
        socket.connect("/ws/chat/$sessionId$tokenSuffix")
        scope.launch {
            socket.incoming.collect { frame ->
                if (frame is WsFrame.Text) {
                    ChatEventParser.parse(frame.text)?.let { _events.emit(it) }
                }
            }
        }
    }

    suspend fun send(
        message: String,
        replySenderLabel: String? = null,
        replySnippet: String? = null,
    ) {
        val payload = ChatOutgoingMessage(message, replySenderLabel, replySnippet)
        socket.sendText(json.encodeToString(payload))
    }

    fun disconnect() {
        socket.disconnect()
    }
}
