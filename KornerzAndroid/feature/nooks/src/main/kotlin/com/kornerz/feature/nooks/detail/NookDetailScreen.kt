package com.kornerz.feature.nooks.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.ui.components.KornerzErrorState
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.nooks.detail.NookDetailNavEvent as DetailNooksNavEvent

/**
 * Entry Composable for the Nook detail screen (architecture §2.1,
 * §2.4). Pulls `nookId` from the `SavedStateHandle` so a single
 * `hiltViewModel()` instance is shared per `nookId` deep-link.
 *
 * Navigation events are surfaced via [onNavEvent] so the shell route
 * graph (`AppShell`) can resolve them — the screen never references
 * `NavController` directly (architecture §10).
 */
@Composable
fun NookDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: NookDetailViewModel = hiltViewModel(),
    onNavEvent: (DetailNooksNavEvent) -> Unit = {},
    onBack: () -> Unit = {},
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
            when (val value = state.detail) {
                is AsyncValue.Loading -> NookDetailContentShimmer()
                is AsyncValue.Error -> NookDetailError(
                    throwable = value.throwable,
                    onRetry = { viewModel.onEvent(NookDetailEvent.Retry) },
                    onBack = onBack,
                )
                is AsyncValue.Success -> NookDetailContent(
                    detail = value.data,
                    onEvent = viewModel::onEvent,
                )
            }
        }
    }
}

@Composable
private fun NookDetailError(
    throwable: Throwable,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isNotFound = throwable.message?.contains("not found", ignoreCase = true) == true
    if (isNotFound) {
        NookDetailNotFound(modifier = modifier.padding(Spacing.space8))
        Box(modifier = modifier.fillMaxSize().padding(Spacing.space8)) {
            androidx.compose.material3.TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxSize(),
            ) { androidx.compose.material3.Text("Back") }
        }
    } else {
        KornerzErrorState(
            message = throwable.message ?: "We couldn't load this nook",
            onRetry = onRetry,
            modifier = modifier.padding(Spacing.space8),
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun NookDetailScreenPreview() {
    KornerzTheme(darkTheme = false) {
        NookDetailContent(
            detail = com.kornerz.core.model.NookDetail(
                nook = com.kornerz.core.model.Nook(
                    id = "n1",
                    communityId = "c1",
                    title = "Friday Fireside",
                    startDate = kotlinx.datetime.Instant.parse("2026-10-10T19:00:00Z"),
                    isLive = true,
                ),
                korner = com.kornerz.core.model.Korner(
                    id = "c1",
                    name = "Design Korner",
                    themeColor = "#E27D5A",
                ),
            ),
            onEvent = {},
        )
    }
}
