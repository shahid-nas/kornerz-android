package com.kornerz.feature.home

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.flow.Flow

/**
 * Maps every [HomeNavEvent] to a `SnackbarHostState.showSnackbar("Coming
 * in Phase N")`. Single place updated when destinations ship.
 */
object HomeNavResolver {

    suspend fun bind(snackbar: SnackbarHostState, events: Flow<HomeNavEvent>) {
        events.collect { event ->
            val message = when (event) {
                HomeNavEvent.NavigateToProfile -> "Profile coming in Phase 6"
                is HomeNavEvent.NavigateToLoopDetail -> "Loop detail coming in Phase 5"
                is HomeNavEvent.NavigateToEventDetail -> "Event detail coming in Phase 4"
                is HomeNavEvent.NavigateToKornerDetail -> "Korner detail coming in Phase 3"
            }
            snackbar.showSnackbar(message)
        }
    }
}
