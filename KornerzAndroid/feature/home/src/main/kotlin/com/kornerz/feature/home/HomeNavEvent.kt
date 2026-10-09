package com.kornerz.feature.home

/**
 * Navigation intents emitted by the Home graph (architecture §2.4).
 * Each event is resolved by `HomeNavResolver`, which currently shows a
 * "Coming in Phase N" snackbar so destinations can land in P3–P10
 * without crashing the P2 Home tab.
 */
sealed interface HomeNavEvent {
    data object NavigateToProfile : HomeNavEvent
    data class NavigateToLoopDetail(val loopId: String) : HomeNavEvent
    data class NavigateToEventDetail(val nookId: String) : HomeNavEvent
    data class NavigateToKornerDetail(val kornerId: String) : HomeNavEvent
}
