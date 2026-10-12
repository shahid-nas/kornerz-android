package com.kornerz.feature.nooks.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.kornerz.core.model.AsyncValue
import com.kornerz.feature.nooks.data.NooksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
 * Hilt-managed ViewModel for the Nooks tab list. Owns two paging
 * state machines (live + upcoming) backed by [NooksRepository].
 *
 * Architecture §2.1 / §2.4 / §2.6: emits navigation through a
 * buffered [Channel]; supports cursor-based pagination for both
 * sections (iOS-equivalent of `communityLastSnapshot` /
 * `eventLastSnapshot`).
 */
@HiltViewModel
class NooksViewModel @Inject constructor(
    private val repository: NooksRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NooksState())
    val state: StateFlow<NooksState> = _state.asStateFlow()

    private val _navEvents = Channel<NooksNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<NooksNavEvent> = _navEvents.receiveAsFlow()

    private var liveJob: Job? = null
    private var upcomingJob: Job? = null
    private var liveLoadMoreJob: Job? = null
    private var upcomingLoadMoreJob: Job? = null

    // Pagination cursors — held privately so the public state stays
    // free of Firestore types.
    private var liveCursor: DocumentSnapshot? = null
    private var upcomingCursor: DocumentSnapshot? = null

    init {
        loadLiveInitial()
        loadUpcomingInitial()
    }

    fun onEvent(event: NooksEvent) {
        when (event) {
            NooksEvent.Refresh -> {
                loadLiveInitial()
                loadUpcomingInitial()
            }
            NooksEvent.Retry -> {
                loadLiveInitial()
                loadUpcomingInitial()
            }
            NooksEvent.LoadMoreLive -> loadLiveMore()
            NooksEvent.LoadMoreUpcoming -> loadUpcomingMore()
            is NooksEvent.SelectType -> {
                _state.update { it.copy(selectedType = event.type) }
            }
            is NooksEvent.LiveCardClicked ->
                emit(NooksNavEvent.NavigateToKornerDetail(event.kornerId))
            is NooksEvent.CardClicked ->
                emit(NooksNavEvent.NavigateToNookDetail(event.nookId))
            is NooksEvent.JoinVideoCall ->
                emit(NooksNavEvent.JoinVideoCall(event.sessionId))
        }
    }

    fun refresh() = onEvent(NooksEvent.Refresh)

    private fun loadLiveInitial() {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            _state.update { it.copy(live = AsyncValue.Loading) }
            liveCursor = null
            repository.fetchLiveKornerzPage(after = null).collect { result ->
                result.fold(
                    onSuccess = { page ->
                        liveCursor = page.lastSnapshot
                        _state.update {
                            it.copy(
                                live = AsyncValue.Success(
                                    LiveSectionState(
                                        items = page.items,
                                        isLoadingMore = false,
                                        hasMore = page.hasMore,
                                    ),
                                ),
                            )
                        }
                    },
                    onFailure = { t ->
                        _state.update { it.copy(live = AsyncValue.Error(t)) }
                    },
                )
            }
        }
    }

    private fun loadUpcomingInitial() {
        upcomingJob?.cancel()
        upcomingJob = viewModelScope.launch {
            _state.update { it.copy(upcoming = AsyncValue.Loading) }
            upcomingCursor = null
            repository.fetchUpcomingPage(after = null).collect { result ->
                result.fold(
                    onSuccess = { page ->
                        upcomingCursor = page.lastSnapshot
                        _state.update {
                            it.copy(
                                upcoming = AsyncValue.Success(
                                    UpcomingSectionState(
                                        items = page.items,
                                        isLoadingMore = false,
                                        hasMore = page.hasMore,
                                    ),
                                ),
                            )
                        }
                    },
                    onFailure = { t ->
                        _state.update { it.copy(upcoming = AsyncValue.Error(t)) }
                    },
                )
            }
        }
    }

    private fun loadLiveMore() {
        val current = _state.value.live as? AsyncValue.Success ?: return
        if (!current.data.hasMore || current.data.isLoadingMore) return
        val cursor = liveCursor ?: return
        liveLoadMoreJob?.cancel()
        liveLoadMoreJob = viewModelScope.launch {
            _state.update { it.copy(live = AsyncValue.Success(current.data.copy(isLoadingMore = true))) }
            repository.fetchLiveKornerzPage(after = cursor).collect { result ->
                result.fold(
                    onSuccess = { page ->
                        liveCursor = page.lastSnapshot
                        _state.update {
                            it.copy(
                                live = AsyncValue.Success(
                                    current.data.copy(
                                        items = current.data.items + page.items,
                                        isLoadingMore = false,
                                        hasMore = page.hasMore,
                                    ),
                                ),
                            )
                        }
                    },
                    onFailure = {
                        _state.update {
                            it.copy(live = AsyncValue.Success(current.data.copy(isLoadingMore = false)))
                        }
                    },
                )
            }
        }
    }

    private fun loadUpcomingMore() {
        val current = _state.value.upcoming as? AsyncValue.Success ?: return
        if (!current.data.hasMore || current.data.isLoadingMore) return
        val cursor = upcomingCursor ?: return
        upcomingLoadMoreJob?.cancel()
        upcomingLoadMoreJob = viewModelScope.launch {
            _state.update { it.copy(upcoming = AsyncValue.Success(current.data.copy(isLoadingMore = true))) }
            repository.fetchUpcomingPage(after = cursor).collect { result ->
                result.fold(
                    onSuccess = { page ->
                        upcomingCursor = page.lastSnapshot
                        _state.update {
                            it.copy(
                                upcoming = AsyncValue.Success(
                                    current.data.copy(
                                        items = current.data.items + page.items,
                                        isLoadingMore = false,
                                        hasMore = page.hasMore,
                                    ),
                                ),
                            )
                        }
                    },
                    onFailure = {
                        _state.update {
                            it.copy(upcoming = AsyncValue.Success(current.data.copy(isLoadingMore = false)))
                        }
                    },
                )
            }
        }
    }

    private fun emit(event: NooksNavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }
}
