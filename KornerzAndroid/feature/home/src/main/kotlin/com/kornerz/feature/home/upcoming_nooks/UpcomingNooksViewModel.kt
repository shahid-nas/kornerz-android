package com.kornerz.feature.home.upcoming_nooks

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Nook
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.home.HomeRepository
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
 * Drives the Upcoming Nooks section. The user's membership IDs come
 * from the [SessionRepository] flow (P2 uses the cached User; the full
 * memberships subcollection lands in P3) and are passed to the
 * HomeRepository for the time-windowed events query.
 */
@dagger.hilt.android.scopes.ViewModelScoped
class UpcomingNooksViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<AsyncValue<List<Nook>>>(AsyncValue.Loading)
    val state: StateFlow<AsyncValue<List<Nook>>> = _state.asStateFlow()

    fun fetchData() {
        scope.launch {
            _state.value = AsyncValue.Loading
            runCatching {
                val session = sessionRepository.data.first()
                if (session !is SessionState.Authenticated) return@runCatching emptyList<Nook>()
                val communityIds = listOf(session.user.id) // P2 placeholder: feed from full memberships in P3
                homeRepository.fetchUpcomingNooks(communityIds).getOrThrow()
            }.onSuccess { _state.value = AsyncValue.Success(it) }
                .onFailure { _state.value = AsyncValue.Error(it) }
        }
    }
}
