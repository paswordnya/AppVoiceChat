package com.pip.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Mirrors iOS's `BuddyStore.shared`. In-memory selection, mirrored to/from
 * the backend `/settings` `buddy` field by [com.pip.app.ui.chat.ChatSessionScreen]
 * (loads on start, pushes on change) — this object itself stays a plain,
 * unpersisted holder so screens that only read `selected` don't need to
 * know about Settings/Koin at all. */
object BuddyStore {
    var selected: Buddy by mutableStateOf(Buddies.pip)
        private set

    fun select(buddy: Buddy) {
        selected = buddy
    }

    fun cycleNext() {
        val currentIndex = Buddies.all.indexOf(selected)
        selected = Buddies.all[(currentIndex + 1) % Buddies.all.size]
    }
}
