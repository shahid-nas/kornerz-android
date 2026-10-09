package com.kornerz.core.ui.components

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Which corner the triangle is cut from. The iOS source-of-truth
 * supports several corners. The P2 Kornerz tile only uses [TopRight];
 * the others are implemented symmetrically for symmetry with the iOS
 * source.
 */
enum class TriangleCorner { TopRight, TopLeft, BottomRight, BottomLeft }

/**
 * Right-triangle clipped shape with rounded inner corners. 1:1 port of
 * iOS `TriangleClippedShape.swift`. The carved triangle removes one
 * corner of the layout rectangle; the two perpendicular edges meet the
 * diagonal with [cornerRadius] rounded corners.
 */
fun TriangleClippedShape(
    corner: TriangleCorner = TriangleCorner.TopRight,
    cornerRadius: Dp = 0.dp,
): Shape = GenericShape { size: Size, _ ->
    val w = size.width
    val h = size.height
    val r = cornerRadius.value
    topRightTriangle(w, h, r)
    when (corner) {
        TriangleCorner.TopRight -> Unit
        TriangleCorner.TopLeft -> mirrorX(w)
        TriangleCorner.BottomRight -> mirrorY(h)
        TriangleCorner.BottomLeft -> mirrorXY(w, h)
    }
}

private fun Path.topRightTriangle(w: Float, h: Float, r: Float) {
    moveTo(0f, 0f)
    lineTo(w - r, 0f)
    if (r > 0f) {
        arcTo(
            rect = Rect(Offset(w - 2 * r, 0f), Size(2 * r, 2 * r)),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
    }
    lineTo(w, h - r)
    if (r > 0f) {
        arcTo(
            rect = Rect(Offset(w - 2 * r, h - 2 * r), Size(2 * r, 2 * r)),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
    }
    lineTo(0f, h)
    close()
}

private fun Path.mirrorX(w: Float) {
    transform(Matrix().apply { this[0, 0] = -1f; this[0, 2] = w })
}

private fun Path.mirrorY(h: Float) {
    transform(Matrix().apply { this[1, 1] = -1f; this[1, 2] = h })
}

private fun Path.mirrorXY(w: Float, h: Float) {
    transform(Matrix().apply {
        this[0, 0] = -1f; this[0, 2] = w
        this[1, 1] = -1f; this[1, 2] = h
    })
}
