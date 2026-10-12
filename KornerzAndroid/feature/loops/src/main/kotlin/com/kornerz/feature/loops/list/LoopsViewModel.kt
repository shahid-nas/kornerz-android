package com.kornerz.feature.loops.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Loop
import com.kornerz.core.model.LoopsFilter
import com.kornerz.core.network.community.CommunitiesRepository
import com.kornerz.core.network.loops.LoopCursor
import com.kornerz.core.network.user.UsersRepository
import com.kornerz.feature.loops.components.LoopCardData
import com.kornerz.feature.loops.components.RandomColorService
import com.kornerz.feature.loops.data.LoopMapper
import com.kornerz.feature.loops.data.LoopRepository
import com.kornerz.feature.loops.data.toCardData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hilt-managed ViewModel for the Loops tab. Owns the feed
 * `MutableStateFlow<LoopsState>`, a per-filter cursor, and the
 * debounced search flow. Mirrors iOS `LoopsTabViewModel`.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class LoopsViewModel @Inject constructor(
    private val loopRepository: LoopRepository,
    private val usersRepository: UsersRepository,
    private val communitiesRepository: CommunitiesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoopsState())
    val state: StateFlow<LoopsState> = _state.asStateFlow()

    private val _navEvents = Channel<LoopsNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<LoopsNavEvent> = _navEvents.receiveAsFlow()

    private val searchQuery = MutableStateFlow("")
    private val searchSubmitTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var feedJob: Job? = null
    private var lastDocument: LoopCursor? = null

    init {
        loadInitial()
        observeSearch()
    }

    fun onEvent(event: LoopsEvent) {
        when (event) {
            LoopsEvent.Refresh -> loadInitial()
            LoopsEvent.Retry -> loadInitial()
            is LoopsEvent.LoopClicked -> emit(LoopsNavEvent.NavigateToLoopDetail(event.loopId))
            is LoopsEvent.FilterChanged -> {
                if (_state.value.selectedFilter != event.filter) {
                    _state.update { it.copy(selectedFilter = event.filter) }
                    loadInitial()
                }
            }
            LoopsEvent.SearchToggled -> _state.update { it.copy(isSearchMode = true) }
            is LoopsEvent.SearchQueryChanged -> {
                searchQuery.value = event.query
                _state.update { it.copy(searchQuery = event.query) }
            }
            LoopsEvent.SearchSubmitted -> searchSubmitTrigger.tryEmit(Unit)
            LoopsEvent.SearchCancelled -> {
                _state.update { it.copy(isSearchMode = false, searchQuery = "") }
                searchQuery.value = ""
                loadInitial()
            }
            LoopsEvent.LoadMore,
            LoopsEvent.ScrollReachedEnd -> loadMore()
        }
    }

    fun fetchLoopColor(index: Int): androidx.compose.ui.graphics.Color =
        RandomColorService.colorFor(index)

    private fun loadInitial() {
        feedJob?.cancel()
        lastDocument = null
        _state.update { it.copy(feed = AsyncValue.Loading, hasMoreData = true) }
        feedJob = viewModelScope.launch {
            loopRepository
                .observeLoops(
                    filter = _state.value.selectedFilter,
                    searchQuery = searchQuery.value,
                    cursor = null,
                )
                .collect { result ->
                    result.fold(
                        onSuccess = { page ->
                            lastDocument = page.nextCursor
                            val cardData = enrich(page.items)
                            _state.update {
                                it.copy(
                                    feed = AsyncValue.Success(
                                        PagingState(
                                            items = cardData,
                                            isLoadingMore = false,
                                            hasMore = page.hasMore,
                                        ),
                                    ),
                                    hasMoreData = page.hasMore,
                                )
                            }
                        },
                        onFailure = { t -> _state.update { it.copy(feed = AsyncValue.Error(t)) } },
                    )
                }
        }
    }

    private fun loadMore() {
        val cursor = lastDocument ?: return
        val current = _state.value.feed as? AsyncValue.Success ?: return
        if (!_state.value.hasMoreData || current.data.isLoadingMore) return
        viewModelScope.launch {
            _state.update {
                it.copy(feed = AsyncValue.Success(current.data.copy(isLoadingMore = true)))
            }
            loopRepository
                .observeLoops(
                    filter = _state.value.selectedFilter,
                    searchQuery = searchQuery.value,
                    cursor = cursor,
                )
                .collect { result ->
                    result.fold(
                        onSuccess = { page ->
                            lastDocument = page.nextCursor
                            val cardData = enrich(page.items)
                            _state.update {
                                it.copy(
                                    feed = AsyncValue.Success(
                                        current.data.copy(
                                            items = current.data.items + cardData,
                                            isLoadingMore = false,
                                            hasMore = page.hasMore,
                                        ),
                                    ),
                                    hasMoreData = page.hasMore,
                                )
                            }
                        },
                        onFailure = {
                            _state.update {
                                it.copy(feed = AsyncValue.Success(current.data.copy(isLoadingMore = false)))
                            }
                        },
                    )
                }
        }
    }

    private suspend fun enrich(loops: List<Loop>): List<LoopCardData> {
        if (loops.isEmpty()) return emptyList()
        val communityIds = loops.mapNotNull { it.communityId }.distinct()
        val publisherIds = loops.mapNotNull { it.publisherId }.distinct()
        val communities: Map<String, com.kornerz.core.model.Community> =
            communitiesRepository.fetchCommunityNamesByIds(communityIds)
                .mapValues { (id, name) ->
                    com.kornerz.core.model.Community(
                        id = id,
                        name = name,
                    )
                }
        val users: Map<String, com.kornerz.core.model.User> =
            usersRepository.fetchUsersByCodes(publisherIds)
                .mapValues { (_, snippet) ->
                    com.kornerz.core.model.User(
                        id = snippet.code,
                        email = "",
                        displayName = snippet.name,
                        nickname = snippet.name,
                        photoUrl = snippet.thumbnailUrl,
                    )
                }
        return loops.toCardData(communities, users)
    }

    private fun observeSearch() {
        viewModelScope.launch {
            searchQuery.debounce(1_500L).collectLatest {
                loadInitial()
            }
        }
        viewModelScope.launch {
            searchSubmitTrigger.collect {
                loadInitial()
            }
        }
    }

    private fun emit(event: LoopsNavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }
}
