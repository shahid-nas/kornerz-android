package com.kornerz.feature.auth.login

import com.kornerz.core.model.AsyncValue

/** UI state for [LoginScreen]. */
data class LoginState(
    val email: String = "",
    val password: String = "",
    val submit: AsyncValue<Unit> = AsyncValue.Loading,
) {
    val isFormValid: Boolean
        get() = email.contains("@") && password.isNotEmpty()
}

/** User actions on the login form. */
sealed interface LoginEvent {

    data class EmailChanged(val value: String) : LoginEvent

    data class PasswordChanged(val value: String) : LoginEvent

    data object Submit : LoginEvent

    data object CreateAccount : LoginEvent

    data object ForgotPassword : LoginEvent
}

/** Navigation events emitted over `Channel<NavEvent>` (architecture §2.4). */
sealed interface LoginNavEvent {

    data object NavigateToShell : LoginNavEvent

    data object NavigateToSignup : LoginNavEvent

    data object NavigateToForgotPassword : LoginNavEvent
}