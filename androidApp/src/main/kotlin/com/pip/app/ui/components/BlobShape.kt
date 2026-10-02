package com.pip.app.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Pip's signature organic "blob" silhouette, ported 1:1 from iOS's
 * `BlobShape.swift` — same CSS `border-radius: 42% 58% 65% 35% / 45% 40%
 * 60% 55%` corner fractions and the same cubic-bezier kappa constant, so
 * the mascot reads identically on both platforms (PRD §13a).
 */
class BlobShape(
    private val horizontal: FloatArray = floatArrayOf(0.42f, 0.58f, 0.65f, 0.35f), // TL, TR, BR, BL
    private val vertical: FloatArray = floatArrayOf(0.45f, 0.40f, 0.60f, 0.55f), // TL, TR, BR, BL
) : Shape {
    private val kappa = 0.5522847498f

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height

        val aTL = horizontal[0] * w
        val bTL = vertical[0] * h
        val aTR = horizontal[1] * w
        val bTR = vertical[1] * h
        val aBR = horizontal[2] * w
        val bBR = vertical[2] * h
        val aBL = horizontal[3] * w
        val bBL = vertical[3] * h

        val topMidX = aTL
        val topMidY = 0f
        val rightMidX = w
        val rightMidY = bTR
        val bottomMidX = w - aBR
        val bottomMidY = h
        val leftMidX = 0f
        val leftMidY = h - bBL

        val path =
            Path().apply {
                moveTo(topMidX, topMidY)
                // Top-right corner: top edge -> right edge.
                cubicTo(
                    topMidX + aTR * kappa,
                    topMidY,
                    rightMidX,
                    rightMidY - bTR * kappa,
                    rightMidX,
                    rightMidY,
                )
                // Bottom-right corner: right edge -> bottom edge.
                cubicTo(
                    rightMidX,
                    rightMidY + bBR * kappa,
                    bottomMidX + aBR * kappa,
                    bottomMidY,
                    bottomMidX,
                    bottomMidY,
                )
                // Bottom-left corner: bottom edge -> left edge.
                cubicTo(
                    bottomMidX - aBL * kappa,
                    bottomMidY,
                    leftMidX,
                    leftMidY + bBL * kappa,
                    leftMidX,
                    leftMidY,
                )
                // Top-left corner: left edge -> top edge, closing the blob.
                cubicTo(
                    leftMidX,
                    leftMidY - bTL * kappa,
                    topMidX - aTL * kappa,
                    topMidY,
                    topMidX,
                    topMidY,
                )
                close()
            }
        return Outline.Generic(path)
    }
}
