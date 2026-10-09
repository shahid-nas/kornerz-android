package com.kornerz.feature.kornerz

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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KornerzScreen(
    modifier: Modifier = Modifier,
    viewModel: KornerzViewModel = hiltViewModel(),
    onNavEvent: (KornerzNavEvent) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event -> onNavEvent(event) }
    }

    val isRefreshing = state.yourKornerz is AsyncValue.Loading &&
        state.recommended is AsyncValue.Loading &&
        state.newThisWeek is AsyncValue.Loading

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
                onRefresh = { viewModel.onEvent(KornerzEvent.Refresh) },
            ) {
                KornerzContent(state = state, onEvent = viewModel::onEvent)
            }
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
