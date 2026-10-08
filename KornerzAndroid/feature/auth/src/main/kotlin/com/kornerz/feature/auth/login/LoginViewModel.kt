package com.kornerz.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Login screen. Exposes [state] as a [StateFlow] and
 * [navEvents] as a `Channel(BUFFERED)` flow (architecture §2.4). Never
 * touches a `NavController`.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _navEvents = Channel<LoginNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<LoginNavEvent> = _navEvents.receiveAsFlow()

    private var submitting = false

    fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.EmailChanged -> _state.value = _state.value.copy(
                email = event.value,
            )
            is LoginEvent.PasswordChanged -> _state.value = _state.value.copy(
                password = event.value,
            )
            LoginEvent.Submit -> submit()
            LoginEvent.ForgotPassword -> _navEvents.trySend(LoginNavEvent.NavigateToForgotPassword)
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.isFormValid || submitting) return
        submitting = true
        _state.value = current.copy(submit = AsyncValue.Loading)
        viewModelScope.launch {
            val result = authRepository.login(
                email = current.email.trim(),
                password = current.password,
            )
            _state.value = _state.value.copy(submit = result)
            submitting = false
            if (result is AsyncValue.Success) {
                _navEvents.trySend(LoginNavEvent.NavigateToShell)
            }
        }
    }
}