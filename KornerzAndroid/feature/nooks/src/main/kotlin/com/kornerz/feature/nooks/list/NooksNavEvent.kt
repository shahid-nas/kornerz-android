package com.kornerz.feature.nooks.list

/**
 * Side-channel navigation events emitted by [NooksViewModel] over
 * `Channel<NooksNavEvent>` (architecture §2.4). The shell collects
 * these and routes via `NavController.navigate(...)` into the
 * per-tab detail destination.
 */
sealed interface NooksNavEvent {
    data object NavigateToProfile : NooksNavEvent
    data class NavigateToNookDetail(val nookId: String) : NooksNavEvent
    data class NavigateToKornerDetail(val kornerId: String) : NooksNavEvent
    data class JoinVideoCall(
        val sessionId: String,
        val communityName: String,
        val communityId: String,
    ) : NooksNavEvent
}