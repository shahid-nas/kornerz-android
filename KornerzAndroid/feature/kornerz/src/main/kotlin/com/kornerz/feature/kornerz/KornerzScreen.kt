package com.kornerz.feature.kornerz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.feature.kornerz.list.KornerzContent

/**
 * Entry Composable for the Kornerz tab list (architecture §2.1).
 * Collects the VM's state and forwards [KornerzEvent]s back into the
 * VM. Pull-to-refresh wired via [PullToRefreshBox] (6.12).
 *
 * Navigation events are delivered to the host via [onNavEvent] so the
 * shell route graph (`AppShell`) can switch tabs / push details
 * without `KornerzScreen` ever referencing `NavController` directly
 * (architecture §2.4 / §7.10).
 *
 * **Layout note:** the previous implementation wrapped the content
 * in a Material 3 `Scaffold` for the snackbar slot, but `Scaffold`
 * defaults to `contentWindowInsets` that include the bottom system
 * nav bar. The shell's `NavigationBar` already consumes that inset
 * (via `NavigationBarDefaults.windowInsets`), so the `Scaffold` was
 * double-counting it and producing a visible gap between the
 * scrollable list and the bottom tab bar (the same inset was also
 * double-counted at the top against the status bar). This file now
 * uses a plain `Box`, mirroring `NooksScreen` — the snackbar was
 * never shown here anyway, the shell owns the shared
 * `SnackbarHostState` (via `comingSoonSnackbar`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KornerzScreen(
    modifier: Modifier = Modifier,
    viewModel: KornerzViewModel = hiltViewModel(),
    onNavEvent: (KornerzNavEvent) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event -> onNavEvent(event) }
    }

    val isRefreshing = state.yourKornerz is AsyncValue.Loading &&
        state.recommended is AsyncValue.Loading &&
        state.newThisWeek is AsyncValue.Loading

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.onEvent(KornerzEvent.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            KornerzContent(state = state, onEvent = viewModel::onEvent)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun KornerzScreenPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzContent(
            state = KornerzState(
                recommended = AsyncValue.Success(emptyList()),
            ),
            onEvent = {},
        )
    }
}