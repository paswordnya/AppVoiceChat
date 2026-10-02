package com.pip.shared.voice

/**
 * Mirrors the server's `VoiceState` enum — 15 states (PRD §7/§7.1).
 * `StateFlow<VoiceTurnState>` is what [VoiceSession]/`VoiceSessionViewModel`
 * expose. Transitions between these are inferred from client-side
 * observation (PRD §7.1's caveat), not read from the server's
 * `voice_state_machine.py` directly — confirm before Sprint 4 (§22).
 */
enum class VoiceTurnState(val wireValue: String) {
    IDLE("idle"),
    LISTENING("listening"),
    RECORDING("recording"),
    PROCESSING("processing"),
    THINKING("thinking"),
    SPEAKING("speaking"),
    INTERRUPTED("interrupted"),
    PAUSED("paused"),
    WAITING_QUEUE("waiting_queue"),
    QUEUE_PROCESSING("queue_processing"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    ERROR("error"),
    DISCONNECTED("disconnected"),
    RECONNECTING("reconnecting"),
    ;

    companion object {
        fun fromWireValue(value: String): VoiceTurnState? = entries.find { it.wireValue == value }
    }
}
