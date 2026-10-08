package com.kornerz.feature.auth.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.theme.Satoshi

// ─── Brand tokens (private — only the branded signup screens use these) ───
//
// iOS: AppColor.C68B6CC (AquaLight), AppColor.CF2F4F6 (SmokeLight),
// AppColor.C9E9EAE (PurpleGreyLight). Redeclared privately to bypass
// Material 3 for branded surfaces — same pattern as LoginContent.kt.
private val Aqua = Color(0xFF68B6CC)
private val DisabledBg = Color(0xFFF2F4F6)
private val DisabledFg = Color(0xFF9E9EAE)

// ─── Scaffold ─────────────────────────────────────────────────────────────

/**
 * Shared scaffold for the branded signup wizard screens (steps 1–8).
 * Mirrors every iOS signup view in `kornerz/.../Signup/Views/`:
 *  - full-bleed Aqua `#68B6CC` background
 *  - `bg-clouds-light` decoration (250dp tall, y+60 offset) above the form
 *  - white Satoshi Medium 24sp leading title with 16dp bottom padding
 *  - 32dp outer padding, 16dp inner spacing
 *  - white back chevron in the top-start corner
 *
 * [content] is the per-step body (text fields, grids, CTA row).
 */
@Composable
fun BrandedSignupScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showCloudHeader: Boolean = true,
    content: @Composable () -> Unit,
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
            if (showCloudHeader) {
                androidx.compose.foundation.Image(
                    painter = painterResource(
                        com.kornerz.feature.auth.R.drawable.bg_clouds_light,
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .offset(y = 60.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
                content()
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 4.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .width(36.dp)
                    .height(36.dp)
                    .padding(8.dp),
            )
        }
    }
}

// ─── Text fields ──────────────────────────────────────────────────────────

/**
 * Transparent text field with a 2dp white left vertical bar — the iOS
 * `TransparentTextField` `.white` config (Satoshi Bold 24sp, white
 * text/placeholder, 2pt left bar, clear background).
 */
@Composable
fun BrandedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
) {
    val textColor = Color.White
    val textStyle = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = textColor,
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(36.dp)
                .background(textColor),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            singleLine = true,
            cursorBrush = SolidColor(textColor),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization,
                imeAction = imeAction,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle.copy(color = textColor),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

/**
 * Transparent password field with a 2dp left bar and an inline
 * visibility eye toggle — the iOS `TransparentPasswordField` `.white`
 * config.
 */
@Composable
fun BrandedPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    var visible by remember { mutableStateOf(false) }
    val textColor = Color.White
    val textStyle = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = textColor,
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(36.dp)
                .background(textColor),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 4.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = textStyle,
                singleLine = true,
                cursorBrush = SolidColor(textColor),
                visualTransformation = if (visible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = imeAction,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 32.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle.copy(color = textColor),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            IconButton(
                onClick = { visible = !visible },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(36.dp),
            ) {
                Icon(
                    imageVector = if (visible) Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff,
                    contentDescription = if (visible) "Hide password" else "Show password",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .width(20.dp)
                        .height(20.dp),
                )
            }
        }
    }
}

// ─── Buttons ──────────────────────────────────────────────────────────────

/**
 * Black capsule button — iOS `KornerzButton.capsuleWhiteOnBlack` size
 * `.large` (horizontal=18dp, vertical=12dp, Satoshi Bold 16sp, full
 * width). When [enabled] is false, swaps to `DisabledBg` (`#F2F4F6`)
 * background and `DisabledFg` (`#9E9EAE`) label — matches the iOS
 * `KornerzButtonStyle` disabled palette.
 */
@Composable
fun BlackCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bg = if (enabled) Color.Black else DisabledBg
    val fg = if (enabled) Color.White else DisabledFg
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(percent = 50),
        color = bg,
        onClick = if (enabled) onClick else ({ /* no-op while disabled */ }),
        enabled = enabled,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = 18.dp,
                vertical = 12.dp,
            ),
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
 * Bordered capsule button — iOS `KornerzButton.capsuleBordered`. Renders
 * as a transparent-fill capsule with a 2dp stroke in [color]. When
 * [enabled] is false, swaps to the same disabled palette as
 * [BlackCapsuleButton].
 */
@Composable
fun BorderedCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    enabled: Boolean = true,
) {
    val borderColor = if (enabled) color else DisabledFg
    val textColor = if (enabled) color else DisabledFg
    Surface(
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(percent = 50),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            width = 2.dp,
            color = borderColor,
        ),
        onClick = if (enabled) onClick else ({ /* no-op while disabled */ }),
        enabled = enabled,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = 18.dp,
                vertical = 10.dp,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = textColor,
                fontFamily = Satoshi,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}

/**
 * White-on-black filled capsule, fixed-content width (i.e. shrinks to
 * wrap its text) — used for the country code picker and the "Suggest
 * nickname" button. Mirrors the iOS `capsuleBordered` / `capsuleBlackOnWhite`
 * sizing for compact actions.
 */
@Composable
fun CompactCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bg = if (enabled) Color.Black else DisabledBg
    val fg = if (enabled) Color.White else DisabledFg
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(percent = 50),
        color = bg,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = 18.dp,
                vertical = 10.dp,
            ),
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

// ─── Layout helpers ───────────────────────────────────────────────────────

/**
 * 2-column grid of selectable capsules — visually mirrors the iOS
 * `LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())])`
 * used by `SignupLanguageView` and `SignupKornerzView`. Built on a
 * regular `Column` of `Row`s (not a `LazyVerticalGrid`) so it can be
 * placed inside a `Column(verticalScroll(...))` — a `LazyVerticalGrid`
 * inside a vertical scroll trips the "infinity maximum height
 * constraints" check and crashes the app. The selected item renders
 * as a black-on-white capsule; unselected items render as a
 * white-bordered capsule.
 */
@Composable
fun <T> SelectableCapsuleGrid(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        modifier = modifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowItems.forEach { item ->
                    val selected = isSelected(item)
                    val mod = Modifier.weight(1f)
                    if (selected) {
                        BlackCapsuleButton(
                            text = label(item),
                            onClick = { onToggle(item) },
                            modifier = mod,
                        )
                    } else {
                        BorderedCapsuleButton(
                            text = label(item),
                            onClick = { onToggle(item) },
                            color = Color.White,
                            modifier = mod,
                        )
                    }
                }
                if (rowItems.size < 2) {
                    // Pad odd-length final row so the last cell stays
                    // left-aligned like iOS's `GridItem(.flexible())`.
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ─── Convenience: spacer between content and bottom-anchored CTAs ─────────

@Composable
fun BottomSpacer() {
    Spacer(Modifier.height(8.dp))
}
