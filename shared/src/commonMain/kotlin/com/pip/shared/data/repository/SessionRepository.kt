package com.pip.shared.data.repository

import com.pip.shared.core.storage.KeyValueStore
import com.pip.shared.domain.model.SessionId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * `AppSession`'s UUID management, moved to `commonMain` (PRD §6 Phase 3).
 * One id per install, shared by chat and voice (PRD §4.4).
 */
class SessionRepository(private val keyValueStore: KeyValueStore) {
    private val key = "app.session.id"

    val sessionId: SessionId by lazy {
        val existing = keyValueStore.getString(key)
        if (existing != null) {
            SessionId(existing)
        } else {
            val fresh = generateId()
            keyValueStore.putString(key, fresh)
            SessionId(fresh)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun generateId(): String = Uuid.random().toString()
}
