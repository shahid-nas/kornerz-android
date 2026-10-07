package com.kornerz.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.network.AuthRepository
import com.kornerz.core.network.fcm.FcmTokenRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the placeholder Profile tab action that emits [ShellNavEvent.Logout].
 * The Screen collector calls `AuthRepository.logout()` and instructs the
 * root NavHost to swap back to the auth graph.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val fcmTokenRegistrar: FcmTokenRegistrar,
) : ViewModel() {

    private val _navEvents = Channel<ShellNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<ShellNavEvent> = _navEvents.receiveAsFlow()

    fun onLogout() {
        viewModelScope.launch {
            fcmTokenRegistrar.clearOnLogout()
            _navEvents.trySend(ShellNavEvent.Logout)
        }
    }
}

sealed interface ShellNavEvent {
    data object Logout : ShellNavEvent
}