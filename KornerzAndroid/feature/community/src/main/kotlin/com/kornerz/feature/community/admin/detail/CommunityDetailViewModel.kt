package com.kornerz.feature.community.admin.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
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
 * ViewModel for the community-detail-as-admin stats screen (mirrors
 * iOS `CommunityDetailAsAdminVC` + `OwnedCommunityViewModel`).
 *
 * Reads the 6-char `code` from the navigation
 * `SavedStateHandle.arguments["code"]` (the `community/admin/detail/{code}`
 * route, declared on `CommunityRoute.AdminDetail` + wired by
 * `AppShell.kt`) and fires a single
 * [OwnedCommunityStatsRepository.fetchStats] call that returns the
 * [CommunityStats] projection (which already fans out the seven
 * counts in parallel inside the repository).
 *
 * The header also reads the current authenticated [User] from
 * [SessionRepository] so the header card can render the
 * "Administrator" label (mirrors iOS line 164
 * `AppHelper.shared.user?.name ?? ""`).
 */
@HiltViewModel
class CommunityDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val statsRepository: OwnedCommunityStatsRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val communityCode: String = savedStateHandle.get<String>(ARG_CODE).orEmpty()

    private val _state = MutableStateFlow(CommunityDetailState())
    val state: StateFlow<CommunityDetailState> = _state.asStateFlow()

    private val _navEvents = Channel<CommunityDetailNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<CommunityDetailNavEvent> = _navEvents.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: CommunityDetailEvent) {
        when (event) {
            CommunityDetailEvent.Refresh -> load()
            CommunityDetailEvent.Retry -> load()
            CommunityDetailEvent.PendingRequestsClicked ->
                _navEvents.trySend(CommunityDetailNavEvent.OpenPendingRequests)
        }
    }

    fun onBack() {
        _navEvents.trySend(CommunityDetailNavEvent.Back)
    }

    private fun load() {
        if (communityCode.isBlank()) {
            _state.update { it.copy(error = "Missing community code") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            val adminUser = runCatching {
                sessionRepository.data
                    .first { it is SessionState.Authenticated }
                    .let { (it as SessionState.Authenticated).user }
            }.getOrNull()
            _state.update { it.copy(adminUser = adminUser) }
            statsRepository.fetchStats(communityCode).fold(
                onSuccess = { stats ->
                    _state.update {
                        it.copy(
                            stats = AsyncValue.Success(stats),
                            isRefreshing = false,
                        )
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "Failed to load community stats"
                    _state.update {
                        it.copy(
                            stats = AsyncValue.Error(throwable),
                            isRefreshing = false,
                            error = message,
                        )
                    }
                    _navEvents.trySend(CommunityDetailNavEvent.ShowError(message))
                },
            )
        }
    }

    companion object {
        const val ARG_CODE = "code"
    }
}
