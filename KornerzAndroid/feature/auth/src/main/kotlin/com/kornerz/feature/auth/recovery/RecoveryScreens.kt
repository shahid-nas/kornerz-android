package com.kornerz.feature.auth.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.ui.components.KornerzButton
import com.kornerz.core.ui.components.KornerzOtpTextField
import com.kornerz.core.ui.components.KornerzPasswordField
import com.kornerz.core.ui.components.KornerzTextField
import com.kornerz.core.ui.theme.Spacing

@Composable
fun ForgotPasswordScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is ForgotPasswordNavEvent.NavigateToOtp ->
                    navController.navigate(com.kornerz.feature.auth.AuthRoute.OtpConfirm.build(event.email))
            }
        }
    }
    ForgotPasswordContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
fun ForgotPasswordContent(
    state: ForgotPasswordState,
    onEvent: (ForgotPasswordEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.space8, vertical = Spacing.space16),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Reset password",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(Spacing.space2))
            Text(
                text = "Enter your email and we'll send you a code.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.space12))
            KornerzTextField(
                value = state.email,
                onValueChange = { onEvent(ForgotPasswordEvent.EmailChanged(it)) },
                label = "Email",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
            )
            Spacer(Modifier.height(Spacing.space8))
            val isLoading = state.submit is AsyncValue.Loading
            KornerzButton(
                text = if (isLoading) "Sending…" else "Send code",
                onClick = { onEvent(ForgotPasswordEvent.Submit) },
                enabled = state.isFormValid && !isLoading,
            )
            (state.submit as? AsyncValue.Error)?.let { error ->
                Spacer(Modifier.height(Spacing.space4))
                Text(
                    text = error.throwable.message ?: "Unable to send code",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun OtpConfirmScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: OtpConfirmViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is OtpConfirmNavEvent.NavigateToChangePassword -> navController.navigate(
                    com.kornerz.feature.auth.AuthRoute.ChangePassword.build(event.email, event.resetToken),
                )
            }
        }
    }
    OtpConfirmContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
fun OtpConfirmContent(
    state: OtpConfirmState,
    onEvent: (OtpConfirmEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.space8, vertical = Spacing.space16),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Enter code",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(Spacing.space2))
            Text(
                text = "We sent a 6-digit code to ${state.email}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.space12))
            KornerzOtpTextField(
                value = state.code,
                onValueChange = { onEvent(OtpConfirmEvent.CodeChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                isError = state.submit is AsyncValue.Error,
            )
            Spacer(Modifier.height(Spacing.space8))
            val isLoading = state.submit is AsyncValue.Loading
            KornerzButton(
                text = if (isLoading) "Verifying…" else "Verify code",
                onClick = { onEvent(OtpConfirmEvent.Submit) },
                enabled = state.isFormValid && !isLoading,
            )
            Spacer(Modifier.height(Spacing.space8))
            val cooldown = state.resendCooldownSeconds
            TextButton(
                onClick = { onEvent(OtpConfirmEvent.Resend) },
                enabled = cooldown == 0,
            ) {
                Text(
                    text = if (cooldown == 0) "Resend code" else "Resend in ${cooldown}s",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            (state.submit as? AsyncValue.Error)?.let { error ->
                Spacer(Modifier.height(Spacing.space4))
                Text(
                    text = "Invalid or expired code",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun ChangePasswordScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.navEvents.collect {
            navController.popBackStack(
                route = com.kornerz.feature.auth.AuthRoute.Welcome.route,
                inclusive = false,
            )
        }
    }
    ChangePasswordContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
fun ChangePasswordContent(
    state: ChangePasswordState,
    onEvent: (ChangePasswordEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.space8, vertical = Spacing.space16),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Set a new password",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(Spacing.space12))
            KornerzPasswordField(
                value = state.newPassword,
                onValueChange = { onEvent(ChangePasswordEvent.NewPasswordChanged(it)) },
                label = "New password",
            )
            Spacer(Modifier.height(Spacing.space6))
            KornerzPasswordField(
                value = state.confirmPassword,
                onValueChange = { onEvent(ChangePasswordEvent.ConfirmPasswordChanged(it)) },
                label = "Confirm password",
                isError = state.confirmPassword.isNotEmpty() && state.newPassword != state.confirmPassword,
                errorText = if (state.confirmPassword.isNotEmpty() && state.newPassword != state.confirmPassword) {
                    "Passwords do not match"
                } else null,
            )
            Spacer(Modifier.height(Spacing.space8))
            val isLoading = state.submit is AsyncValue.Loading
            KornerzButton(
                text = if (isLoading) "Saving…" else "Save password",
                onClick = { onEvent(ChangePasswordEvent.Submit) },
                enabled = state.isFormValid && !isLoading,
            )
            (state.submit as? AsyncValue.Error)?.let { error ->
                Spacer(Modifier.height(Spacing.space4))
                Text(
                    text = error.throwable.message ?: "Unable to save password",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}