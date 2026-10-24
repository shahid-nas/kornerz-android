package com.kornerz.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.kornerz.core.model.LoopAccentPalette

/**
 * Cyclic palette used by the Home-feed tiles (Recent articles and the
 * Kornerz carousel fallback accent) and by every per-loop accent colour
 * on Android. 1:1 port of iOS `RandomColorService.color(for: index)` in
 * `kornerz/Kornerz/Backend/Repositories/RandomColorService.swift`. The
 * five swatches are picked in order; index N uses `palette[N % palette.size]`.
 *
 * The hex swatches themselves live in `core/model`'s
 * [LoopAccentPalette] — the domain module has to be able to name a
 * loop's accent colour without Compose, and `core/ui` depends on
 * `core/model`, not the other way round. This object is therefore the
 * single **UI** entry point: it owns the `Color` rendering of the
 * palette and the hex-to-`Color` conversion the detail screen needs.
 *
 * Colors follow the same hex prefixes (`0xFF` for full opacity) used in
 * [KornerzPalette]. `kornerzCardColor()` is the only public entry point.
 */
object KornerzCardPalette {

    /** The five iOS swatches, in iOS order. */
    val hexSwatches: List<String> = LoopAccentPalette.hexColors

    private val swatches: List<Color> = hexSwatches.map { hexToColor(it) }

    /** The palette colour for [index], wrapping modulo the palette size. */
    fun color(index: Int): Color = swatches[wrap(index)]

    /**
     * The palette **hex** for [index], wrapping modulo the palette
     * size — the very colour [color] renders, as a `#RRGGBB` string.
     *
     * The feed rows paint their thumbnail with [color] and hand the
     * matching hex to the detail screen, so the two agree on the
     * colour of a loop (iOS passes the row's colour to the detail
     * screen the same way).
     */
    fun hexForIndex(index: Int): String = hexSwatches[wrap(index)]

    /**
     * The palette colour for the hex string [hex] — e.g. the
     * `LoopDetailInfo.accentColorHex` the domain layer resolved.
     *
     * Returns `null` when [hex] is not a `#RRGGBB` string, so a caller
     * can fall back to a default.
     */
    fun colorFromHex(hex: String?): Color? {
        val cleaned = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
        return runCatching { Color(android.graphics.Color.parseColor("#$cleaned")) }.getOrNull()
    }

    val size: Int get() = swatches.size

    private fun wrap(index: Int): Int = ((index % swatches.size) + swatches.size) % swatches.size

    private fun hexToColor(hex: String): Color =
        Color(android.graphics.Color.parseColor(hex))
}

/** Public convenience for `KornerzCardPalette.color(index)`. */
fun kornerzCardColor(index: Int): Color = KornerzCardPalette.color(index)
