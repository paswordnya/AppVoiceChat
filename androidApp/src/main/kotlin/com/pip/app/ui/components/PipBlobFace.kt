package com.pip.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pip.app.ui.theme.PipTheme
import kotlin.math.min

/**
 * Pip's idle mascot face (two closed/happy eyes + a smile), ported 1:1 from
 * iOS's `PipBlobFace.swift` — same reference proportions (150pt hero blob)
 * and same eye/mouth sizing/offsets (PRD §13a's design system parity).
 *
 * @param talkLevel `0...1`, driven by playing TTS audio level so the mouth
 * stretches while Pip talks. `0` (default) is the idle closed-smile look.
 * @param eyeOpen `1` = eyes open, `0` = mid-blink — drive this from a blink
 * loop (see [rememberBlinkState]) for a self-contained idle blink, or from
 * shared state for barge-in-synced blinking.
 */
@Composable
fun PipBlobFace(
    modifier: Modifier = Modifier,
    color: Color = PipTheme.ink,
    talkLevel: Float = 0f,
    eyeOpen: Float = 1f,
) {
    BoxWithConstraints(modifier = modifier) {
        val referenceSize = 150f
        val sizeDp = min(maxWidth.value, maxHeight.value)
        val scale = if (sizeDp > 0f) sizeDp / referenceSize else 1f

        val eyeWidthDp = (12f * scale).dp
        val eyeHeightDp = (7f * scale).dp
        val eyeInsetDp = (48f * scale).dp
        val eyeTopDp = (60f * scale).dp
        val mouthWidthDp = (18f * scale).dp
        val mouthHeightDp = (9f * scale).dp
        val mouthBottomDp = (50f * scale).dp
        val strokeWidthDp = maxOf(1.5f, 2.5f * scale).dp

        val clampedTalk = talkLevel.coerceIn(0f, 1f)
        val openness = eyeOpen.coerceIn(0f, 1f)
        val blinkSquash = 1f - openness

        HappyArc(
            modifier =
                Modifier
                    .offset(x = eyeInsetDp, y = eyeTopDp - eyeHeightDp / 2)
                    .size(width = eyeWidthDp, height = eyeHeightDp)
                    .graphicsLayer(scaleY = 1f - blinkSquash * 0.92f, transformOrigin = TransformOrigin.Center),
            color = color,
            strokeWidth = strokeWidthDp,
        )

        HappyArc(
            modifier =
                Modifier
                    .offset(x = maxWidth - eyeInsetDp - eyeWidthDp, y = eyeTopDp - eyeHeightDp / 2)
                    .size(width = eyeWidthDp, height = eyeHeightDp)
                    .graphicsLayer(scaleY = 1f - blinkSquash * 0.92f, transformOrigin = TransformOrigin.Center),
            color = color,
            strokeWidth = strokeWidthDp,
        )

        HappyArc(
            modifier =
                Modifier
                    .offset(x = maxWidth / 2 - mouthWidthDp / 2, y = maxHeight - mouthBottomDp - mouthHeightDp / 2)
                    .size(width = mouthWidthDp, height = mouthHeightDp)
                    .graphicsLayer(
                        scaleX = 1f - clampedTalk * 0.12f,
                        scaleY = 1f + clampedTalk * 2.1f,
                        transformOrigin = TransformOrigin(0.5f, 0f),
                    ),
            color = color,
            strokeWidth = strokeWidthDp,
        )
    }
}

/** A single "happy" arc — a shallow smile-shaped curve, matching iOS's `HappyArcShape`. */
@Composable
private fun HappyArc(
    modifier: Modifier,
    color: Color,
    strokeWidth: Dp,
) {
    Canvas(modifier = modifier) {
        val path =
            Path().apply {
                moveTo(0f, 0f)
                quadraticTo(size.width / 2f, size.height, size.width, 0f)
            }
        drawPath(path = path, color = color, style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round))
    }
}
