package com.kornerz.core.model

/**
 * UI projection of the loop-detail screen's data — the Android counterpart
 * of the iOS `LoopInfo` struct
 * (`kornerz/Kornerz/SwiftUI/Dashboard/Loops/LoopsTab/LoopsTabViewModel.swift:14-40`).
 *
 * iOS resolves the loop, its owner and its community **before** pushing the
 * screen and hands the whole bundle to the detail view model. Android
 * resolves the same three pieces inside the detail screen itself (see
 * `core/network/.../loops/LoopDetailRepository.kt`), so the screen's single
 * input object is this projection.
 *
 * The accent colour travels as a `#RRGGBB` hex **string** rather than a
 * Compose `Color`, because `core/model` is a plain Kotlin/JVM module and
 * must stay free of Compose and `android.*` types (see the `loop-domain`
 * spec). The hex-to-`Color` conversion happens in the UI layer.
 */
data class LoopDetailInfo(
    val loop: Loop,
    val owner: User? = null,
    val community: Community? = null,
    /**
     * The per-loop accent colour as a `#RRGGBB` hex string, drawn from the
     * iOS five-colour palette and cycled by an index derived from the loop
     * id. Defaults to the colour derived from [loop]'s own id, so a
     * partially-resolved detail is representable without extra arguments.
     */
    val accentColorHex: String = LoopAccentPalette.hexForLoopId(loop.id.orEmpty()),
)

/**
 * The iOS per-loop accent palette — the Android counterpart of
 * `RandomColorService.color(for:)` in
 * `kornerz/Kornerz/Backend/Repositories/RandomColorService.swift`.
 *
 * The five swatches are used in order and the index wraps modulo the
 * palette size, exactly as iOS does. The colours are the same five that
 * `core/ui`'s `KornerzCardPalette` carries, so the UI layer can convert the
 * hex to a `Color` with the one shared palette instead of keeping a second
 * copy of the swatches.
 */
object LoopAccentPalette {

    /** The five iOS accent colours, in iOS order. */
    val hexColors: List<String> = listOf(
        "#E27D5A", // Coral
        "#AD598D", // Plum
        "#86BFA9", // Sage
        "#68B6CC", // Aqua
        "#EBAA4A", // Saffron
    )

    /** The number of entries in the palette. */
    const val SIZE: Int = 5

    /**
     * The palette colour for [index], wrapping modulo the palette size.
     *
     * A negative index wraps too (iOS's `index % count` would trap on a
     * negative operand; the Android port normalises instead, so the
     * function never throws).
     */
    fun hexForIndex(index: Int): String = hexColors[wrap(index)]

    /**
     * The palette index for [loopId].
     *
     * The index is derived from the id so the same loop always renders in
     * the same colour — iOS derives it from the row index, which does not
     * survive a deep link or a re-ordering.
     *
     * When the id ends with a decimal ordinal (`"l1"`, `"loop-42"`) that
     * ordinal is used, so consecutive ids get consecutive palette entries.
     * The ordinal is 1-based, matching how the ids are written, and is
     * shifted to a 0-based palette index. Ids without a trailing ordinal
     * fall back to a stable hash of the id, which keeps the mapping
     * deterministic for UUID-shaped ids.
     */
    fun indexForLoopId(loopId: String): Int {
        val ordinal = loopId.takeLastWhile { it.isDigit() }.toIntOrNull()
        return if (ordinal != null) ordinal - 1 else loopId.hashCode()
    }

    /**
     * The palette colour for [loopId] — deterministic for a given id.
     */
    fun hexForLoopId(loopId: String): String = hexForIndex(indexForLoopId(loopId))

    /**
     * The route-safe form of [hex] — the six hex digits **without** the
     * leading `#`.
     *
     * A navigation route is URI-shaped: a `#` inside a query value would
     * be parsed as the fragment separator and the colour would be lost,
     * so the colour travels without it.
     */
    fun routeArgument(hex: String): String = hex.trim().removePrefix("#").uppercase()

    /**
     * The canonical `#RRGGBB` form of [hex] — the `#` is added when it
     * is missing and the digits are upper-cased.
     *
     * A value that is not a six-digit hex colour is returned trimmed
     * but otherwise unchanged, so a caller can recognise it and fall
     * back to the default.
     */
    fun canonical(hex: String): String {
        val digits = hex.trim().removePrefix("#")
        return if (isHexColor(digits)) "#${digits.uppercase()}" else hex.trim()
    }

    /**
     * Whether [hex] is a six-digit hex colour, with or without the
     * leading `#`.
     */
    fun isHexColor(hex: String): Boolean {
        val digits = hex.trim().removePrefix("#")
        return digits.length == 6 && digits.all { it.isHexDigit() }
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun wrap(index: Int): Int = ((index % SIZE) + SIZE) % SIZE
}
