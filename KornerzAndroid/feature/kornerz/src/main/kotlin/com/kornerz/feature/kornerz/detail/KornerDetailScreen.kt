package com.kornerz.feature.kornerz.detail

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

/**
 * Entry Composable for the Korner detail screen (architecture §2.1,
 * §2.4). Pulls `kornerId` from the `SavedStateHandle` (the
 * Navigation-Compose nav-arg wired in `AppShell` task 5.4) so a single
 * `hiltViewModel()` instance is shared per `kornerId` deep-link.
 *
 * Navigation events are surfaced via [onNavEvent] so the shell route
 * graph (`AppShell`) can resolve them — the screen never references
 * `NavController` directly (architecture §10).
 */
@Composable
fun KornerDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: KornerDetailViewModel = hiltViewModel(),
    onNavEvent: (KornerDetailNavEvent) -> Unit = {},
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
                is AsyncValue.Loading -> KornerDetailHeaderShimmer()
                is AsyncValue.Error -> KornerzErrorState(
                    message = "We couldn't load this Korner",
                    onRetry = { viewModel.onEvent(KornerDetailEvent.Retry) },
                    modifier = Modifier.padding(Spacing.space8),
                )
                is AsyncValue.Success -> KornerDetailContent(detail = value.data, onEvent = viewModel::onEvent)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun KornerDetailScreenPreview() {
    KornerzTheme(darkTheme = false) {
        KornerDetailContent(
            detail = com.kornerz.core.model.KornerDetail(
                korner = com.kornerz.core.model.Korner(
                    id = "c1",
                    name = "Design Korner",
                    themeColor = "#E27D5A",
                ),
                nooks = emptyList(),
                members = emptyList(),
            ),
            onEvent = {},
        )
    }
}
