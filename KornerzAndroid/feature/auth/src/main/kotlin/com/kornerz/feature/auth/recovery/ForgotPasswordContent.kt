package com.kornerz.feature.auth.recovery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Satoshi

/**
 * Stateless renderer for the Forgot Password screen. Mirrors the iOS
 * `ForgotPasswordScreen`
 * (`kornerz/Kornerz/User/ForgotPassword/ForgotPasswordScreen.swift`) 1:1:
 * Aqua #68B6CC background, `bg-clouds-light` decoration offset below the
 * status bar, a white "Forgot Password?" heading, a transparent email
 * field with a 2dp white leading bar, and a white-on-black capsule
 * "Reset Password" CTA that is disabled until the email is valid.
 *
 * Like the Android Login screen, this screen hardcodes its palette and
 * white-on-Aqua foreground so both platforms read as the same screen.
 */
@Composable
fun ForgotPasswordContent(
    state: ForgotPasswordState,
    onEvent: (ForgotPasswordEvent) -> Unit,
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
                    text = "Forgot Password?",
                    color = Color.White,
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )

                val errorMessage = (state.submit as? AsyncValue.Error)
                    ?.throwable
                    ?.message
                    ?.takeIf { it.isNotBlank() }
                if (errorMessage != null) {
                    RecoveryErrorBanner(message = errorMessage)
                }

                RecoveryTransparentTextField(
                    value = state.email,
                    onValueChange = { onEvent(ForgotPasswordEvent.EmailChanged(it)) },
                    placeholder = "Email",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                )

                if (state.submit is AsyncValue.Loading) {
                    RecoveryLoadingIndicator()
                } else {
                    RecoveryCapsuleWhiteOnBlackButton(
                        text = "Reset Password",
                        onClick = { onEvent(ForgotPasswordEvent.Submit) },
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

/**
 * Mirrors iOS `CloudHeaderView` — the `bg-clouds-light` artwork, 250pt
 * tall, pushed 60pt below the top edge.
 */
@Composable
internal fun RecoveryCloudHeader(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(com.kornerz.feature.auth.R.drawable.bg_clouds_light),
        contentDescription = null,
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .offset(y = 60.dp),
        contentScale = ContentScale.Fit,
    )
}

/** Inline error copy, styled like the iOS Login screen's error banner. */
@Composable
internal fun RecoveryErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = RecoveryCoralRed.copy(alpha = 0.1f),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = RecoveryCoralRed,
            fontFamily = Satoshi,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ForgotPasswordContentPreview() {
    KornerzTheme(darkTheme = false) {
        ForgotPasswordContent(
            state = ForgotPasswordState(email = "user@kornerz.app"),
            onEvent = {},
        )
    }
}
