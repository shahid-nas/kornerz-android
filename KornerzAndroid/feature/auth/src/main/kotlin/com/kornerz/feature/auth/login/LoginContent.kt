package com.kornerz.feature.auth.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.AuthException
import com.kornerz.core.network.auth.AuthError
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Satoshi

/**
 * Stateless renderer for the Login screen. Mirrors the iOS
 * `LoginView` (kornerz/Kornerz/SwiftUI/OnBoarding/Login/LoginView.swift)
 * 1:1 so the visual design — Aqua #68B6CC background, `bg-clouds-light`
 * decoration, capsule fields, white-on-black CTA, error banner, and
 * Satoshi typography — matches across platforms.
 *
 * Per the agreed scope, this screen hardcodes its palette and
 * white-on-Aqua foreground, intentionally bypassing Material 3 so the
 * Android Login reads as the same screen as iOS. All other screens
 * continue to consume `MaterialTheme.colorScheme`.
 */
@Composable
fun LoginContent(
    state: LoginState,
    onEvent: (LoginEvent) -> Unit,
    modifier: Modifier = Modifier,
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
                    text = "Log in",
                    color = Color.White,
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )

                val errorMessage = (state.submit as? AsyncValue.Error)?.toReadableMessage()
                if (errorMessage != null) {
                    ErrorBanner(message = errorMessage)
                }

                FieldColumn(label = "Email") {
                    CapsuleTextField(
                        value = state.email,
                        onValueChange = { onEvent(LoginEvent.EmailChanged(it)) },
                        placeholder = "Email",
                        keyboardType = KeyboardType.Email,
                    )
                }

                FieldColumn(label = "Password") {
                    CapsulePasswordField(
                        value = state.password,
                        onValueChange = { onEvent(LoginEvent.PasswordChanged(it)) },
                        placeholder = "Password",
                    )
                }

                ForgotPasswordRow(
                    onClick = { onEvent(LoginEvent.ForgotPassword) },
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
                    CapsuleBlackOnWhiteButton(
                        text = "Log in",
                        onClick = { onEvent(LoginEvent.Submit) },
                        enabled = state.isFormValid,
                    )
                }
            }
        }
    }
}

// ─── Brand tokens (private — only the Login screen uses these) ─────────────

private val Aqua = Color(0xFF68B6CC)
private val Sand = Color(0xFFEBE1D3)
private val PurpleGrey = Color(0xFF9E9EAE)
private val CoralRed = Color(0xFFF50041)

// ─── Composables ──────────────────────────────────────────────────────────

@Composable
private fun FieldColumn(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = Satoshi,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
        )
        content()
    }
}

@Composable
private fun CapsuleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    val textColor = Color.Black
    val textStyle = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = textColor,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(percent = 50),
        color = Sand,
        border = BorderStroke(1.dp, Sand),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            singleLine = true,
            cursorBrush = SolidColor(textColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle.copy(color = PurpleGrey),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun CapsulePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    var visible by remember { mutableStateOf(false) }
    val textColor = Color.Black
    val textStyle = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = textColor,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(percent = 50),
        color = Sand,
        border = BorderStroke(1.dp, Sand),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = textStyle,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
                cursorBrush = SolidColor(textColor),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle.copy(color = PurpleGrey),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable { visible = !visible },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (visible) "Hide password" else "Show password",
                    tint = textColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ForgotPasswordRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        ) {
            Text(
                text = "Forgot password",
                fontFamily = Satoshi,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun CapsuleBlackOnWhiteButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val bg = if (enabled) Color.Black else Color(0xFFF2F4F6)
    val fg = if (enabled) Color.White else PurpleGrey
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

// ─── Error mapping (preserved from previous Android Login) ────────────────

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

// ─── Previews ─────────────────────────────────────────────────────────────

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