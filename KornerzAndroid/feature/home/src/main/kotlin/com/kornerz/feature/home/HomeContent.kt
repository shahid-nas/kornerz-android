package com.kornerz.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.ui.components.header.TabHeaderContent
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.home.loop.LoopOfDayContent
import com.kornerz.feature.home.loop.LoopOfDayRetrySection
import com.kornerz.feature.home.loop.LoopOfDayShimmerSection
import com.kornerz.feature.home.recent_loops.RecentLoopsContent
import com.kornerz.feature.home.recent_loops.RecentLoopsRetrySection
import com.kornerz.feature.home.recent_loops.RecentLoopsShimmerSection
import com.kornerz.feature.home.upcoming_nooks.UpcomingNooksContent
import com.kornerz.feature.home.upcoming_nooks.UpcomingNooksRetrySection
import com.kornerz.feature.home.upcoming_nooks.UpcomingNooksShimmerSection
import com.kornerz.feature.home.your_kornerz.YourKornerzContent
import com.kornerz.feature.home.your_kornerz.YourKornerzRetrySection
import com.kornerz.feature.home.your_kornerz.YourKornerzShimmerSection

@Composable
fun HomeContent(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.space4),
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        item(key = "header") {
            TabHeaderContent(
                state = state.header,
                onAvatarTap = { onEvent(HomeEvent.AvatarTapped) },
            )
        }
        item(key = "loopOfDay") { LoopSection(state.loopOfDay, onEvent) }
        item(key = "upcomingNooks") { UpcomingNooksSection(state.upcomingNooks, onEvent) }
        item(key = "yourKornerz") { YourKornerzSection(state.yourKornerz, onEvent) }
        item(key = "recentLoops") { RecentLoopsSection(state.recentLoops, onEvent) }
    }
}

@Composable
private fun LoopSection(value: AsyncValue<com.kornerz.core.model.Loop>, onEvent: (HomeEvent) -> Unit) {
    when (value) {
        is AsyncValue.Success -> if (value.data.id?.isNotEmpty() == true) {
            LoopOfDayContent(
                loop = value.data,
                // The loop of the day is the first tile of its
                // section, so it wears the palette's first colour —
                // the same one the tile was painted with.
                onClick = { loopId, accentColorHex ->
                    onEvent(HomeEvent.LoopOfDayClicked(loopId, accentColorHex))
                },
            )
        }
        is AsyncValue.Error -> LoopOfDayRetrySection(onRetry = { onEvent(HomeEvent.LoopOfDayRetry) })
        AsyncValue.Loading -> LoopOfDayShimmerSection()
    }
}

@Composable
private fun UpcomingNooksSection(value: AsyncValue<List<com.kornerz.core.model.Nook>>, onEvent: (HomeEvent) -> Unit) {
    when (value) {
        is AsyncValue.Success -> if (value.data.isNotEmpty()) {
            UpcomingNooksContent(nooks = value.data, onClick = { onEvent(HomeEvent.NookClicked(it)) })
        }
        is AsyncValue.Error -> UpcomingNooksRetrySection(onRetry = { onEvent(HomeEvent.UpcomingNooksRetry) })
        AsyncValue.Loading -> UpcomingNooksShimmerSection()
    }
}

@Composable
private fun YourKornerzSection(value: AsyncValue<List<com.kornerz.core.model.Community>>, onEvent: (HomeEvent) -> Unit) {
    when (value) {
        is AsyncValue.Success -> if (value.data.isNotEmpty()) {
            YourKornerzContent(
                items = value.data,
                // The tile's accent hex travels with the tap, so the
                // Korner detail opens in the very colour the tile was
                // painted with.
                onItemClick = { kornerId, themeColorHex ->
                    onEvent(HomeEvent.KornerClicked(kornerId, themeColorHex))
                },
            )
        }
        is AsyncValue.Error -> YourKornerzRetrySection(onRetry = { onEvent(HomeEvent.YourKornerzRetry) })
        AsyncValue.Loading -> YourKornerzShimmerSection()
    }
}

@Composable
private fun RecentLoopsSection(value: AsyncValue<List<com.kornerz.core.model.Loop>>, onEvent: (HomeEvent) -> Unit) {
    when (value) {
        is AsyncValue.Success -> if (value.data.isNotEmpty()) {
            RecentLoopsContent(
                loops = value.data,
                onClick = { loopId, accentColorHex ->
                    onEvent(HomeEvent.LoopOfDayClicked(loopId, accentColorHex))
                },
            )
        }
        is AsyncValue.Error -> RecentLoopsRetrySection(onRetry = { onEvent(HomeEvent.RecentLoopsRetry) })
        AsyncValue.Loading -> RecentLoopsShimmerSection()
    }
}