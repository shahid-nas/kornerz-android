package com.kornerz.feature.loops.list

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

/**
 * Entry Composable for the Loops tab list (architecture §2.1).
 * Collects the VM's state and forwards [LoopsEvent]s back into the VM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopsScreen(
    modifier: Modifier = Modifier,
    viewModel: LoopsViewModel = hiltViewModel(),
    onNavEvent: (LoopsNavEvent) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event -> onNavEvent(event) }
    }

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
                isRefreshing = state.feed is com.kornerz.core.model.AsyncValue.Loading,
                onRefresh = { viewModel.onEvent(LoopsEvent.Refresh) },
            ) {
                LoopsContent(state = state, onEvent = viewModel::onEvent)
            }
        }
    }
}
