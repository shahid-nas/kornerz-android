package com.kornerz.feature.ranking.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.ui.components.KornerzLoadingView
import com.kornerz.core.ui.components.KornerzNoDataView
import com.kornerz.core.ui.components.KornerzRetryView
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.ranking.list.components.RankingHeaderRow
import com.kornerz.feature.ranking.list.components.RankingRow

/**
 * Stateless renderer for the Klouders leaderboard. Mirrors iOS
 * `RankingScreen.swift` (`kornerz/Kornerz/HomeVC/Ranking/RankingScreen.swift`):
 *
 *  - a plain list — no row separators;
 *  - a sticky header row reading `#` / `Name` / `Points`;
 *  - one row per leaderboard entry.
 *
 * iOS's `List { Section { … } header: { headerRow } }` puts the header
 * in a `Section`, which pins it to the top of the list while it
 * scrolls — Android reproduces that with a `stickyHeader` item in the
 * same `LazyColumn`.
 *
 * Three render states, per the `ranking-leaderboard` spec: a loading
 * state, an error state with a working retry, and the loaded list. The
 * empty case renders the shared no-data view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RankingContent(
    state: RankingState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val rankings = state.rankings) {
        AsyncValue.Loading -> KornerzLoadingView(modifier = modifier.fillMaxSize())

        is AsyncValue.Error -> KornerzRetryView(
            message = rankings.throwable.message ?: "Failed to load leaderboard",
            onRetry = onRetry,
            modifier = modifier.fillMaxSize(),
        )

        is AsyncValue.Success -> if (rankings.data.isEmpty()) {
            KornerzNoDataView(
                title = "No rankings yet",
                modifier = modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Spacing.space8,
                    vertical = Spacing.space4,
                ),
            ) {
                stickyHeader(key = "ranking-header") {
                    RankingHeaderRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = Spacing.space4),
                    )
                }
                itemsIndexed(
                    items = rankings.data,
                    key = { _, row -> row.userId },
                ) { index, row ->
                    RankingRow(
                        ranking = row,
                        position = index + 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.space8),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RankingContentEmptyPreview() {
    KornerzTheme(darkTheme = false) {
        RankingContent(
            state = RankingState(rankings = AsyncValue.Success(emptyList())),
            onRetry = {},
        )
    }
}
