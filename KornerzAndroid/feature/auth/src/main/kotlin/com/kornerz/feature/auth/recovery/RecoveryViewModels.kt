package com.kornerz.feature.auth.recovery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordState())
    val state: StateFlow<ForgotPasswordState> = _state.asStateFlow()

    private val _navEvents = Channel<ForgotPasswordNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<ForgotPasswordNavEvent> = _navEvents.receiveAsFlow()

    fun onEvent(event: ForgotPasswordEvent) {
        when (event) {
            is ForgotPasswordEvent.EmailChanged -> _state.value = _state.value.copy(email = event.value)
            ForgotPasswordEvent.Submit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.isFormValid) return
        _state.value = current.copy(submit = AsyncValue.Loading)
        viewModelScope.launch {
            val result = authRepository.requestPasswordReset(email = current.email.trim())
            _state.value = _state.value.copy(submit = result)
            if (result is AsyncValue.Success) {
                _navEvents.trySend(ForgotPasswordNavEvent.NavigateToOtp(current.email.trim()))
            }
        }
    }
}

@HiltViewModel
class OtpConfirmViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val email: String = savedStateHandle.get<String>(ARG_EMAIL).orEmpty()

    private val _state = MutableStateFlow(OtpConfirmState(email = email, resendCooldownSeconds = 30))
    val state: StateFlow<OtpConfirmState> = _state.asStateFlow()

    private val _navEvents = Channel<OtpConfirmNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<OtpConfirmNavEvent> = _navEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                _state.value = _state.value.copy(
                    resendCooldownSeconds = (_state.value.resendCooldownSeconds - 1).coerceAtLeast(0),
                )
            }
        }
    }

    fun onEvent(event: OtpConfirmEvent) {
        when (event) {
            is OtpConfirmEvent.CodeChanged -> _state.value = _state.value.copy(code = event.value)
            OtpConfirmEvent.Submit -> submit()
            OtpConfirmEvent.Resend -> resend()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.isFormValid) return
        _state.value = current.copy(submit = AsyncValue.Loading)
        viewModelScope.launch {
            val result = authRepository.confirmPasswordResetCode(
                email = current.email,
                code = current.code,
            )
            val submitResult: AsyncValue<Unit> = when (result) {
                is AsyncValue.Success -> AsyncValue.Success(Unit)
                is AsyncValue.Error -> AsyncValue.Error(result.throwable)
                AsyncValue.Loading -> AsyncValue.Loading
            }
            _state.value = _state.value.copy(submit = submitResult)
            if (result is AsyncValue.Success) {
                _state.value = _state.value.copy(code = "")
                _navEvents.trySend(
                    OtpConfirmNavEvent.NavigateToChangePassword(current.email, result.data),
                )
            }
        }
    }

    private fun resend() {
        if (_state.value.resendCooldownSeconds > 0) return
        _state.value = _state.value.copy(resendCooldownSeconds = 30)
        viewModelScope.launch {
            authRepository.requestPasswordReset(email = _state.value.email)
        }
    }

    private companion object {
        const val ARG_EMAIL = "email"
    }
}

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val email: String = savedStateHandle.get<String>(ARG_EMAIL).orEmpty()
    private val resetToken: String = savedStateHandle.get<String>(ARG_TOKEN).orEmpty()

    private val _state = MutableStateFlow(
        ChangePasswordState(email = email, resetToken = resetToken),
    )
    val state: StateFlow<ChangePasswordState> = _state.asStateFlow()

    private val _navEvents = Channel<ChangePasswordNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<ChangePasswordNavEvent> = _navEvents.receiveAsFlow()

    fun onEvent(event: ChangePasswordEvent) {
        when (event) {
            is ChangePasswordEvent.NewPasswordChanged -> _state.value = _state.value.copy(newPassword = event.value)
            is ChangePasswordEvent.ConfirmPasswordChanged -> _state.value = _state.value.copy(confirmPassword = event.value)
            ChangePasswordEvent.Submit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.isFormValid) return
        _state.value = current.copy(submit = AsyncValue.Loading)
        viewModelScope.launch {
            val result = authRepository.changePassword(
                email = current.email,
                resetToken = current.resetToken,
                newPassword = current.newPassword,
            )
            _state.value = _state.value.copy(submit = result)
            if (result is AsyncValue.Success) {
                _navEvents.trySend(ChangePasswordNavEvent.NavigateToLoginWithToast)
            }
        }
    }

    private companion object {
        const val ARG_EMAIL = "email"
        const val ARG_TOKEN = "token"
    }
}