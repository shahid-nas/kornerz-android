package com.kornerz.feature.ranking.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.gamification.GamificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 */
@HiltViewModel
class RankingViewModel @Inject constructor(
    private val gamificationRepository: GamificationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RankingState())
    val state: StateFlow<RankingState> = _state.asStateFlow()

    init {
        loadRankings()
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

    companion object {
        /** The number of rows the leaderboard shows. iOS parity — see `RankingViewModel.swift:56`. */
        const val TOP_ROWS: Int = 10
    }
}
