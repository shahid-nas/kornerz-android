package com.kornerz.feature.home

sealed interface HomeEvent {
    data object Refresh : HomeEvent
    data object LoopOfDayRetry : HomeEvent
    data object UpcomingNooksRetry : HomeEvent
    data object YourKornerzRetry : HomeEvent
    data object RecentLoopsRetry : HomeEvent
    data object AvatarTapped : HomeEvent
    data class LoopOfDayClicked(val loopId: String) : HomeEvent
    data class NookClicked(val nookId: String) : HomeEvent
    data class KornerClicked(val kornerId: String) : HomeEvent
}
