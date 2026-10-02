package com.pip.shared.chat

import com.pip.shared.network.dto.ChatServerEvent
import com.pip.shared.network.ws.FakeWebSocketTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Replays a real chat turn (audited against `ChatSocketClient.swift`'s
 * `handle(_:)`, PRD §4.4) through [ChatSession] via [FakeWebSocketTransport]
 * — matching PRD §17's Chat test requirement: "streaming token assembly...
 * retry/reconnect sequencing."
 */
class ChatSessionFixtureTest {
    @Test
    fun replayingAStreamedReply_assemblesTokensInOrder() =
        runTest {
            val transport = FakeWebSocketTransport()
            val session = ChatSession(transport, sessionId = "fixture-session", dispatcher = Dispatchers.Unconfined)
            session.connect()
            assertEquals("/ws/chat/fixture-session", transport.lastConnectedPath)

            val events = mutableListOf<ChatServerEvent>()
            val eventJob = launch(Dispatchers.Unconfined) { session.events.collect { events.add(it) } }

            transport.emitText("""{"event":"thinking"}""")
            transport.emitText("""{"event":"typing","text":"Ha"}""")
            transport.emitText("""{"event":"typing","text":"lo"}""")
            transport.emitText("""{"event":"typing","text":"!"}""")
            transport.emitText("""{"event":"done","text":"Halo!","model":"gemini"}""")

            val parser = StreamingParser()
            val assembled =
                events.filterIsInstance<ChatServerEvent.Token>().fold("") { _, token -> parser.append(token.text) }
            assertEquals("Halo!", assembled)

            val done = events.filterIsInstance<ChatServerEvent.Done>().single()
            assertEquals("Halo!", done.text)
            assertEquals("gemini", done.model)

            eventJob.cancel()
        }

    @Test
    fun replayingACommandReply_isDistinctFromNormalDone() =
        runTest {
            val transport = FakeWebSocketTransport()
            val session = ChatSession(transport, sessionId = "fixture-session", dispatcher = Dispatchers.Unconfined)
            session.connect()

            val events = mutableListOf<ChatServerEvent>()
            val eventJob = launch(Dispatchers.Unconfined) { session.events.collect { events.add(it) } }

            transport.emitText("""{"event":"command","text":"Model diganti ke Gemini."}""")

            val command = events.filterIsInstance<ChatServerEvent.Command>().single()
            assertEquals("Model diganti ke Gemini.", command.text)
            assertTrue(events.none { it is ChatServerEvent.Done })

            eventJob.cancel()
        }

    @Test
    fun send_encodesReplyMetadataInOutgoingPayload() =
        runTest {
            val transport = FakeWebSocketTransport()
            val session = ChatSession(transport, sessionId = "fixture-session", dispatcher = Dispatchers.Unconfined)

            session.send("balasan singkat", replySenderLabel = "Kamu", replySnippet = "pesan asli")

            val payload = transport.sentText.single()
            assertTrue(payload.contains("\"message\":\"balasan singkat\""))
            assertTrue(payload.contains("\"reply_sender_label\":\"Kamu\""))
            assertTrue(payload.contains("\"reply_snippet\":\"pesan asli\""))
        }

    @Test
    fun isConnected_reflectsUnderlyingTransport() =
        runTest {
            val transport = FakeWebSocketTransport()
            val session = ChatSession(transport, sessionId = "fixture-session", dispatcher = Dispatchers.Unconfined)

            assertEquals(false, session.isConnected.value)
            session.connect()
            assertEquals(true, session.isConnected.value)
            session.disconnect()
            assertEquals(false, session.isConnected.value)
        }
}
