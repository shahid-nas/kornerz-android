package com.kornerz.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * Entry Composable for the Home tab. Collects the VM's state and
 * forwards [HomeEvent]s back into the VM (architecture §2.1).
 *
 * Pull-to-refresh in P2 is exposed via the trailing refresh icon to
 * keep the surface area minimal; the P3 polish pass adds the iOS
 * `.refreshable` equivalent.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToKornerDetail: (String) -> Unit = {},
    onNavigateToLoopDetail: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        HomeNavResolver.bind(
            snackbar = snackbarHostState,
            events = viewModel.navEvents,
            onNavigateToKornerDetail = onNavigateToKornerDetail,
            onNavigateToLoopDetail = onNavigateToLoopDetail,
        )
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
            HomeContent(state = state, onEvent = viewModel::onEvent, modifier = Modifier)
            IconButton(
                onClick = { viewModel.onEvent(HomeEvent.Refresh) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(40.dp),
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    KornerzTheme(darkTheme = false) {
        HomeContent(
            state = HomeState(
                header = com.kornerz.core.model.AsyncValue.Success(
                    com.kornerz.core.ui.components.header.TabHeaderData(
                        user = com.kornerz.core.model.User(
                            id = "u1",
                            email = "u@example.com",
                            displayName = "Ada Lovelace",
                            nickname = "ada",
                        ),
                        points = 0,
                    ),
                ),
                loopOfDay = com.kornerz.core.model.AsyncValue.Success(
                    com.kornerz.core.model.Loop(
                        id = "l1",
                        title = "How engines work",
                        createdAt = kotlinx.datetime.Instant.parse("2026-10-01T00:00:00Z"),
                    ),
                ),
                upcomingNooks = com.kornerz.core.model.AsyncValue.Success(emptyList()),
                yourKornerz = com.kornerz.core.model.AsyncValue.Success(emptyList()),
                recentLoops = com.kornerz.core.model.AsyncValue.Success(emptyList()),
            ),
            onEvent = {},
        )
    }
}
