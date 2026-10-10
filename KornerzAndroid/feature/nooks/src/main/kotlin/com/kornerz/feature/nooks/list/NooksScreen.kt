package com.kornerz.feature.nooks.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.feature.nooks.list.NooksNavEvent as ListNooksNavEvent

/**
 * Entry Composable for the Nooks tab list (architecture §2.1).
 * Collects the VM's state and forwards [NooksEvent]s back into the VM.
 * Pull-to-refresh wired via [PullToRefreshBox] (6.10).
 *
 * Navigation events are delivered to the host via [onNavEvent] so the
 * shell route graph (`AppShell`) can switch tabs / push details
 * without `NooksScreen` ever referencing `NavController` directly
 * (architecture §2.4 / §7.10).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NooksScreen(
    modifier: Modifier = Modifier,
    viewModel: NooksViewModel = hiltViewModel(),
    onNavEvent: (ListNooksNavEvent) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event -> onNavEvent(event) }
    }

    val isRefreshing = state.live is AsyncValue.Loading && state.upcoming is AsyncValue.Loading

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.onEvent(NooksEvent.Refresh) },
            ) {
                NooksContent(state = state, onEvent = viewModel::onEvent)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun NooksScreenPreview() {
    KornerzTheme(darkTheme = false) {
        NooksContent(
            state = NooksState(
                live = AsyncValue.Success(LiveSectionState(items = emptyList())),
                upcoming = AsyncValue.Success(UpcomingSectionState(items = emptyList())),
            ),
            onEvent = {},
        )
    }
}
