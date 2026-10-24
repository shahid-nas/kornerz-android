package com.kornerz.feature.loops.list

import com.kornerz.core.model.LoopsFilter

sealed interface LoopsEvent {
    data object Refresh : LoopsEvent
    data object Retry : LoopsEvent
    data object AvatarTapped : LoopsEvent

    /**
     * A feed row was tapped.
     *
     * @param loopId the loop to open.
     * @param accentColorHex the row's accent colour as a `#RRGGBB`
     * hex string — the colour the row was painted with. It travels
     * with the navigation so the detail screen paints itself in the
     * very colour the user tapped (iOS `LoopsTabView.swift:114-118`).
     */
    data class LoopClicked(
        val loopId: String,
        val accentColorHex: String,
    ) : LoopsEvent

    data class FilterChanged(val filter: LoopsFilter) : LoopsEvent
    data object SearchToggled : LoopsEvent
    data class SearchQueryChanged(val query: String) : LoopsEvent
    data object SearchSubmitted : LoopsEvent
    data object SearchCancelled : LoopsEvent
    data object LoadMore : LoopsEvent
    data object ScrollReachedEnd : LoopsEvent
}