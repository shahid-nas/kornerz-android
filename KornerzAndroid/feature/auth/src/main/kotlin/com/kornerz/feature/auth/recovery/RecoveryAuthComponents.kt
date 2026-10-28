package com.kornerz.feature.auth.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.theme.Satoshi

/**
 * Shared building blocks for the password-recovery screens, mirroring the
 * iOS recovery flow (`kornerz/Kornerz/User/ForgotPassword`,
 * `.../ConfirmCode`, `.../ChangePassword`).
 *
 * iOS paints every recovery screen on the Aqua `#68B6CC` canvas with a
 * `bg-clouds-light` header, transparent fields carrying a 2dp white
 * leading bar, and capsule CTAs. These primitives are the Android
 * equivalents of `TransparentTextField`, `TransparentPasswordField`,
 * `KornerzButton.capsuleWhiteOnBlack`, `KornerzButton.capsuleBordered`
 * and `ChevronBackButton`.
 */

/** iOS `AppColor.C68B6CC` — the recovery flow's background colour. */
internal val RecoveryAqua = Color(0xFF68B6CC)

/** iOS `AppColor.CF50041` — used for inline error copy. */
internal val RecoveryCoralRed = Color(0xFFF50041)

/** iOS `AppColor.CEBE1D3` — the OTP slot background. */
internal val RecoverySand = Color(0xFFEBE1D3)

/** iOS `AppColor.C9E9EAE` — muted placeholder grey. */
internal val RecoveryPlaceholderGrey = Color(0xFF9E9EAE)

/**
 * Mirrors iOS `ChevronBackButton` in the navigation bar's leading slot.
 * [tint] matches the iOS `color:` argument — white on the Aqua screens.
 */
@Composable
internal fun RecoveryBackChevronButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(start = 8.dp, top = 8.dp)
            .size(40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Mirrors iOS `TransparentTextField` with the `.white` config: a clear
 * background, a 2dp white bar on the leading edge and white text.
 */
@Composable
internal fun RecoveryTransparentTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    val textStyle = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = Color.White,
    )
    Row(
        modifier = modifier
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
                    keyboardType = keyboardType,
                    imeAction = imeAction,
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
 * Mirrors iOS `TransparentPasswordField` with the `.white` config — the
 * transparent field with a 2dp white leading bar plus the trailing
 * visibility (eye) toggle.
 */
@Composable
internal fun RecoveryTransparentPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    var visible by remember { mutableStateOf(false) }
    val textStyle = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = Color.White,
    )
    Row(
        modifier = modifier
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
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = textStyle,
                visualTransformation = if (visible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = imeAction,
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
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { visible = !visible },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (visible) "Hide password" else "Show password",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Mirrors iOS `ProgressView().tint(.white)` — the recovery flow's busy
 * indicator, shown in place of the primary CTA while a request is in
 * flight.
 */
@Composable
internal fun RecoveryLoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Mirrors iOS `KornerzButton.capsuleWhiteOnBlack(_:size:.large)` — a white
 * capsule with a black label, full width.
 */
@Composable
internal fun RecoveryCapsuleWhiteOnBlackButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bg = if (enabled) Color.White else Color(0xFFF2F4F6)
    val fg = if (enabled) Color.Black else RecoveryPlaceholderGrey
    Surface(
        modifier = modifier
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

/**
 * Mirrors iOS `KornerzButton.capsuleBordered(_:color:.black)` — a
 * transparent capsule with a black outline and black label.
 */
@Composable
internal fun RecoveryCapsuleBorderedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val borderColor = if (enabled) Color.Black else RecoveryPlaceholderGrey
    val labelColor = if (enabled) Color.Black else RecoveryPlaceholderGrey
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(percent = 50),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(width = 1.dp, color = borderColor),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = labelColor,
                fontFamily = Satoshi,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}
