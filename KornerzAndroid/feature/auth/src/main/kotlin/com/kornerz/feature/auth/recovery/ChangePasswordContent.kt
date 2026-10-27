package com.kornerz.feature.auth.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Satoshi

/**
 * Stateless renderer for the "Set a new password" step. Mirrors the iOS
 * `ChangePasswordScreen`
 * (`kornerz/Kornerz/User/ChangePassword/ChangePasswordScreen.swift`): the
 * Aqua canvas with the cloud header, a white 24sp instruction line, two
 * transparent password fields with a 2dp white leading bar and a trailing
 * visibility toggle, and a white-on-black "Change Password" capsule that
 * is disabled until both entries match and satisfy the length rule.
 */
@Composable
fun ChangePasswordContent(
    state: ChangePasswordState,
    onEvent: (ChangePasswordEvent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RecoveryAqua),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            RecoveryCloudHeader()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Create a new password with at least 6 characters",
                    color = Color.White,
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )

                RecoveryTransparentPasswordField(
                    value = state.newPassword,
                    onValueChange = { onEvent(ChangePasswordEvent.NewPasswordChanged(it)) },
                    placeholder = "New Password",
                )

                RecoveryTransparentPasswordField(
                    value = state.confirmPassword,
                    onValueChange = { onEvent(ChangePasswordEvent.ConfirmPasswordChanged(it)) },
                    placeholder = "Confirm Password",
                    imeAction = ImeAction.Done,
                )

                (state.submit as? AsyncValue.Error)?.let { error ->
                    Text(
                        text = error.throwable.message ?: "Unable to save password",
                        color = RecoveryCoralRed,
                        fontFamily = Satoshi,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                    )
                }

                if (state.submit is AsyncValue.Loading) {
                    RecoveryLoadingIndicator()
                } else {
                    RecoveryCapsuleWhiteOnBlackButton(
                        text = "Change Password",
                        onClick = { onEvent(ChangePasswordEvent.Submit) },
                        enabled = state.isFormValid,
                    )
                }
            }
        }

        if (onBack != null) {
            RecoveryBackChevronButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChangePasswordContentPreview() {
    KornerzTheme(darkTheme = false) {
        ChangePasswordContent(
            state = ChangePasswordState(newPassword = "secret12", confirmPassword = "secret12"),
            onEvent = {},
        )
    }
}
