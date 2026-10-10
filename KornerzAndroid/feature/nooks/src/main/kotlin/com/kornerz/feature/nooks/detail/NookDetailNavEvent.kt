package com.kornerz.feature.nooks.detail

/**
 * Side-channel navigation events emitted by [NookDetailViewModel]
 * over `Channel<NookDetailNavEvent>` (architecture §2.4). The shell
 * collects these and routes via `NavController.navigate(...)`.
 */
sealed interface NookDetailNavEvent {
    data class NavigateToKornerDetail(val kornerId: String) : NookDetailNavEvent
}
