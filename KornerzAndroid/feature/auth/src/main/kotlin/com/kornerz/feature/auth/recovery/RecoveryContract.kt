package com.kornerz.feature.auth.recovery

import com.kornerz.core.model.AsyncValue

data class ForgotPasswordState(
    val email: String = "",
    // Idle until the user submits; `Loading` is only set while the
    // reset request is in flight (iOS shows the spinner only then).
    val submit: AsyncValue<Unit> = AsyncValue.Success(Unit),
) {
    val isFormValid: Boolean
        get() = email.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"))
}

sealed interface ForgotPasswordEvent {
    data class EmailChanged(val value: String) : ForgotPasswordEvent
    data object Submit : ForgotPasswordEvent
}

sealed interface ForgotPasswordNavEvent {
    data class NavigateToOtp(val email: String) : ForgotPasswordNavEvent
}

data class OtpConfirmState(
    val email: String = "",
    val code: String = "",
    val submit: AsyncValue<Unit> = AsyncValue.Loading,
    val resendCooldownSeconds: Int = 0,
) {
    val isFormValid: Boolean get() = code.length == 6
}

sealed interface OtpConfirmEvent {
    data class CodeChanged(val value: String) : OtpConfirmEvent
    data object Submit : OtpConfirmEvent
    data object Resend : OtpConfirmEvent
}

sealed interface OtpConfirmNavEvent {
    data class NavigateToChangePassword(val email: String, val resetToken: String) : OtpConfirmNavEvent
}

data class ChangePasswordState(
    val email: String = "",
    val resetToken: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val submit: AsyncValue<Unit> = AsyncValue.Loading,
) {
    val isFormValid: Boolean
        get() = newPassword.length >= 6 && newPassword == confirmPassword
}

sealed interface ChangePasswordEvent {
    data class NewPasswordChanged(val value: String) : ChangePasswordEvent
    data class ConfirmPasswordChanged(val value: String) : ChangePasswordEvent
    data object Submit : ChangePasswordEvent
}

sealed interface ChangePasswordNavEvent {
    data object NavigateToLoginWithToast : ChangePasswordNavEvent
}