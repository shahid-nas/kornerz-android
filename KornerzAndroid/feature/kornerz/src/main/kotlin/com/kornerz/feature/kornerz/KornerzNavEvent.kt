package com.kornerz.feature.kornerz

/**
 * Side-channel navigation events emitted by [KornerzViewModel] over
 * `Channel<KornerzNavEvent>` (architecture §2.4). The shell collects
 * these and routes via `NavController.navigate(...)`.
 */
sealed interface KornerzNavEvent {
    data object NavigateToProfile : KornerzNavEvent

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
    ) : KornerzNavEvent
}