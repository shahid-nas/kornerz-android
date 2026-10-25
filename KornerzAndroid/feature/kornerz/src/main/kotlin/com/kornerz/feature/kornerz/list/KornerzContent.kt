package com.kornerz.feature.kornerz.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Korner
import com.kornerz.core.ui.components.HomeShimmerPlaceholder
import com.kornerz.core.ui.components.KornerzCarousel
import com.kornerz.core.ui.components.KornerzErrorState
import com.kornerz.core.ui.components.KornerzHeaderView
import com.kornerz.core.ui.components.KornerzSquareTileDefaults
import com.kornerz.core.ui.components.header.TabHeaderContent
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.kornerz.KornerzEvent
import com.kornerz.feature.kornerz.KornerzState

/**
 * Stateless renderer for the Kornerz tab. Mirrors iOS `KornerzTabView`
 * (`ScrollView { VStack { HomeHeader; 3× KornerzCarouselView } }`):
 *   - Shared avatar + points header (1:1 with iOS `HomeHeaderView`)
 *   - Tab title row (`KornerzHeaderView` mirrors iOS `TabHeaderView`)
 *   - Three horizontal carousels: Your Kornerz, Recommended for you,
 *     New this week.
 *
 * Each section hides when the underlying `AsyncValue` resolves to
 * `Success(empty)` per spec. Pull-to-refresh + the surrounding scaffold
 * live in `KornerzScreen`; this composable is a pure renderer.
 */
@Composable
fun KornerzContent(
    state: KornerzState,
    onEvent: (KornerzEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.space4),
    ) {
        item(key = "header") {
            TabHeaderContent(
                state = state.header,
                onAvatarTap = { onEvent(KornerzEvent.AvatarTapped) },
            )
        }
        item(key = "kornerzTitle") {
            KornerzHeaderView(title = "Kornerz")
        }
        section("your", "Your Kornerz", state.yourKornerz, onEvent)
        section("recommended", "Recommended for you", state.recommended, onEvent)
        section("newThisWeek", "New this week", state.newThisWeek, onEvent)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    key: String,
    title: String,
    value: AsyncValue<List<Korner>>,
    onEvent: (KornerzEvent) -> Unit,
) {
    when (value) {
        is AsyncValue.Loading -> item(key = "$key-loading") {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.space4)) {
                SectionTitle(title)
                ShimmerRow()
            }
        }
        is AsyncValue.Error -> item(key = "$key-error") {
            Column(modifier = Modifier.fillMaxWidth().padding(Spacing.space8)) {
                SectionTitle(title)
                KornerzErrorState(
                    message = "Couldn't load $title",
                    onRetry = { onEvent(KornerzEvent.Retry) },
                )
            }
        }
        is AsyncValue.Success -> if (value.data.isNotEmpty()) {
            item(key = "$key-list") {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.space4)) {
                    SectionTitle(title)
                    KornerzCarousel(
                        items = value.data,
                        onItemClick = { korner -> onEvent(KornerzEvent.RowClicked(korner.id)) },
                    )
                }
            }
        }
        // Success(empty) → section is hidden per spec.
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
    )
}

@Composable
private fun ShimmerRow() {
    HomeShimmerPlaceholder(
        modifier = Modifier.padding(horizontal = Spacing.space8),
    ) {
        Spacer(modifier = Modifier.height(KornerzSquareTileDefaults.TileSize))
    }
}

@Preview(showBackground = true)
@Composable
private fun KornerzContentPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzContent(
            state = KornerzState(
                yourKornerz = AsyncValue.Success(
                    listOf(Korner(id = "y1", name = "Following", themeColor = "#E27D5A")),
                ),
                recommended = AsyncValue.Success(
                    listOf(
                        Korner(id = "r1", name = "Design", themeColor = "#E27D5A"),
                        Korner(id = "r2", name = "Engineering", themeColor = "#4A90E2"),
                    ),
                ),
                newThisWeek = AsyncValue.Loading,
            ),
            onEvent = {},
        )
    }
}