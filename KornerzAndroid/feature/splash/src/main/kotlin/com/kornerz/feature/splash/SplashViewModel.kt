package com.kornerz.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.forceupdate.ForceUpdateRepository
import com.kornerz.core.network.forceupdate.ForceUpdateState
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Splash router. Reads the persisted session and the Remote Config
 * force-update flag, then emits [SplashNavEvent.NavigateToAuthGraph] or
 * [SplashNavEvent.NavigateToShellGraph] over a `Channel<NavEvent>`
 * (architecture §2.4). The Screen collector calls `navController.navigate(...)`.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val forceUpdateRepository: ForceUpdateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SplashState())
    val state: StateFlow<SplashState> = _state.asStateFlow()

    private val _navEvents = Channel<SplashNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<SplashNavEvent> = _navEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            val forceUpdate = forceUpdateRepository.state.first()
            _state.value = _state.value.copy(forceUpdate = forceUpdate)
            val session = sessionRepository.data.first { it !is SessionState.Loading }
            val route = when (session) {
                is SessionState.Authenticated -> SplashRoute.ShellGraph
                SessionState.Anonymous, SessionState.Loading -> SplashRoute.AuthGraph
            }
            _state.value = _state.value.copy(route = AsyncValue.Success(route))
            when (route) {
                SplashRoute.AuthGraph -> _navEvents.trySend(SplashNavEvent.NavigateToAuthGraph)
                SplashRoute.ShellGraph -> _navEvents.trySend(SplashNavEvent.NavigateToShellGraph)
            }
        }
    }

    /** Test/preview helper to inject a forced-update state. */
    fun onForceUpdate(value: ForceUpdateState) {
        _state.value = _state.value.copy(forceUpdate = value)
    }
}