package com.kornerz.feature.community.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.community.data.AdminCommunitiesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the admin-owned-communities list (P7, task 7.2 +
 * parity fix). Mirrors iOS `AdminCommunityListViewModel.fetchData()`
 * lines 76–118 — the two-step `CommunityAdm.whereField("userId",
 * == userId).limit(100)` join followed by `Communities.whereField(
 * "communityId", in: ids).whereField("communityApproved", == true)`,
 * batched in groups of 30 because Firestore caps `whereIn` at 30.
 *
 * Search filtering is performed client-side at the screen layer
 * (mirrors iOS `AdminCommunityListViewModel.filteredCommunities` on
 * lines 20–26). Real-time updates are NOT used on either platform.
 */
@HiltViewModel
class AdminCommunitiesListViewModel @Inject constructor(
    private val adminCommunitiesRepository: AdminCommunitiesRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminCommunitiesListState())
    val state: StateFlow<AdminCommunitiesListState> = _state.asStateFlow()

    private val _navEvents = Channel<AdminCommunitiesListNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<AdminCommunitiesListNavEvent> = _navEvents.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: AdminCommunitiesListEvent) {
        when (event) {
            AdminCommunitiesListEvent.Refresh -> load()
            AdminCommunitiesListEvent.Retry -> load()
        }
    }

    fun onCommunityClicked(code: String) {
        _navEvents.trySend(AdminCommunitiesListNavEvent.OpenCommunityDetail(code))
    }

    fun onBack() {
        _navEvents.trySend(AdminCommunitiesListNavEvent.Back)
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            val user = runCatching {
                sessionRepository.data
                    .first { it is SessionState.Authenticated }
                    .let { (it as SessionState.Authenticated).user }
            }.getOrNull()
            val userCode = user?.userCode.orEmpty().ifEmpty { user?.id.orEmpty() }
            if (userCode.isBlank()) {
                _state.update {
                    it.copy(
                        items = AsyncValue.Success(emptyList()),
                        isRefreshing = false,
                    )
                }
                return@launch
            }
            val result = adminCommunitiesRepository.fetchOwnedByUser(userCode)
            result.fold(
                onSuccess = { communities ->
                    _state.update {
                        it.copy(
                            items = AsyncValue.Success(communities),
                            isRefreshing = false,
                        )
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "Failed to load communities"
                    _state.update {
                        it.copy(
                            items = AsyncValue.Error(throwable),
                            isRefreshing = false,
                            error = message,
                        )
                    }
                    _navEvents.trySend(AdminCommunitiesListNavEvent.ShowError(message))
                },
            )
        }
    }
}
