package com.kornerz.feature.nooks.list

/**
 * UiEvent for the Nooks tab list (architecture §2.1).
 */
sealed interface NooksEvent {
    data object Refresh : NooksEvent
    data object Retry : NooksEvent
    data object AvatarTapped : NooksEvent
    data object LoadMoreLive : NooksEvent
    data object LoadMoreUpcoming : NooksEvent
    data class SelectType(val type: NooksType) : NooksEvent
    data class LiveCardClicked(val kornerId: String) : NooksEvent
    data class CardClicked(val nookId: String) : NooksEvent
    data class JoinVideoCall(
        val sessionId: String,
        val communityName: String,
        val communityId: String,
    ) : NooksEvent
}