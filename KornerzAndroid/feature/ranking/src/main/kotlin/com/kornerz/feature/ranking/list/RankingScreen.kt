package com.kornerz.feature.ranking.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Rank
import com.kornerz.core.model.UserRanking
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * The Klouders tab's host screen (inventory 2.6). Mirrors iOS
 * `RankingViewController` (`kornerz/Kornerz/HomeVC/Ranking/
 * RankingViewController.swift`).
 *
 * iOS's `configureNavigation` (`:27-32`) sets the navigation title to
 * the `klouders` localisation key and shows the navigation bar; on
 * Android the title is rendered by the content, because the shell's
 * bottom-bar tabs carry no top app bar.
 *
 * The screen loads the leaderboard on init — the ViewModel's `init`
 * block issues the query, mirroring iOS's `.onAppear { viewModel.fetchRankings() }`.
 *
 * Navigation events are delivered to the host via [onNavEvent] so the
 * shell route graph (`AppShell`) can push the profile without
 * `RankingScreen` ever referencing `NavController` directly
 * (architecture §2.4).
 */
@Composable
fun RankingScreen(
    modifier: Modifier = Modifier,
    viewModel: RankingViewModel = hiltViewModel(),
    onNavEvent: (RankingNavEvent) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event -> onNavEvent(event) }
    }

    RankingContent(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun RankingScreenPreview() {
    KornerzTheme(darkTheme = false) {
        RankingScreenPreviewContent()
    }
}

@Composable
private fun RankingScreenPreviewContent() {
    RankingContent(
        state = RankingState(
            rankings = AsyncValue.Success(
                listOf(
                    UserRanking(userId = "u1", userName = "Ana", points = 320, medal = Rank.GOLD),
                    UserRanking(userId = "u2", userName = "Bo", points = 210, medal = Rank.SILVER),
                    UserRanking(userId = "u3", userName = "Cy", points = 90, medal = Rank.BRONZE),
                    UserRanking(userId = "u4", userName = "Dee", points = 12, medal = Rank.NONE),
                ),
            ),
        ),
        onEvent = {},
    )
}
