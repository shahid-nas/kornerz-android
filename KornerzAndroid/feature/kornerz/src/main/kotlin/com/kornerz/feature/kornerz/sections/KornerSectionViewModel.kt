package com.kornerz.feature.kornerz.sections

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Korner
import com.kornerz.feature.kornerz.KornerzRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Section-level ViewModel for one of the three Kornerz tab carousels
 * (Your / Recommended / New this week). Mirrors the per-section pattern
 * used by the Home tab (`YourKornerzViewModel`, `RecentLoopsViewModel`,
 * etc.).
 *
 * Each section runs its own independent fetch — failure or empty list
 * on one section does not affect the others. The section composable
 * hides itself when the wrapped [AsyncValue] is `Success(empty)` per
 * spec `kornerz-tab/spec.md`.
 */
abstract class KornerSectionViewModel(
    private val fetch: suspend () -> Result<List<Korner>>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<AsyncValue<List<Korner>>>(AsyncValue.Loading)
    val state: StateFlow<AsyncValue<List<Korner>>> = _state.asStateFlow()

    fun fetchData() {
        scope.launch {
            _state.value = AsyncValue.Loading
            _state.value = fetch().fold(
                onSuccess = { AsyncValue.Success(it) },
                onFailure = { AsyncValue.Error(it) },
            )
        }
    }
}

class YourKornerzSectionViewModel @Inject constructor(
    private val repository: KornerzRepository,
) : KornerSectionViewModel(
    fetch = {
        repository.fetchYourKornerz().first().getOrElse { emptyList() }
            .let { Result.success(it) }
    },
) {
    init { fetchData() }
}

class RecommendedKornerzSectionViewModel @Inject constructor(
    private val repository: KornerzRepository,
) : KornerSectionViewModel(
    fetch = {
        repository.fetchRecommended().first().getOrElse { emptyList() }
            .let { Result.success(it) }
    },
) {
    init { fetchData() }
}

class NewThisWeekKornerzSectionViewModel @Inject constructor(
    private val repository: KornerzRepository,
) : KornerSectionViewModel(
    fetch = {
        repository.fetchNewThisWeek().first().getOrElse { emptyList() }
            .let { Result.success(it) }
    },
) {
    init { fetchData() }
}
