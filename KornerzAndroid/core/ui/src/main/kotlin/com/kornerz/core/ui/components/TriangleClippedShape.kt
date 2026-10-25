package com.kornerz.core.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Which corner of the square keeps its right angle. The opposite corner
 * is the one that gets cut away by the 45° diagonal, and the two corners
 * the diagonal meets keep a [cornerRadius] rounding. Mirrors the iOS
 * `TriangleClippedShapeType` enum (`.topRight` / `.bottomLeft`); iOS
 * only ships those two, `TopLeft` / `BottomRight` are the mirrored
 * extensions Android adds on top of the iOS pair.
 */
enum class TriangleCorner { TopRight, TopLeft, BottomRight, BottomLeft }

/**
 * A square clipped by a 45° diagonal into a right triangle whose
 * surviving corners are rounded by [cornerRadius]. 1:1 port of iOS
 * `TriangleClippedShape.swift`.
 *
 * The iOS `.topRight` path is:
 *  - `move(to:)` `(r, 0)` on the top edge,
 *  - a 135° arc around the top-left corner,
 *  - the diagonal down to the bottom-right corner,
 *  - a 135° arc around the bottom-right corner,
 *  - the right edge up, a 90° arc around the top-right corner,
 *  - `close` along the top edge.
 *
 * i.e. the *named* corner keeps the right angle and the opposite corner
 * is the one that is cut away. The `.bottomLeft` variant is the same
 * path rotated by 180°.
 */
fun TriangleClippedShape(
    corner: TriangleCorner = TriangleCorner.TopRight,
    cornerRadius: Dp = 0.dp,
): Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val r = (cornerRadius.value * density.density).coerceAtMost(minOf(w, h) / 2f)
        val path = Path().apply { topRightTriangle(w, h, r) }
        when (corner) {
            TriangleCorner.TopRight -> Unit
            TriangleCorner.TopLeft -> path.mirrorX(w)
            TriangleCorner.BottomRight -> path.mirrorY(h)
            TriangleCorner.BottomLeft -> path.mirrorXY(w, h)
        }
        return Outline.Generic(path)
    }
}

/**
 * The iOS `.topRight` path, expressed with Compose arcs. iOS sweeps its
 * arcs anticlockwise (`clockwise: true` with SwiftUI's angle
 * convention), which is a negative sweep in Compose.
 */
private fun Path.topRightTriangle(w: Float, h: Float, r: Float) {
    if (r <= 0f) {
        moveTo(0f, 0f)
        lineTo(w, 0f)
        lineTo(w, h)
        close()
        return
    }
    moveTo(r, 0f)
    arcTo(
        rect = Rect(0f, 0f, 2f * r, 2f * r),
        startAngleDegrees = 270f,
        sweepAngleDegrees = -135f,
        forceMoveTo = false,
    )
    arcTo(
        rect = Rect(w - 2f * r, h - 2f * r, w, h),
        startAngleDegrees = 135f,
        sweepAngleDegrees = -135f,
        forceMoveTo = false,
    )
    lineTo(w, r)
    arcTo(
        rect = Rect(w - 2f * r, 0f, w, 2f * r),
        startAngleDegrees = 0f,
        sweepAngleDegrees = -90f,
        forceMoveTo = false,
    )
    close()
}

/**
 * The mirrored variants of the path.
 *
 * Compose's [Matrix] is a **4×4** matrix, not the 3×3 affine one: it is
 * stored row-major (`values[row * 4 + column]`) and its translation
 * components live in **row 3** — `matrix[3, 0]` is `translateX` and
 * `matrix[3, 1]` is `translateY` (see `Matrix.map`/`Matrix.translate`).
 * Writing the offset to `[0, 2]` / `[1, 2]` instead lands it in the
 * perspective column, which `android.graphics.Matrix.setFrom` forces
 * back to zero — so the mirror would flip the shape about the origin
 * with **no translation**, moving the triangle to negative coordinates
 * where the clip discards it and the avatar renders as nothing at all.
 *
 * The mirror has to be about the shape's centre, so the offset is the
 * shape's own width/height.
 */
private fun Path.mirrorX(w: Float) {
    transform(Matrix().apply {
        this[0, 0] = -1f
        this[3, 0] = w
    })
}

private fun Path.mirrorY(h: Float) {
    transform(Matrix().apply {
        this[1, 1] = -1f
        this[3, 1] = h
    })
}

private fun Path.mirrorXY(w: Float, h: Float) {
    transform(Matrix().apply {
        this[0, 0] = -1f
        this[1, 1] = -1f
        this[3, 0] = w
        this[3, 1] = h
    })
}
