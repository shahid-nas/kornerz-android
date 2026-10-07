package com.kornerz.feature.auth.welcome

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

/**
 * ViewModel for the welcome screen. Both CTAs are navigation events —
 * no business logic on this screen.
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(WelcomeState())
    val state: StateFlow<WelcomeState> = _state.asStateFlow()

    private val _navEvents = Channel<WelcomeNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<WelcomeNavEvent> = _navEvents.receiveAsFlow()

    fun onEvent(event: WelcomeEvent) {
        when (event) {
            WelcomeEvent.SignUpTapped -> _navEvents.trySend(WelcomeNavEvent.NavigateToSignup)
            WelcomeEvent.LogInTapped -> _navEvents.trySend(WelcomeNavEvent.NavigateToLogin)
        }
    }
}