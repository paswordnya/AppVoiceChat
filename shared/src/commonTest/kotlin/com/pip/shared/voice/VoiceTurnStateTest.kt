package com.pip.shared.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceTurnStateTest {
    @Test
    fun fromWireValue_matchesAllFifteenServerStates() {
        val expected =
            mapOf(
                "idle" to VoiceTurnState.IDLE,
                "listening" to VoiceTurnState.LISTENING,
                "recording" to VoiceTurnState.RECORDING,
                "processing" to VoiceTurnState.PROCESSING,
                "thinking" to VoiceTurnState.THINKING,
                "speaking" to VoiceTurnState.SPEAKING,
                "interrupted" to VoiceTurnState.INTERRUPTED,
                "paused" to VoiceTurnState.PAUSED,
                "waiting_queue" to VoiceTurnState.WAITING_QUEUE,
                "queue_processing" to VoiceTurnState.QUEUE_PROCESSING,
                "completed" to VoiceTurnState.COMPLETED,
                "cancelled" to VoiceTurnState.CANCELLED,
                "error" to VoiceTurnState.ERROR,
                "disconnected" to VoiceTurnState.DISCONNECTED,
                "reconnecting" to VoiceTurnState.RECONNECTING,
            )
        expected.forEach { (wire, state) ->
            assertEquals(state, VoiceTurnState.fromWireValue(wire))
        }
        assertEquals(15, VoiceTurnState.entries.size)
    }

    @Test
    fun fromWireValue_unknownReturnsNull() {
        assertNull(VoiceTurnState.fromWireValue("not_a_real_state"))
    }
}
