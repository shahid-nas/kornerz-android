package com.kornerz.feature.community.admin

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Community

/**
 * State for the admin-owned-communities list (P7, task 7.1).
 * The per-row [rowCounts] map is populated by
 * [AdminCommunitiesListViewModel] in parallel after the first load
 * (mirrors iOS `AdminCommunityListController`).
 */
data class AdminCommunitiesListState(
    val items: AsyncValue<List<Community>> = AsyncValue.Loading,
    val isRefreshing: Boolean = false,
    val rowCounts: Map<String, Pair<Int, Int>> = emptyMap(),
    val error: String? = null,
)

sealed interface AdminCommunitiesListEvent {
    data object Refresh : AdminCommunitiesListEvent
    data object Retry : AdminCommunitiesListEvent
}

sealed interface AdminCommunitiesListNavEvent {
    data class OpenCommunityDetail(val code: String) : AdminCommunitiesListNavEvent
    data object Back : AdminCommunitiesListNavEvent
    data class ShowError(val message: String) : AdminCommunitiesListNavEvent
}
