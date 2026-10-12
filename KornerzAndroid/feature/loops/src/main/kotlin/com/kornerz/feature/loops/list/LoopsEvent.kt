package com.kornerz.feature.loops.list

import com.kornerz.core.model.LoopsFilter

sealed interface LoopsEvent {
    data object Refresh : LoopsEvent
    data object Retry : LoopsEvent
    data class LoopClicked(val loopId: String) : LoopsEvent
    data class FilterChanged(val filter: LoopsFilter) : LoopsEvent
    data object SearchToggled : LoopsEvent
    data class SearchQueryChanged(val query: String) : LoopsEvent
    data object SearchSubmitted : LoopsEvent
    data object SearchCancelled : LoopsEvent
    data object LoadMore : LoopsEvent
    data object ScrollReachedEnd : LoopsEvent
}
