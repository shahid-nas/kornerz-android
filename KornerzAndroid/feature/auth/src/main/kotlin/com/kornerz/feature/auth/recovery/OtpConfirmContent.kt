package com.kornerz.feature.auth.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Satoshi

/**
 * Stateless renderer for the "Enter code" step. Mirrors the iOS
 * `ConfirmCodeScreen`
 * (`kornerz/Kornerz/User/ConfirmCode/ConfirmCodeScreen.swift`): the same
 * Aqua canvas and cloud header as the Forgot Password screen, a white
 * 24sp instruction line, square OTP slots filled with Sand at 70%
 * opacity carrying bold white 24sp digits, a red inline error, a
 * white-on-black "Confirm Code" capsule and a black-outlined "Resend
 * code" capsule.
 *
 * iOS asks for a 4-digit code; the Android backend issues a 6-digit one,
 * so the instruction line and the slot count follow the Android contract
 * while every visual detail matches iOS.
 */
@Composable
fun OtpConfirmContent(
    state: OtpConfirmState,
    onEvent: (OtpConfirmEvent) -> Unit,
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
                    text = "Please enter the $OTP_LENGTH-digit code sent to your email address.",
                    color = Color.White,
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )

                RecoveryOtpSlots(
                    value = state.code,
                    onValueChange = { onEvent(OtpConfirmEvent.CodeChanged(it)) },
                    length = OTP_LENGTH,
                )

                (state.submit as? AsyncValue.Error)?.let { error ->
                    Text(
                        text = error.throwable.message ?: "Invalid or expired code",
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
                        text = "Confirm Code",
                        onClick = { onEvent(OtpConfirmEvent.Submit) },
                        enabled = state.isFormValid,
                    )
                }

                val cooldown = state.resendCooldownSeconds
                RecoveryCapsuleBorderedButton(
                    text = if (cooldown == 0) "Resend code" else "Resend code ($cooldown s)",
                    onClick = { onEvent(OtpConfirmEvent.Resend) },
                    enabled = cooldown == 0,
                )
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

private const val OTP_LENGTH = 6

/**
 * Mirrors iOS `AEOTPView`: square slots (no corner radius, no border)
 * filled with Sand at 70% opacity, holding bold white 24sp digits.
 */
@Composable
private fun RecoveryOtpSlots(
    value: String,
    onValueChange: (String) -> Unit,
    length: Int,
) {
    BasicTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(input.filter { it.isDigit() }.take(length))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                repeat(length) { index ->
                    val digit = value.getOrNull(index)?.toString().orEmpty()
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(60.dp)
                            .background(RecoverySand.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = digit,
                            color = Color.White,
                            fontFamily = Satoshi,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            innerTextField()
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun OtpConfirmContentPreview() {
    KornerzTheme(darkTheme = false) {
        OtpConfirmContent(
            state = OtpConfirmState(email = "user@kornerz.app", code = "123456"),
            onEvent = {},
        )
    }
}
