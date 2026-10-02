package com.pip.shared.network.ws

import com.pip.shared.core.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.ktor.websocket.Frame as KtorFrame

/**
 * Shared reconnect/backoff core for [com.pip.shared.voice.VoiceSession]/
 * [com.pip.shared.chat.ChatSession] (PRD §5.2, §16's "exponential backoff
 * with a ceiling" NFR). Owns the Ktor WebSocket session lifecycle; callers
 * push/consume frames through [incoming]/`sendText`/`sendBytes` rather than
 * touching the underlying session.
 */
class ReconnectingWebSocketClient(
    private val appConfig: AppConfig,
    private val maxBackoffMs: Long = 30_000,
    private val initialBackoffMs: Long = 500,
) : WebSocketTransport {
    private val client = HttpClient { install(WebSockets) }
    private var session: DefaultClientWebSocketSession? = null
    private var scope: CoroutineScope? = null
    private var currentAttempt = 0
    private var manuallyStopped = false

    private val _incoming = MutableSharedFlow<WsFrame>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<WsFrame> = _incoming.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    override fun connect(path: String) {
        manuallyStopped = false
        val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = jobScope
        jobScope.launch { connectLoop(path) }
    }

    private suspend fun connectLoop(path: String) {
        while (!manuallyStopped) {
            try {
                client.webSocket(urlString = appConfig.wsBaseUrl + path) {
                    session = this
                    currentAttempt = 0
                    _isConnected.value = true
                    for (frame in incoming) {
                        when (frame) {
                            is KtorFrame.Text -> _incoming.emit(WsFrame.Text(frame.readText()))
                            is KtorFrame.Binary -> _incoming.emit(WsFrame.Binary(frame.readBytes()))
                            else -> Unit
                        }
                    }
                }
            } catch (e: Exception) {
                // Falls through to the backoff below — §16 requires this
                // not hammer reconnect attempts on a dead connection.
            }
            _isConnected.value = false
            session = null
            if (manuallyStopped) break
            val backoff = (initialBackoffMs * (1L shl currentAttempt.coerceAtMost(6))).coerceAtMost(maxBackoffMs)
            currentAttempt++
            delay(backoff)
        }
    }

    override suspend fun sendText(text: String) {
        session?.send(KtorFrame.Text(text))
    }

    override suspend fun sendBytes(bytes: ByteArray) {
        session?.send(KtorFrame.Binary(true, bytes))
    }

    override fun disconnect() {
        manuallyStopped = true
        scope?.cancel()
        scope = null
        session = null
        _isConnected.value = false
    }
}
