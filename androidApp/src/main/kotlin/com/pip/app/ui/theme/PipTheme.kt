package com.pip.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Brand tokens, ported 1:1 from iOS's `PipTheme.swift` (PRD §13a's "design
 * system parity" — same gradient stops, same ink/cream values).
 */
object PipTheme {
    /** Default brand accent (the moc's `brandColor` default, "Blue"). */
    val accent = Color(0xFF3B82F6)

    /** `color-mix(in srgb, accent 60%, black)` — the splash gradient's dark stop. */
    val accentDeep = Color(0xFF234E94)

    /** Mascot face / heading ink color. */
    val ink = Color(0xFF2B2320)

    /** App background cream. */
    val cream = Color(0xFFFFFCF8)

    /** Default buddy ("Pip") mascot gradient stops. */
    val buddyFrom = Color(0xFFFF9466)
    val buddyTo = Color(0xFFFF5F82)
}

/**
 * Stand-ins for the moc's "Fredoka"/"Inter" fonts — rounded/default system
 * fonts, same fallback approach iOS takes (no bundled custom font either).
 */
fun pipDisplay(
    size: Int,
    weight: FontWeight = FontWeight.SemiBold,
): TextStyle = TextStyle(fontSize = size.sp, fontWeight = weight)

fun pipBody(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
): TextStyle = TextStyle(fontSize = size.sp, fontWeight = weight)

val pipTypography = Typography()
