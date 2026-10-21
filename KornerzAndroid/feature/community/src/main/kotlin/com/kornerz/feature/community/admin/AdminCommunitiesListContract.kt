package com.kornerz.feature.community.admin

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Community

/**
 * State for the admin-owned-communities list (P7 +
 * `admin-communities-list` spec, revised to match iOS
 * `AdminCommunityListViewModel`).
 *
 * The list is one-shot (no real-time listener on either platform),
 * fed by `AdminCommunitiesRepository.fetchOwnedByUser` via the
 * two-step `CommunityAdm → Communities(whereIn)` join. Search filtering
 * is a UI-only concern handled in
 * [AdminCommunitiesListContent] (mirrors iOS's `viewModel.filter` +
 * `viewModel.filteredCommunities` computed property).
 */
data class AdminCommunitiesListState(
    val items: AsyncValue<List<Community>> = AsyncValue.Loading,
    val isRefreshing: Boolean = false,
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
