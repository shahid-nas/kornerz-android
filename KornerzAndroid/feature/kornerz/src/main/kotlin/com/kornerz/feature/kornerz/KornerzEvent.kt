package com.kornerz.feature.kornerz

/**
 * UiEvent for the Kornerz tab list. Mirrors architecture §2.1 (sealed
 * interface, `data object`/`data class` subtypes, no business logic in
 * composables).
 */
sealed interface KornerzEvent {
    data object Refresh : KornerzEvent
    data object Retry : KornerzEvent
    data object AvatarTapped : KornerzEvent

    /**
     * A carousel tile was tapped.
     *
     * @param kornerId the Korner to open.
     * @param themeColorHex the tile's accent colour as a `#RRGGBB`
     * hex string — the colour the tile was painted with. It travels
     * with the navigation so the detail screen paints itself in the
     * very colour the user tapped (iOS hands the tile's colour to the
     * detail screen the same way).
     */
    data class RowClicked(
        val kornerId: String,
        val themeColorHex: String,
    ) : KornerzEvent
}