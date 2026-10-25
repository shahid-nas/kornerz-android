package com.kornerz.feature.videocall.ui

import androidx.compose.ui.geometry.Size

/**
 * Grid metrics for the subscriber area, ported 1:1 from iOS
 * `VideoCallViewModel` (`kornerz/Kornerz/SwiftUI/VideoCall/VideoCallViewModel.swift`).
 *
 * iOS lays the remote participants out in a hand-rolled grid rather
 * than a lazy list, so the Android port has to reproduce the same row
 * arithmetic to end up with the same tile sizes on both platforms.
 */

/** iOS `VideoCallViewModel.gridSpacing` — the gap between tiles and rows. */
internal const val VideoCallGridSpacing = 4f

/**
 * Splits the participants into grid rows.
 *
 * Direct port of iOS `subscriberGridRows()`:
 *  - 0 participants → no rows.
 *  - 2 participants → **two** rows of one tile each (each tile spans
 *    the full width). This is an iOS quirk, preserved deliberately.
 *  - otherwise → rows of two tiles, with a trailing row of one tile
 *    when the count is odd.
 */
internal fun <T> subscriberGridRows(items: List<T>): List<List<T>> {
    val count = items.size
    if (count <= 0) return emptyList()
    if (count == 2) return listOf(listOf(items[0]), listOf(items[1]))

    val rows = mutableListOf<List<T>>()
    var index = 0
    while (index < count) {
        if (count - index >= 2) {
            rows += listOf(items[index], items[index + 1])
            index += 2
        } else {
            rows += listOf(items[index])
            index += 1
        }
    }
    return rows
}

/**
 * Size of a single tile.
 *
 * Direct port of iOS `subscriberGridSize(for:rowItemCount:in:totalRows:)`:
 * the row height divides the container height by the row count (minus
 * the inter-row spacing), and a row with a single tile spans the full
 * container width while a row with two tiles splits it in half (minus
 * the inter-tile spacing).
 */
internal fun subscriberGridTileSize(
    rowItemCount: Int,
    containerWidth: Float,
    containerHeight: Float,
    totalRows: Int,
): Size {
    val totalVerticalSpacing = VideoCallGridSpacing * (totalRows - 1).coerceAtLeast(0)
    val rowHeight = if (totalRows <= 0) {
        containerHeight
    } else {
        (containerHeight - totalVerticalSpacing) / totalRows
    }
    val width = if (rowItemCount == 1) {
        containerWidth
    } else {
        (containerWidth - VideoCallGridSpacing) / 2f
    }
    return Size(
        width = width.coerceAtLeast(0f),
        height = rowHeight.coerceAtLeast(0f),
    )
}
