package com.kornerz.feature.home

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.flow.Flow

/**
 * Maps every [HomeNavEvent] to either a destination switch (when the
 * destination exists) or a `SnackbarHostState.showSnackbar("Coming in
 * Phase N")` placeholder. Phase 3 wires the Korner detail route to a
 * real destination; loop/event/profile remain placeholders until P5 /
 * P4 / P6 ship.
 */
object HomeNavResolver {

    suspend fun bind(
        snackbar: SnackbarHostState,
        events: Flow<HomeNavEvent>,
        onNavigateToKornerDetail: (String) -> Unit,
    ) {
        events.collect { event ->
            when (event) {
                HomeNavEvent.NavigateToProfile -> snackbar.showSnackbar("Profile coming in Phase 6")
                is HomeNavEvent.NavigateToLoopDetail -> snackbar.showSnackbar("Loop detail coming in Phase 5")
                is HomeNavEvent.NavigateToEventDetail -> snackbar.showSnackbar("Event detail coming in Phase 4")
                is HomeNavEvent.NavigateToKornerDetail -> onNavigateToKornerDetail(event.kornerId)
            }
        }
    }
}
