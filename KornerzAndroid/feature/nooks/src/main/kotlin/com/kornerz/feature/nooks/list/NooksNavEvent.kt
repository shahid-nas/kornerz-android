package com.kornerz.feature.nooks.list

/**
 * Side-channel navigation events emitted by [NooksViewModel] over
 * `Channel<NooksNavEvent>` (architecture §2.4). The shell collects
 * these and routes via `NavController.navigate(...)` into the
 * per-tab detail destination.
 */
sealed interface NooksNavEvent {
    data class NavigateToNookDetail(val nookId: String) : NooksNavEvent
    data class NavigateToKornerDetail(val kornerId: String) : NooksNavEvent
}
