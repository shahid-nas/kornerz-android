package com.kornerz.feature.community.admin.detail

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.User

/**
 * State for the `CommunityDetailAsAdmin` screen. Mirrors
 * `CommunityDetailAsAdminVC.setupUI()` fields on iOS, condensed
 * into a single [CommunityStats] projection so the screen renders a
 * single source of truth instead of wiring seven independent
 * callback-driven setters.
 */
data class CommunityDetailState(
    val stats: AsyncValue<CommunityStats> = AsyncValue.Loading,
    val adminUser: User? = null,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

sealed interface CommunityDetailEvent {
    data object Refresh : CommunityDetailEvent
    data object Retry : CommunityDetailEvent
    data object PendingRequestsClicked : CommunityDetailEvent
}

sealed interface CommunityDetailNavEvent {
    data object Back : CommunityDetailNavEvent
    data object OpenPendingRequests : CommunityDetailNavEvent
    data class ShowError(val message: String) : CommunityDetailNavEvent
}
