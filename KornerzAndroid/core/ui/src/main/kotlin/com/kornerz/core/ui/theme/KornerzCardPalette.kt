package com.kornerz.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Cyclic palette used by the Home-feed tiles (Recent articles and the
 * Kornerz carousel fallback accent). 1:1 port of iOS
 * `RandomColorService.color(for: index)` in
 * `kornerz/Kornerz/Services/RandomColorService.swift`. The five swatches
 * are picked in order; index N uses `palette[N % palette.size]`.
 *
 * Colors follow the same hex prefixes (`0xFF` for full opacity) used in
 * [KornerzPalette]. `kornerzCardColor()` is the only public entry point.
 */
object KornerzCardPalette {

    private val swatches: List<Color> = listOf(
        Color(0xFFE27D5A), // Coral
        Color(0xFFAD598D), // Plum
        Color(0xFF86BFA9), // Sage
        Color(0xFF68B6CC), // Aqua
        Color(0xFFEBAA4A), // Saffron
    )

    fun color(index: Int): Color = swatches[((index % swatches.size) + swatches.size) % swatches.size]

    val size: Int get() = swatches.size
}

/** Public convenience for `KornerzCardPalette.color(index)`. */
fun kornerzCardColor(index: Int): Color = KornerzCardPalette.color(index)
