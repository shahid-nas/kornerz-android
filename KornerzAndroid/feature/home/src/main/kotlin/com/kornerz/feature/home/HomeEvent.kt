package com.kornerz.feature.home

sealed interface HomeEvent {
    data object Refresh : HomeEvent
    data object LoopOfDayRetry : HomeEvent
    data object UpcomingNooksRetry : HomeEvent
    data object YourKornerzRetry : HomeEvent
    data object RecentLoopsRetry : HomeEvent
    data object AvatarTapped : HomeEvent

    /**
     * A loop tile was tapped.
     *
     * @param loopId the loop to open.
     * @param accentColorHex the tile's accent colour as a `#RRGGBB`
     * hex string — the colour the tile was painted with. It travels
     * with the navigation so the detail screen paints itself in the
     * very colour the user tapped (iOS hands the tile's colour to the
     * detail screen the same way — `HomeTabCoordinator
     * .navigateToLoopDetail(_:)` reads `loopInfo.color`).
     */
    data class LoopOfDayClicked(
        val loopId: String,
        val accentColorHex: String,
    ) : HomeEvent

    data class NookClicked(val nookId: String) : HomeEvent

    /**
     * A "Your Kornerz" tile was tapped.
     *
     * @param kornerId the Korner to open.
     * @param themeColorHex the tile's accent colour as a `#RRGGBB`
     * hex string — the colour the tile was painted with. It travels
     * with the navigation so the detail screen paints itself in the
     * very colour the user tapped (iOS hands the tile's colour to the
     * detail screen the same way).
     */
    data class KornerClicked(
        val kornerId: String,
        val themeColorHex: String,
    ) : HomeEvent
}
