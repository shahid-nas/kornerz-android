package com.kornerz.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * Entry Composable for the Home tab. Collects the VM's state and
 * forwards [HomeEvent]s back into the VM (architecture §2.1).
 *
 * Pull-to-refresh is wired through the Material 3 `PullToRefreshBox`
 * which mirrors the iOS `.refreshable` modifier (iOS
 * `NooksTabView.swift` line 60). The previous implementation placed
 * a floating `IconButton` at `Alignment.TopEnd` that overlapped the
 * shared `HomeHeaderView` (avatar + points); the floating button was
 * the P2 placeholder, and P3 ships the iOS-matching pull gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToKornerDetail: (String, String) -> Unit = { _, _ -> },
    onNavigateToLoopDetail: (String, String) -> Unit = { _, _ -> },
    onNavigateToProfile: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        HomeNavResolver.bind(
            snackbar = snackbarHostState,
            events = viewModel.navEvents,
            onNavigateToKornerDetail = onNavigateToKornerDetail,
            onNavigateToLoopDetail = onNavigateToLoopDetail,
            onNavigateToProfile = onNavigateToProfile,
        )
    }
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.onEvent(HomeEvent.Refresh)
            isRefreshing = false
        }
    }
    val anyLoading = state.loopOfDay is com.kornerz.core.model.AsyncValue.Loading ||
        state.upcomingNooks is com.kornerz.core.model.AsyncValue.Loading ||
        state.yourKornerz is com.kornerz.core.model.AsyncValue.Loading ||
        state.recentLoops is com.kornerz.core.model.AsyncValue.Loading

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing || anyLoading,
            onRefresh = { isRefreshing = true },
            modifier = Modifier.fillMaxSize(),
        ) {
            HomeContent(
                state = state,
                onEvent = viewModel::onEvent,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    KornerzTheme(darkTheme = false) {
        HomeScreen()
    }
}
