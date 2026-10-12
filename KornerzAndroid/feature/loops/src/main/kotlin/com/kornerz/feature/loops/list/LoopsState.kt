package com.kornerz.feature.loops.list

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.LoopsFilter
import com.kornerz.core.ui.components.header.TabHeaderData

/** One page of the Loops tab feed. */
data class PagingState(
    val items: List<com.kornerz.feature.loops.components.LoopCardData> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
)

/**
 * State shape for the Loops tab (architecture §2.2).
 *
 * [header] mirrors the iOS `HomeHeaderView` row that iOS embeds on
 * every tab — shared via `:core:ui` so Android tabs can match
 * (architecture §2.5).
 */
data class LoopsState(
    val header: AsyncValue<TabHeaderData> = AsyncValue.Loading,
    val feed: AsyncValue<PagingState> = AsyncValue.Loading,
    val selectedFilter: LoopsFilter = LoopsFilter.ALL,
    val searchQuery: String = "",
    val isSearchMode: Boolean = false,
    val hasMoreData: Boolean = true,
    val accentColors: Map<String, androidx.compose.ui.graphics.Color> = emptyMap(),
)