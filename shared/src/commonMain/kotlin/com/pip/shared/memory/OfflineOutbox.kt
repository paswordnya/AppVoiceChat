package com.pip.shared.memory

/**
 * Messages queued while disconnected (PRD §5a/§9) — in-memory only today;
 * lost on app kill until §5.3's SQLDelight promotion decision is made
 * (§22 open question). Never a fabricated reply, only a visibly "pending"
 * queue entry (PRD §16's Offline mode NFR).
 */
data class OutboxEntry(
    val id: String,
    val sessionId: String,
    val text: String,
    val queuedAtEpochMs: Long,
)

class OfflineOutbox {
    private val entries = mutableListOf<OutboxEntry>()

    fun enqueue(entry: OutboxEntry) {
        entries.add(entry)
    }

    fun pending(): List<OutboxEntry> = entries.toList()

    fun remove(id: String) {
        entries.removeAll { it.id == id }
    }

    fun clear() = entries.clear()
}
