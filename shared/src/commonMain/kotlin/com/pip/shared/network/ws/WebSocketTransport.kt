package com.pip.shared.network.ws

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Frame types crossing the WS boundary — shared by the real client and test fakes (PRD §17). */
sealed interface WsFrame {
    data class Text(val text: String) : WsFrame

    data class Binary(val bytes: ByteArray) : WsFrame
}

/**
 * Seam between [com.pip.shared.voice.VoiceSession]/[com.pip.shared.chat.ChatSession]
 * and the actual transport — lets tests replay fixture events through a
 * fake implementation instead of a live WS connection, matching PRD §17's
 * "replay real JSON fixtures" integration-test requirement without
 * fighting Ktor's incomplete `MockEngine` WebSocket support.
 */
interface WebSocketTransport {
    val incoming: SharedFlow<WsFrame>
    val isConnected: StateFlow<Boolean>

    fun connect(path: String)

    suspend fun sendText(text: String)

    suspend fun sendBytes(bytes: ByteArray)

    fun disconnect()
}
