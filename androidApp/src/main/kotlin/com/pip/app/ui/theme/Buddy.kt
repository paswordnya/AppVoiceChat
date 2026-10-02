package com.pip.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A selectable mascot personality, ported 1:1 from iOS's `Buddy.swift`
 * (same gradients, same id/name — PRD §13a's design system parity). Pitch/
 * rate fields are omitted here: they drive `voice_app.mp3` playback, which
 * is native per-platform audio, out of this shared-data-only port's scope.
 */
data class Buddy(
    val id: String,
    val name: String,
    val colorFrom: Color,
    val colorTo: Color,
)

object Buddies {
    val pip = Buddy("pip", "Pip", Color(0xFFFF9466), Color(0xFFFF5F82))
    val bloop = Buddy("bloop", "Bloop", Color(0xFF54D6C8), Color(0xFF3E8BFF))
    val sunny = Buddy("sunny", "Sunny", Color(0xFFFFD24D), Color(0xFFFF9F43))
    val coco = Buddy("coco", "Coco", Color(0xFFC58BFF), Color(0xFF7C6EF6))

    val all = listOf(pip, bloop, sunny, coco)
}
