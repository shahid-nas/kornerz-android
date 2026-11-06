package com.kornerz.feature.home

/**
 * Navigation intents emitted by the Home graph (architecture §2.4).
 * Each event is resolved by `HomeNavResolver`, which currently shows a
 * "Coming in Phase N" snackbar so destinations can land in P3–P10
 * without crashing the P2 Home tab.
 */
sealed interface HomeNavEvent {
    data object NavigateToProfile : HomeNavEvent

    /**
     * Opens a loop's detail screen.
     *
     * @param loopId the loop to open.
     * @param accentColorHex the tile's accent colour as a `#RRGGBB`
     * hex string — the colour the tile was painted with. The detail
     * screen inherits it, so the loop keeps one colour across the
     * feed and the detail.
     */
    data class NavigateToLoopDetail(
        val loopId: String,
        val accentColorHex: String,
    ) : HomeNavEvent

    data class NavigateToEventDetail(val nookId: String) : HomeNavEvent

    /**
     * Opens the Korner detail.
     *
     * @param kornerId the Korner to open.
     * @param themeColorHex the tile's accent colour as a `#RRGGBB`
     * hex string — the colour the tile was painted with. The detail
     * screen inherits it, so the Korner keeps one colour across the
     * carousel and the detail.
     */
    data class NavigateToKornerDetail(
        val kornerId: String,
        val themeColorHex: String,
    ) : HomeNavEvent
}
