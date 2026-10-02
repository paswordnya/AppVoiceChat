package com.pip.shared.network.ws

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fixture-replay test double for [WebSocketTransport] (PRD §17) — no real
 * network, no Ktor engine. Tests call [emitText]/[emitBinary] to replay a
 * captured server event sequence and assert on the resulting
 * [com.pip.shared.voice.VoiceSession]/[com.pip.shared.chat.ChatSession] state.
 */
class FakeWebSocketTransport : WebSocketTransport {
    private val _incoming = MutableSharedFlow<WsFrame>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<WsFrame> = _incoming.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    var lastConnectedPath: String? = null
        private set
    val sentText = mutableListOf<String>()
    val sentBytes = mutableListOf<ByteArray>()

    override fun connect(path: String) {
        lastConnectedPath = path
        _isConnected.value = true
    }

    override suspend fun sendText(text: String) {
        sentText.add(text)
    }

    override suspend fun sendBytes(bytes: ByteArray) {
        sentBytes.add(bytes)
    }

    override fun disconnect() {
        _isConnected.value = false
    }

    suspend fun emitText(text: String) {
        _incoming.emit(WsFrame.Text(text))
    }

    suspend fun emitBinary(bytes: ByteArray) {
        _incoming.emit(WsFrame.Binary(bytes))
    }
}
