package com.kornerz.feature.community.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Community
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.community.data.AdminCommunitiesRepository
import com.kornerz.feature.community.data.CommunityCountKind
import com.kornerz.feature.community.data.CommunitiesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
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
 * ViewModel for the admin-owned-communities list (P7, task 7.2).
 * Mirrors iOS `AdminCommunityListController.viewDidLoad` (lines 38–71)
 * — the initial `Communities.whereEqualTo("userId", userCode)` query
 * followed by per-row count queries in parallel via `coroutineScope {
 * async { ... } }`.
 */
@HiltViewModel
class AdminCommunitiesListViewModel @Inject constructor(
    private val communitiesRepository: CommunitiesRepository,
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
                    populateRowCounts(communities)
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

    private fun populateRowCounts(communities: List<Community>) {
        viewModelScope.launch {
            val counts = coroutineScope {
                communities.map { community ->
                    async {
                        val members = communitiesRepository.fetchCount(community.code, CommunityCountKind.Members)
                        val nooks = communitiesRepository.fetchCount(community.code, CommunityCountKind.Nooks)
                        community.code to (members to nooks)
                    }
                }.map { it.await() }
            }.toMap()
            _state.update { it.copy(rowCounts = counts) }
        }
    }
}
