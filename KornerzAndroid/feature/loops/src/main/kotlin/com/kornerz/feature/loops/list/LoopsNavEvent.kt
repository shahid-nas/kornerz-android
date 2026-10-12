package com.kornerz.feature.loops.list

sealed interface LoopsNavEvent {
    data class NavigateToLoopDetail(val loopId: String) : LoopsNavEvent
}
