package com.kornerz.feature.auth.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.AuthException
import com.kornerz.core.network.auth.AuthError
import com.kornerz.core.ui.components.KornerzButton
import com.kornerz.core.ui.components.KornerzPasswordField
import com.kornerz.core.ui.components.KornerzTextField
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * Stateless renderer for the Login form (architecture §4). The screen
 * Composable is the stateful host; this function takes a [LoginState]
 * and an [onEvent] callback and renders the UI accordingly. Safe to
 * preview and unit-test.
 */
@Composable
fun LoginContent(
    state: LoginState,
    onEvent: (LoginEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.space8),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Spacing.space2))
            Text(
                text = "Log in to your Kornerz account",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.space12))

            KornerzTextField(
                value = state.email,
                onValueChange = { onEvent(LoginEvent.EmailChanged(it)) },
                label = "Email",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
            )
            Spacer(Modifier.height(Spacing.space6))
            KornerzPasswordField(
                value = state.password,
                onValueChange = { onEvent(LoginEvent.PasswordChanged(it)) },
                label = "Password",
            )

            val isLoading = state.submit is AsyncValue.Loading
            val errorMessage = (state.submit as? AsyncValue.Error)?.let { it.toReadableMessage() }

            Spacer(Modifier.height(Spacing.space8))
            KornerzButton(
                text = if (isLoading) "Signing in…" else "Log in",
                onClick = { onEvent(LoginEvent.Submit) },
                enabled = state.isFormValid && !isLoading,
            )
            if (errorMessage != null) {
                Spacer(Modifier.height(Spacing.space4))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(Spacing.space12))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { onEvent(LoginEvent.ForgotPassword) }) {
                    Text("Forgot password?", color = MaterialTheme.colorScheme.primary)
                }
            }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { onEvent(LoginEvent.CreateAccount) }) {
                    Text("Create account", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun AsyncValue.Error.toReadableMessage(): String {
    val authKind = (throwable as? AuthException)?.errorKind
    return when (authKind ?: AuthError.Unknown(throwable.message ?: "")) {
        AuthError.InvalidCredentials -> "Email or password is incorrect"
        AuthError.NetworkError -> "Connection error. Please try again."
        AuthError.SessionExpired -> "Your session has expired. Please log in again."
        AuthError.EmailAlreadyInUse -> "This email is already registered"
        AuthError.InvalidCode -> "Invalid code"
        is AuthError.Unknown -> throwable.message ?: "Something went wrong"
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginContentPreview() {
    KornerzTheme(darkTheme = false) {
        LoginContent(state = LoginState(email = "user@kornerz.app", password = "password"), onEvent = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginContentDarkPreview() {
    KornerzTheme(darkTheme = true) {
        LoginContent(state = LoginState(email = "user@kornerz.app", password = "password"), onEvent = {})
    }
}