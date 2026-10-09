package com.kornerz.feature.home.header

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.home.HomeHeaderData
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
 * Drives the Home-header section. Reads the current user from
 * [SessionRepository] and computes the gamification points. P2 returns
 * 0 points; P10 wires `GamificationService`.
 *
 * Scoped to the parent `HomeViewModel`'s lifetime via
 * [dagger.hilt.android.scopes.ViewModelScoped]; instantiated per-host
 * because the section shares the host's coroutine scope.
 */
@dagger.hilt.android.scopes.ViewModelScoped
class HomeHeaderViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<AsyncValue<HomeHeaderData>>(AsyncValue.Loading)
    val state: StateFlow<AsyncValue<HomeHeaderData>> = _state.asStateFlow()

    fun fetchData() {
        scope.launch {
            _state.value = AsyncValue.Loading
            runCatching {
                val session = sessionRepository.data.first()
                when (session) {
                    is SessionState.Authenticated -> HomeHeaderData(
                        user = session.user,
                        points = 0,
                    )
                    SessionState.Anonymous -> error("not signed in")
                    SessionState.Loading -> return@launch
                }
            }.onSuccess { _state.value = AsyncValue.Success(it) }
                .onFailure { _state.value = AsyncValue.Error(it) }
        }
    }
}
