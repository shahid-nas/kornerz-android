package com.kornerz.feature.loops.list

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

/**
 * Entry Composable for the Loops tab list (architecture §2.1).
 * Collects the VM's state and forwards [LoopsEvent]s back into the VM.
 *
 * Navigation events are delivered to the host via [onNavEvent] so the
 * shell route graph (`AppShell`) can switch tabs / push details
 * without `LoopsScreen` ever referencing `NavController` directly
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
fun LoopsScreen(
    modifier: Modifier = Modifier,
    viewModel: LoopsViewModel = hiltViewModel(),
    onNavEvent: (LoopsNavEvent) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event -> onNavEvent(event) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.feed is AsyncValue.Loading,
            onRefresh = { viewModel.onEvent(LoopsEvent.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LoopsContent(state = state, onEvent = viewModel::onEvent)
        }
    }
}