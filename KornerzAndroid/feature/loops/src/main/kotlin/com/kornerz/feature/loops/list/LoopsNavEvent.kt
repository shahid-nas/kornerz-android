package com.kornerz.feature.loops.list

sealed interface LoopsNavEvent {
    data object NavigateToProfile : LoopsNavEvent

    /**
     * Opens a loop's detail screen.
     *
     * @param loopId the loop to open.
     * @param accentColorHex the feed row's accent colour as a
     * `#RRGGBB` hex string — the colour the row was painted with. The
     * detail screen inherits it, so the loop keeps one colour across
     * the feed and the detail (iOS `LoopsTabCoordinator
     * .navigateToLoopDetail(_:color:)`).
     */
    data class NavigateToLoopDetail(
        val loopId: String,
        val accentColorHex: String,
    ) : LoopsNavEvent
}
