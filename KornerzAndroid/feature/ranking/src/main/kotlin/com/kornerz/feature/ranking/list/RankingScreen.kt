package com.kornerz.feature.ranking.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Rank
import com.kornerz.core.model.UserRanking
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * The Klouders tab's host screen (inventory 2.6). Mirrors iOS
 * `RankingViewController` (`kornerz/Kornerz/HomeVC/Ranking/
 * RankingViewController.swift`).
 *
 * iOS's `configureNavigation` (`:27-32`) sets the navigation title to
 * the `klouders` localisation key and shows the navigation bar; on
 * Android the title is rendered by the screen itself, because the
 * shell's bottom-bar tabs carry no top app bar.
 *
 * The screen loads the leaderboard on init — the ViewModel's `init`
 * block issues the query, mirroring iOS's `.onAppear { viewModel.fetchRankings() }`.
 */
@Composable
fun RankingScreen(
    modifier: Modifier = Modifier,
    viewModel: RankingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
    ) {
        Text(
            text = SCREEN_TITLE,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        RankingContent(
            state = state,
            onRetry = viewModel::loadRankings,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

/** The English value of iOS's `klouders` localisation key. */
const val SCREEN_TITLE: String = "Klouders"

@Preview(showBackground = true)
@Composable
private fun RankingScreenPreview() {
    KornerzTheme(darkTheme = false) {
        RankingScreenPreviewContent()
    }
}

@Composable
private fun RankingScreenPreviewContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
    ) {
        Text(
            text = SCREEN_TITLE,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
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
            onRetry = {},
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}
