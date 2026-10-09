package com.kornerz.feature.kornerz

/**
 * Side-channel navigation events emitted by [KornerzViewModel] over
 * `Channel<KornerzNavEvent>` (architecture §2.4). The shell collects
 * these and routes via `NavController.navigate(...)`.
 */
sealed interface KornerzNavEvent {
    data class NavigateToKornerDetail(val kornerId: String) : KornerzNavEvent
}
