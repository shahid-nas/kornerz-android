package com.kornerz.feature.auth.recovery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
            .background(Aqua),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Image(
                painter = painterResource(com.kornerz.feature.auth.R.drawable.bg_clouds_light),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .offset(y = 60.dp),
                contentScale = ContentScale.Fit,
            )
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
                    ErrorBanner(message = errorMessage)
                }

                TransparentEmailField(
                    value = state.email,
                    onValueChange = { onEvent(ForgotPasswordEvent.EmailChanged(it)) },
                    placeholder = "Email",
                )

                val isLoading = state.submit is AsyncValue.Loading
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else {
                    CapsuleWhiteOnBlackButton(
                        text = "Reset Password",
                        onClick = { onEvent(ForgotPasswordEvent.Submit) },
                        enabled = state.isFormValid,
                    )
                }
            }
        }

        if (onBack != null) {
            BackChevronButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

// ─── Brand tokens (private — mirrors iOS `AppColor` / `Config.swift`) ────

private val Aqua = Color(0xFF68B6CC)
private val CoralRed = Color(0xFFF50041)

// ─── Composables ──────────────────────────────────────────────────────────

/**
 * Mirrors iOS `TransparentTextField` with the `.white` config: a clear
 * background, a 2dp white bar on the leading edge, and white text.
 */
@Composable
private fun TransparentEmailField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val textStyle = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = Color.White,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(56.dp)
                .background(Color.White),
        )
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = textStyle,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

/**
 * Mirrors iOS `KornerzButton.capsuleWhiteOnBlack(_:size:.large)` — a white
 * capsule with a black label, full width.
 */
@Composable
private fun CapsuleWhiteOnBlackButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val bg = if (enabled) Color.White else Color(0xFFF2F4F6)
    val fg = if (enabled) Color.Black else Color(0xFF9E9EAE)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(percent = 50),
        color = bg,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = fg,
                fontFamily = Satoshi,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}

/** Mirrors iOS `ChevronBackButton` in the navigation bar's leading slot. */
@Composable
private fun BackChevronButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(start = 8.dp, top = 8.dp)
            .size(40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = CoralRed.copy(alpha = 0.1f),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = CoralRed,
            fontFamily = Satoshi,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
        )
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────

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
