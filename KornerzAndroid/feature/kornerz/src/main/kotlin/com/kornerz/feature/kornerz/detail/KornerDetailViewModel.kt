package com.kornerz.feature.kornerz.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Korner
import com.kornerz.feature.kornerz.KornerzRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hilt-managed ViewModel for the Korner detail screen.
 *
 * Keyed by `kornerId` via Navigation-Compose's `SavedStateHandle` (per
 * design §"Decision: `KornerDetailViewModel` keyed by `kornerId` via
 * Hilt `SavedStateHandle`"). Initiates the read on `init` (per task
 * 7.6).
 *
 * The read mirrors iOS `KornerDetailViewModel.fetchData()`, which
 * fans out into five parallel reads. Android resolves three of them:
 *
 *  - the Korner document, its nooks and its followers
 *    (`KornerzRepository.fetchKornerDetail`),
 *  - the Korner's admins — the "Kornerstones" tab
 *    (`KornerzRepository.fetchKornerAdmins`),
 *  - the Korner's latest loops — the "Recently in" section
 *    (`KornerzRepository.fetchKornerRecentLoops`).
 *
 * The two extra reads are kicked off once the Korner document has been
 * resolved, because both are keyed by the community's `communityId`
 * field.
 *
 * Nook and member tile clicks forward to the navigation channel —
 * destinations resolve to "Coming soon" snackbars until the
 * corresponding screens ship.
 */
@HiltViewModel
class KornerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: KornerzRepository,
) : ViewModel() {

    private val kornerId: String = savedStateHandle.get<String>(ARG_KORNER_ID).orEmpty()

    private val _state = MutableStateFlow(KornerDetailState())
    val state: StateFlow<KornerDetailState> = _state.asStateFlow()

    private val _navEvents = Channel<KornerDetailNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<KornerDetailNavEvent> = _navEvents.receiveAsFlow()

    init {
        if (kornerId.isNotBlank()) fetchData() else {
            _state.update { it.copy(detail = AsyncValue.Error(IllegalArgumentException("Missing kornerId"))) }
        }
    }

    fun onEvent(event: KornerDetailEvent) {
        when (event) {
            KornerDetailEvent.Retry -> fetchData(showLoading = true)
            KornerDetailEvent.Refresh -> {
                // A pull-to-refresh keeps the current content on screen
                // under the refresh indicator — only the very first load
                // swaps the content for the shimmer.
                _state.update { it.copy(isRefreshing = true) }
                fetchData(showLoading = false)
            }
            KornerDetailEvent.BackClicked -> emit(KornerDetailNavEvent.NavigateBack)
            // The follow / unfollow write is not implemented on Android
            // yet — the `Fellow` collection is only read. The button
            // carries the iOS affordance, the action reports back.
            KornerDetailEvent.FollowClicked -> emit(
                KornerDetailNavEvent.ShowMessage("Following a Korner is coming soon"),
            )
            KornerDetailEvent.AboutClicked -> _state.update { it.copy(isAboutSheetVisible = true) }
            KornerDetailEvent.AboutDismissed -> _state.update { it.copy(isAboutSheetVisible = false) }
            is KornerDetailEvent.NookTabSelected ->
                _state.update { it.copy(selectedNookTab = event.tab) }
            is KornerDetailEvent.UserTabSelected ->
                _state.update { it.copy(selectedUserTab = event.tab) }
            is KornerDetailEvent.NookTileClicked -> emit(KornerDetailNavEvent.NavigateToNookDetail(event.nookId))
            is KornerDetailEvent.MemberTileClicked -> emit(KornerDetailNavEvent.NavigateToMemberProfile(event.userId))
            is KornerDetailEvent.LoopTileClicked -> emit(
                KornerDetailNavEvent.NavigateToLoopDetail(event.loopId, event.accentColorHex),
            )
            is KornerDetailEvent.JoinCtaClicked -> emit(
                KornerDetailNavEvent.ShowMessage("Joining a session is coming soon"),
            )
        }
    }

    private fun fetchData(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _state.update { it.copy(detail = AsyncValue.Loading) }
            }
            repository.fetchKornerDetail(kornerId).collect { result ->
                result
                    .onSuccess { detail ->
                        _state.update {
                            it.copy(detail = AsyncValue.Success(detail), isRefreshing = false)
                        }
                        loadExtras(detail.korner)
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(detail = AsyncValue.Error(error), isRefreshing = false)
                        }
                    }
            }
        }
    }

    /**
     * The two reads that hang off the resolved Korner document: its
     * admins (the "Kornerstones" tab) and its latest loops (the
     * "Recently in" section). Both degrade to an empty list so the
     * screen still renders — iOS prints the error and keeps the screen
     * alive.
     */
    private fun loadExtras(korner: Korner) {
        viewModelScope.launch {
            repository.fetchKornerAdmins(kornerId).collect { result ->
                _state.update { it.copy(kornerstones = result.getOrDefault(emptyList())) }
            }
        }
        viewModelScope.launch {
            repository.fetchKornerRecentLoops(korner).collect { result ->
                _state.update { it.copy(recentLoops = result.getOrDefault(emptyList())) }
            }
        }
    }

    private fun emit(event: KornerDetailNavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }

    companion object {
        const val ARG_KORNER_ID = "kornerId"
    }
}
