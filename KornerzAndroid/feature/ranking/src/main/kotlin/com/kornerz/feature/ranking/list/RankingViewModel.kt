package com.kornerz.feature.ranking.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.gamification.GamificationRepository
import com.kornerz.core.ui.components.header.TabHeaderViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Klouders leaderboard's UDF contract (screens 2.6 + 2.7).
 *
 * Loads the **top 10** rows on init, matching iOS
 * `RankingViewModel.fetchRankings()` (`kornerz/Kornerz/HomeVC/Ranking/
 * RankingViewModel.swift:51-70`), which calls `gamification.getRanking(10)`.
 *
 * iOS caps the leaderboard at ten rows, so Android does too — the
 * leaderboard has no pagination.
 *
 * Also composes the shared `TabHeaderViewModel` so the tab carries the
 * same avatar + points header as the other bottom-bar tabs (mirrors
 * iOS, where every tab embeds `HomeHeaderView` at the top of its
 * content).
 */
@HiltViewModel
class RankingViewModel @Inject constructor(
    private val gamificationRepository: GamificationRepository,
    private val headerVm: TabHeaderViewModel,
) : ViewModel() {

    private val _state = MutableStateFlow(RankingState())
    val state: StateFlow<RankingState> = combine(
        _state,
        headerVm.state,
    ) { s, header -> s.copy(header = header) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = _state.value,
        )

    private val _navEvents = Channel<RankingNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<RankingNavEvent> = _navEvents.receiveAsFlow()

    init {
        headerVm.fetchData()
        loadRankings()
    }

    fun onEvent(event: RankingEvent) {
        when (event) {
            RankingEvent.AvatarTapped -> emitNav(RankingNavEvent.NavigateToProfile)
            RankingEvent.Retry -> loadRankings()
        }
    }

    /**
     * (Re)loads the leaderboard. Called on init and by the retry
     * affordance of the error state.
     */
    fun loadRankings() {
        viewModelScope.launch {
            _state.update { it.copy(rankings = AsyncValue.Loading) }
            gamificationRepository.getRanking(TOP_ROWS)
                .onSuccess { rows ->
                    _state.update { it.copy(rankings = AsyncValue.Success(rows)) }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(rankings = AsyncValue.Error(throwable)) }
                }
        }
    }

    private fun emitNav(event: RankingNavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }

    companion object {
        /** The number of rows the leaderboard shows. iOS parity — see `RankingViewModel.swift:56`. */
        const val TOP_ROWS: Int = 10
    }
}
