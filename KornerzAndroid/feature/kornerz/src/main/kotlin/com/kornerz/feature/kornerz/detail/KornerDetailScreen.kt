package com.kornerz.feature.kornerz.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
 *
 * Pull-to-refresh mirrors iOS `.refreshable { viewModel.fetchData(forcedRefresh: true) }`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KornerDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: KornerDetailViewModel = hiltViewModel(),
    onNavEvent: (KornerDetailNavEvent) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event -> onNavEvent(event) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.onEvent(KornerDetailEvent.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val value = state.detail) {
                is AsyncValue.Loading -> KornerDetailShimmer()
                is AsyncValue.Error -> KornerzErrorState(
                    message = "We couldn't load this Korner",
                    onRetry = { viewModel.onEvent(KornerDetailEvent.Retry) },
                    modifier = Modifier.padding(Spacing.space8),
                )
                is AsyncValue.Success -> KornerDetailContent(
                    state = state,
                    onEvent = viewModel::onEvent,
                )
            }
        }
    }

    val detail = (state.detail as? AsyncValue.Success)?.data
    if (state.isAboutSheetVisible && detail != null) {
        KornerAboutSheet(
            korner = detail.korner,
            isFollowing = detail.korner.isJoined,
            onFollowClick = { viewModel.onEvent(KornerDetailEvent.FollowClicked) },
            onDismiss = { viewModel.onEvent(KornerDetailEvent.AboutDismissed) },
        )
    }
}

/**
 * The loading placeholder. Mirrors iOS `KornerDetailView.shimmerView`,
 * which redacts the header, the "Recently in" header and three
 * article rows — the layout the screen settles into, so nothing jumps
 * when the data lands.
 */
@Composable
private fun KornerDetailShimmer(modifier: Modifier = Modifier) {
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier.fillMaxSize()) {
        KornerDetailHeaderShimmer()
        Spacer(modifier = Modifier.height(Spacing.space8))
        Box(
            modifier = Modifier
                .padding(horizontal = Spacing.space8)
                .fillMaxWidth(0.5f)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(placeholder),
        )
        Spacer(modifier = Modifier.height(Spacing.space4))
        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space8, vertical = Spacing.space2),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(placeholder),
                )
                Spacer(modifier = Modifier.width(Spacing.space6))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholder),
                    )
                    Spacer(modifier = Modifier.height(Spacing.space2))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholder),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun KornerDetailContentPreview() {
    KornerzTheme(darkTheme = false) {
        KornerDetailContent(
            state = KornerDetailState(),
            onEvent = {},
        )
    }
}
