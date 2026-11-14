package com.kornerz.feature.home.loop

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Loop
import com.kornerz.feature.home.HomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Loop-of-the-day section. Single card showing the
 * featured Loop (architecture §6 / §8).
 */
@dagger.hilt.android.scopes.ViewModelScoped
class LoopOfDayViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<AsyncValue<Loop>>(AsyncValue.Loading)
    val state: StateFlow<AsyncValue<Loop>> = _state.asStateFlow()

    fun fetchData() {
        scope.launch {
            _state.value = AsyncValue.Loading
            homeRepository.fetchLoopOfDay()
                .onSuccess { loop ->
                    val publisherCode = loop.publisherId?.takeIf { it.isNotBlank() }
                    val publisher = publisherCode?.let { homeRepository.fetchUserByCode(it) }
                    _state.value = AsyncValue.Success(
                        loop.copy(
                            publisherName = publisher?.displayName ?: loop.publisherName,
                            publisherAvatarUrl = publisher?.thumbnailURL ?: loop.publisherAvatarUrl,
                        ),
                    )
                }
                .onFailure { _state.value = AsyncValue.Error(it) }
        }
    }
}
