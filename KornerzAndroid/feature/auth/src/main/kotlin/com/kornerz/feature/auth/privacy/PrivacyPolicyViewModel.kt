package com.kornerz.feature.auth.privacy

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel for the standalone Privacy Policy screen. Tracks the
 * scroll-to-end gate and emits navigation events when the user accepts
 * or declines (declines returns to the previous auth screen).
 */
@HiltViewModel
class PrivacyPolicyViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(PrivacyPolicyState())
    val state: StateFlow<PrivacyPolicyState> = _state.asStateFlow()

    private val _navEvents = Channel<PrivacyPolicyNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<PrivacyPolicyNavEvent> = _navEvents.receiveAsFlow()

    fun onEvent(event: PrivacyPolicyEvent) {
        when (event) {
            is PrivacyPolicyEvent.ScrolledToEndChanged -> _state.update {
                it.copy(hasScrolledToEnd = it.hasScrolledToEnd || event.value)
            }
            PrivacyPolicyEvent.Accept -> {
                if (_state.value.canAccept) {
                    _navEvents.trySend(PrivacyPolicyNavEvent.NavigateToSignup)
                }
            }
            PrivacyPolicyEvent.Decline -> _navEvents.trySend(PrivacyPolicyNavEvent.NavigateBack)
        }
    }
}