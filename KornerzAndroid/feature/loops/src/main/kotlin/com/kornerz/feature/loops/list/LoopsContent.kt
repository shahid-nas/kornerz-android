package com.kornerz.feature.loops.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.LoopsFilter
import com.kornerz.core.network.loops.LoopsRequireSignInException
import com.kornerz.core.ui.components.HomeShimmerPlaceholder
import com.kornerz.core.ui.components.KornerzEmptyState
import com.kornerz.core.ui.components.KornerzErrorState
import com.kornerz.core.ui.components.KornerzLoadingMoreView
import com.kornerz.core.ui.components.KornerzSearchField
import com.kornerz.core.ui.components.header.TabHeaderContent
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.loops.components.LoopCardData
import com.kornerz.feature.loops.components.LoopItemView
import com.kornerz.core.ui.theme.KornerzCardPalette
import com.kornerz.feature.loops.list.LoopsEvent
import com.kornerz.feature.loops.list.LoopsState
import com.kornerz.feature.loops.list.PagingState
import kotlinx.datetime.Instant

/**
 * Stateless renderer for the Loops tab feed. Renders the header, the
 * filter / search section, and the paginated loop list. Switches
 * between shimmer / error / empty / content based on [LoopsState.feed].
 */
@Composable
fun LoopsContent(
    state: LoopsState,
    onEvent: (LoopsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TabHeaderContent(
            state = state.header,
            onAvatarTap = { onEvent(LoopsEvent.AvatarTapped) },
        )
        LoopsFilterSection(
            state = state,
            onEvent = onEvent,
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        when (val feed = state.feed) {
            is AsyncValue.Loading -> ShimmerList()
            is AsyncValue.Error -> {
                val message = when (feed.throwable) {
                    is LoopsRequireSignInException -> "Please sign in to view your loops"
                    else -> "Loops failed to load"
                }
                KornerzErrorState(
                    message = message,
                    onRetry = { onEvent(LoopsEvent.Retry) },
                )
            }
            is AsyncValue.Success -> SuccessList(
                state = state,
                paging = feed.data,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
fun LoopsFilterSection(
    state: LoopsState,
    onEvent: (LoopsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isSearchMode) {
        KornerzSearchField(
            query = state.searchQuery,
            onQueryChange = { onEvent(LoopsEvent.SearchQueryChanged(it)) },
            onSubmit = { onEvent(LoopsEvent.SearchSubmitted) },
            onCancel = { onEvent(LoopsEvent.SearchCancelled) },
            modifier = modifier,
        )
    } else {
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            modifier = modifier,
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(LoopsFilter.entries) { _, filter ->
                        FilterChip(
                            label = filter.displayName,
                            selected = state.selectedFilter == filter,
                            onClick = { onEvent(LoopsEvent.FilterChanged(filter)) },
                        )
                    }
                }
                Spacer(Modifier.padding(start = 8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onEvent(LoopsEvent.SearchToggled) }
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ShimmerList() {
    HomeShimmerPlaceholder {
        Column(
            modifier = Modifier.padding(Spacing.space8),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun SuccessList(
    state: LoopsState,
    paging: PagingState,
    onEvent: (LoopsEvent) -> Unit,
) {
    val listState = rememberLazyListState()
    val reachedEnd by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 4
        }
    }
    LaunchedEffect(reachedEnd, paging.hasMore, paging.isLoadingMore) {
        if (reachedEnd && paging.hasMore && !paging.isLoadingMore) {
            onEvent(LoopsEvent.LoadMore)
        }
    }
    if (paging.items.isEmpty()) {
        KornerzEmptyState(title = "No loops available", subtitle = "Pull to refresh")
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.space8, vertical = Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(paging.items) { index, item ->
                // The row's accent colour travels with the navigation:
                // the detail screen paints itself in the very colour
                // the user tapped (iOS `LoopsTabView.swift:114-118`).
                val accentHex = KornerzCardPalette.hexForIndex(index)
                LoopItemView(
                    data = item,
                    color = KornerzCardPalette.color(index),
                    onClick = { id -> onEvent(LoopsEvent.LoopClicked(id, accentHex)) },
                )
            }
            if (paging.isLoadingMore) {
                item {
                    KornerzLoadingMoreView(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Preview(showBackground = true, locale = "en")
@Composable
private fun LoopsContentLightPreview() {
    KornerzTheme(darkTheme = false) {
        LoopsContent(
            state = LoopsState(
                feed = AsyncValue.Success(
                    PagingState(
                        items = listOf(
                            LoopCardData(
                                id = "l1",
                                title = "How engines work",
                                description = null,
                                descriptionHtml = null,
                                communityId = "c1",
                                communityName = "Engineering",
                                publisherId = null,
                                publisherNickname = null,
                                publisherThumbnailUrl = null,
                                theme = null,
                                thumbnailUrl = null,
                                imageUrl = null,
                                videoUrl = "",
                                isVideo = false,
                                viewCounts = 100,
                                commentsCount = 5,
                                likesCount = 12,
                                creationDate = Instant.parse("2025-09-01T00:00:00Z"),
                            ),
                        ),
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
