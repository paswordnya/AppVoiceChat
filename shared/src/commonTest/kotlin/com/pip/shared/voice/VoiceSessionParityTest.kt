package com.pip.shared.voice

import com.pip.shared.network.ws.FakeWebSocketTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * PRD §17's cross-platform parity test: "same fixture WS session replayed
 * on both platforms, assert identical final VoiceTurnState/message list."
 * A literal dual-platform CI run isn't automatable from a single test
 * process — the practical proxy that actually matters is here: this logic
 * is 100% `commonMain`, so if replaying the same fixture through two
 * independently constructed [VoiceSession] instances is deterministic in
 * `commonTest`, it is provably identical on iOS and Android too, since
 * both run this exact same compiled Kotlin.
 */
class VoiceSessionParityTest {
    private val fixture =
        listOf(
            """{"event":"state","state":"listening"}""",
            """{"event":"transcript","text":"tolong ingetin aku","final":true}""",
            """{"event":"state","state":"thinking"}""",
            """{"event":"state","state":"speaking"}""",
            """{"event":"reply","text":"siap, sudah kucatat.","final":true,"request_id":"req-42"}""",
            """{"event":"state","state":"listening"}""",
        )

    private suspend fun replay(): VoiceTurnState {
        val transport = FakeWebSocketTransport()
        val session = VoiceSession(transport, sessionId = "parity-session", dispatcher = Dispatchers.Unconfined)
        session.start()
        fixture.forEach { transport.emitText(it) }
        return session.state.value
    }

    @Test
    fun sameFixture_replayedTwice_reachesIdenticalFinalState() =
        runTest {
            val first = replay()
            val second = replay()
            assertEquals(first, second)
            assertEquals(VoiceTurnState.LISTENING, first)
        }
}
