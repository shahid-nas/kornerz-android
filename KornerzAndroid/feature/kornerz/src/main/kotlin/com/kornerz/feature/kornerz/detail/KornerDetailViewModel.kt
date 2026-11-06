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

    /**
     * The accent colour the opening screen handed over, read off the
     * `themeColorHex` nav argument.
     *
     * The route carries the six hex digits **without** the `#` — a
     * navigation route is URI-shaped and a `#` would be read as the
     * fragment separator — so the value is canonicalised back to the
     * `#RRGGBB` form the UI resolves.
     *
     * Empty when the destination was opened without one (a deep link,
     * a nook detail), and the screen falls back to the Korner
     * document's own theme colour.
     */
    private val handoverThemeColorHex: String? =
        savedStateHandle.get<String>(ARG_THEME_COLOR_HEX)
            ?.takeIf { it.isNotBlank() }
            ?.let { "#${it.trim().removePrefix("#")}" }

    private val _state = MutableStateFlow(KornerDetailState(handoverThemeColorHex = handoverThemeColorHex))
    val state: StateFlow<KornerDetailState> = _state.asStateFlow()

    private val _navEvents = Channel<KornerDetailNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<KornerDetailNavEvent> = _navEvents.receiveAsFlow()

    /**
     * Whether a follow / unfollow write is in flight. iOS guards the
     * same way with its progress HUD — a second tap while the write
     * runs would create a duplicate `Fellow` document.
     */
    private var followInFlight = false

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
            KornerDetailEvent.FollowClicked -> toggleFollow()
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
            is KornerDetailEvent.JoinCtaClicked -> joinCall(event.nookId)
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
     * Joins the live session behind a nook card's "Join" capsule.
     *
     * Mirrors iOS `KornerDetailViewModel.joinCall(for:)`: the nook's
     * own `sessionId` is used when the tapped card is a scheduled
     * nook, and the Korner's `sessionId` is the fallback for the
     * "Current" card (which renders even when the Korner has no live
     * nook — iOS joins the community session there).
     *
     * A card without any session id has nothing to join yet, so the
     * screen reports it through the shared snackbar.
     */
    private fun joinCall(nookId: String) {
        val detail = (_state.value.detail as? AsyncValue.Success)?.data ?: return
        val nook = detail.nooks.firstOrNull { it.id == nookId }
        val sessionId = nook?.sessionId?.takeIf { it.isNotBlank() }
            ?: detail.korner.sessionId?.takeIf { it.isNotBlank() }
        if (sessionId == null) {
            emit(
                KornerDetailNavEvent.ShowMessage("This session isn't live yet"),
            )
            return
        }
        emit(
            KornerDetailNavEvent.JoinVideoCall(
                sessionId = sessionId,
                communityName = detail.korner.name,
                communityId = detail.korner.id,
            ),
        )
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

    /**
     * Follows the Korner when the user is not following it yet, and
     * unfollows it when they are — the toggle iOS
     * `KornerDetailViewModel.followTapped()` performs.
     *
     * The write is the `Fellow` document iOS writes
     * (`FellowRepository.followCommunity` / `unfollowCommunity`); on
     * success the capsule flips to the new state, the way iOS sets
     * `isFollowing` from the write's own result.
     *
     * A failed write leaves the state untouched and reports back, the
     * way iOS dismisses its progress HUD on the failure branch.
     */
    private fun toggleFollow() {
        val detail = (_state.value.detail as? AsyncValue.Success)?.data ?: return
        if (followInFlight) return
        followInFlight = true
        viewModelScope.launch {
            val following = detail.korner.isJoined
            val result = if (following) {
                repository.unfollowKorner(kornerId)
            } else {
                repository.followKorner(kornerId)
            }
            result
                .onSuccess { setJoined(!following) }
                .onFailure {
                    emit(
                        KornerDetailNavEvent.ShowMessage(
                            "We couldn't update your follow. Please try again.",
                        ),
                    )
                }
            followInFlight = false
        }
    }

    /**
     * Flips the follow state on the loaded Korner — iOS's
     * `isFollowing = true` / `isFollowing = false` after the write.
     */
    private fun setJoined(joined: Boolean) {
        _state.update { state ->
            val detail = (state.detail as? AsyncValue.Success)?.data ?: return@update state
            state.copy(
                detail = AsyncValue.Success(
                    detail.copy(korner = detail.korner.copy(isJoined = joined)),
                ),
            )
        }
    }

    private fun emit(event: KornerDetailNavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }

    companion object {
        const val ARG_KORNER_ID = "kornerId"

        /**
         * The nav argument carrying the accent colour the opening
         * screen handed over. Mirrors
         * [com.kornerz.feature.kornerz.KornerzRoute.Detail.ARG_THEME_COLOR_HEX];
         * the value is the six hex digits **without** the `#`.
         */
        const val ARG_THEME_COLOR_HEX = "themeColorHex"
    }
}
