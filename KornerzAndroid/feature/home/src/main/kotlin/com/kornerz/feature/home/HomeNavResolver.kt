package com.kornerz.feature.home

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.flow.Flow

/**
 * Maps every [HomeNavEvent] to either a destination switch (when the
 * destination exists) or a `SnackbarHostState.showSnackbar("Coming in
 * Phase N")` placeholder.
 *
 * P6 wires `NavigateToProfile` to the shell's `ProfileRoute.View`
 * destination (mirrors iOS `HomeTabCoordinator.navigateToUserProfile`,
 * which pushes `UserProfileVC` with `hidesBottomBarWhenPushed = true`).
 */
object HomeNavResolver {

    suspend fun bind(
        snackbar: SnackbarHostState,
        events: Flow<HomeNavEvent>,
        onNavigateToKornerDetail: (String) -> Unit,
        onNavigateToLoopDetail: (String) -> Unit = {},
        onNavigateToProfile: () -> Unit = {},
    ) {
        events.collect { event ->
            when (event) {
                HomeNavEvent.NavigateToProfile -> onNavigateToProfile()
                is HomeNavEvent.NavigateToLoopDetail -> onNavigateToLoopDetail(event.loopId)
                is HomeNavEvent.NavigateToEventDetail -> snackbar.showSnackbar("Event detail coming in Phase 4")
                is HomeNavEvent.NavigateToKornerDetail -> onNavigateToKornerDetail(event.kornerId)
            }
        }
    }
}
