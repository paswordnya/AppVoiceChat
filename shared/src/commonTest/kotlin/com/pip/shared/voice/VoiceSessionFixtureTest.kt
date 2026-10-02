package com.pip.shared.voice

import com.pip.shared.network.dto.VoiceServerEvent
import com.pip.shared.network.ws.FakeWebSocketTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Replays a real turn sequence (audited against `VoiceSocketClient.swift`'s
 * `handleEvent`, PRD §4.4) through [VoiceSession] via [FakeWebSocketTransport]
 * — no live WS connection — matching PRD §17's "replay real JSON fixtures
 * captured from the actual backend" integration-test requirement.
 * [Dispatchers.Unconfined] makes the session's internal collection loop
 * run eagerly on the test thread instead of racing a background pool.
 */
class VoiceSessionFixtureTest {
    @Test
    fun replayingATurn_reachesExpectedFinalStateAndEvents() =
        runTest {
            val transport = FakeWebSocketTransport()
            val session = VoiceSession(transport, sessionId = "fixture-session", dispatcher = Dispatchers.Unconfined)
            session.start()
            assertEquals("/ws/voice/fixture-session", transport.lastConnectedPath)

            val events = mutableListOf<VoiceServerEvent>()
            val eventJob = launch(Dispatchers.Unconfined) { session.events.collect { events.add(it) } }

            // Fixture: listening -> partial/final transcript -> thinking -> speaking -> reply -> listening
            transport.emitText("""{"event":"state","state":"listening"}""")
            assertEquals(VoiceTurnState.LISTENING, session.state.value)

            transport.emitText("""{"event":"transcript","text":"halo","final":false}""")
            transport.emitText("""{"event":"transcript","text":"halo pip","final":true}""")
            transport.emitText("""{"event":"state","state":"thinking"}""")
            assertEquals(VoiceTurnState.THINKING, session.state.value)

            transport.emitText("""{"event":"state","state":"speaking"}""")
            transport.emitText(
                """{"event":"reply","text":"halo juga!","final":true,"request_id":"req-1"}""",
            )
            transport.emitText("""{"event":"state","state":"listening"}""")

            assertEquals(VoiceTurnState.LISTENING, session.state.value)
            val finalReply = events.filterIsInstance<VoiceServerEvent.Reply>().single()
            assertEquals("halo juga!", finalReply.text)
            assertEquals("req-1", finalReply.requestId)
            assertTrue(events.filterIsInstance<VoiceServerEvent.Transcript>().any { it.final && it.text == "halo pip" })

            eventJob.cancel()
        }

    @Test
    fun replayingBinaryFrame_surfacesAsAudioFrame() =
        runTest {
            val transport = FakeWebSocketTransport()
            val session = VoiceSession(transport, sessionId = "fixture-session", dispatcher = Dispatchers.Unconfined)
            session.start()

            val audioChunks = mutableListOf<ByteArray>()
            val audioJob = launch(Dispatchers.Unconfined) { session.audioFrames.collect { audioChunks.add(it) } }

            val pcmChunk = byteArrayOf(1, 2, 3, 4)
            transport.emitBinary(pcmChunk)

            assertEquals(1, audioChunks.size)
            assertTrue(audioChunks.first().contentEquals(pcmChunk))

            audioJob.cancel()
        }

    @Test
    fun interrupt_isSurfacedAsEvent() =
        runTest {
            val transport = FakeWebSocketTransport()
            val session = VoiceSession(transport, sessionId = "fixture-session", dispatcher = Dispatchers.Unconfined)
            session.start()

            val events = mutableListOf<VoiceServerEvent>()
            val eventJob = launch(Dispatchers.Unconfined) { session.events.collect { events.add(it) } }

            transport.emitText("""{"event":"state","state":"speaking"}""")
            transport.emitText("""{"event":"interrupt"}""")
            transport.emitText("""{"event":"state","state":"interrupted"}""")

            assertEquals(VoiceTurnState.INTERRUPTED, session.state.value)
            assertTrue(events.any { it is VoiceServerEvent.Interrupt })

            eventJob.cancel()
        }
}
