package com.pip.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Drives Pip's blinking independent of conversation state, ported from
 * iOS's `BlinkScheduler.swift` — randomized interval/duration, occasional
 * doubling, deferred (not skipped) mid-loud-speech, force-able on demand
 * for the barge-in reaction (PRD-Pip-Avatar-Animation).
 */
class BlinkState internal constructor(private val isHighAmplitude: () -> Boolean) {
    /** `1` = eyes fully open, `0` = fully shut. */
    val eyeOpen = Animatable(1f)

    internal suspend fun runLoop() {
        while (true) {
            delay((Random.nextDouble(2.5, 6.0) * 1000).toLong())

            while (isHighAmplitude()) {
                delay(60)
            }

            performBlink()

            if (Random.nextDouble() < 0.15) {
                delay((Random.nextDouble(0.05, 0.3) * 1000).toLong())
                performBlink()
            }
        }
    }

    suspend fun performBlink() {
        val duration = Random.nextDouble(0.12, 0.18)
        val closeMs = (duration * 0.4 * 1000).toInt()
        val openMs = (duration * 0.6 * 1000).toInt()
        eyeOpen.animateTo(0f, tween(closeMs))
        eyeOpen.animateTo(1f, tween(openMs))
    }
}

@Composable
fun rememberBlinkState(isHighAmplitude: () -> Boolean = { false }): BlinkState {
    val state = remember { BlinkState(isHighAmplitude) }
    LaunchedEffect(Unit) { state.runLoop() }
    return state
}
