package com.kornerz.feature.kornerz

/**
 * UiEvent for the Kornerz tab list. Mirrors architecture §2.1 (sealed
 * interface, `data object`/`data class` subtypes, no business logic in
 * composables).
 */
sealed interface KornerzEvent {
    data object Refresh : KornerzEvent
    data object Retry : KornerzEvent
    data class RowClicked(val kornerId: String) : KornerzEvent
}
