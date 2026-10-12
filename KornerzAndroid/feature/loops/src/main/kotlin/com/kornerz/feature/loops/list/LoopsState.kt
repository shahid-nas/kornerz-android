package com.kornerz.feature.loops.list

import com.kornerz.core.model.LoopsFilter

/** One page of the Loops tab feed. */
data class PagingState(
    val items: List<com.kornerz.feature.loops.components.LoopCardData> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
)

/** State shape for the Loops tab (architecture §2.2). */
data class LoopsState(
    val feed: com.kornerz.core.model.AsyncValue<PagingState> =
        com.kornerz.core.model.AsyncValue.Loading,
    val selectedFilter: LoopsFilter = LoopsFilter.ALL,
    val searchQuery: String = "",
    val isSearchMode: Boolean = false,
    val hasMoreData: Boolean = true,
    val accentColors: Map<String, androidx.compose.ui.graphics.Color> = emptyMap(),
)
