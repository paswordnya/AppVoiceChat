package com.pip.shared.analytics

/**
 * Typed event emission wrapping the shape `pip-voice-ai-dashboard`'s
 * `analytics_events.py` already accepts (PRD §6 Phase 7, §12) — so both
 * platforms emit identically-shaped events instead of each hand-rolling
 * its own. The dashboard's event contract is server-side and unmodified;
 * this only standardizes what the client sends into it.
 */
data class AnalyticsEvent(
    val name: String,
    val properties: Map<String, String> = emptyMap(),
)

interface AnalyticsEmitter {
    fun emit(event: AnalyticsEvent)
}

/** Default until Phase 7 wires a real transport — never silently drops in a way that masks a bug. */
class NoOpAnalyticsEmitter : AnalyticsEmitter {
    override fun emit(event: AnalyticsEvent) = Unit
}
