package com.kornerz.feature.loops.components

import androidx.compose.ui.graphics.Color

/**
 * Per-row accent color service. Mirrors the iOS `colorService.color(for:)`
 * helper in `LoopsTabViewModel.swift:195` — the Loops tab cycles
 * through a small palette so each row has a distinct accent for the
 * title / divider / icon tint.
 *
 * The palette is intentionally limited to a handful of warm + cool
 * colors so the feed stays visually quiet.
 */
object RandomColorService {

    private val palette = listOf(
        Color(0xFF5D47ED), // indigo
        Color(0xFFE94B6A), // raspberry
        Color(0xFF22A699), // teal
        Color(0xFFF2994A), // orange
        Color(0xFFBB6BD9), // violet
        Color(0xFF27AE60), // emerald
        Color(0xFFE2B43C), // gold
        Color(0xFF2D9CDB), // sky
    )

    fun colorFor(index: Int): Color {
        if (palette.isEmpty()) return Color(0xFF5D47ED)
        val i = ((index % palette.size) + palette.size) % palette.size
        return palette[i]
    }
}
