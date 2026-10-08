package com.kornerz.feature.auth.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.theme.Satoshi

// ─── Brand tokens (private — only the branded signup screens use these) ───
//
// iOS: AppColor.C68B6CC (AquaLight), AppColor.CF2F4F6 (SmokeLight),
// AppColor.C9E9EAE (PurpleGreyLight). Redeclared privately to bypass
// Material 3 for branded surfaces — same pattern as LoginContent.kt.
internal val Aqua = Color(0xFF68B6CC)
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
 * When [bottomBar] is provided it is pinned to the bottom of the screen
 * — mirrors iOS `VStack { ScrollView { … }; actionView }` so the CTA
 * stays visible even when the content scrolls past the fold.
 */
@Composable
fun BrandedSignupScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showCloudHeader: Boolean = true,
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Aqua),
    ) {
        if (bottomBar == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                BrandedSignupHeader(showCloudHeader)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BrandedSignupTitle(title)
                    content()
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                BrandedSignupHeader(showCloudHeader)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BrandedSignupTitle(title)
                        content()
                    }
                }
                bottomBar()
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

@Composable
private fun BrandedSignupHeader(showCloudHeader: Boolean) {
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
}

@Composable
private fun BrandedSignupTitle(title: String) {
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
    loading: Boolean = false,
) {
    val isActive = enabled && !loading
    val bg = if (isActive) Color.Black else DisabledBg
    val fg = if (isActive) Color.White else DisabledFg
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(percent = 50),
        color = bg,
        onClick = if (isActive) onClick else ({ /* no-op while disabled / loading */ }),
        enabled = isActive,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = 18.dp,
                vertical = 12.dp,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (loading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = fg,
                        strokeWidth = 2.dp,
                    )
                }
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

// ─── Triangle-clipped image + Kornerz square card (iOS `KornerzSquareView`)
//      ──────────────────────────────────────────────────────────────────────

/** Corner which the [TriangleClippedShape] is *cut* toward. The opposing
 *  corner keeps a rounded [cornerRadius]. Mirrors the iOS
 *  `TriangleClippedShapeType` enum (`.topRight` / `.bottomLeft`). */
enum class TriangleType { TopRight, BottomLeft }

/**
 * Triangular clip — a square clipped to a right-triangle whose hypotenuse
 * runs from one corner of the square to the opposite one, with a
 * [cornerRadius] rounding the two included 90° corners. 1:1 port of iOS
 * `TriangleClippedShape` in `kornerz/.../TriangleClippedShape.swift`.
 */
fun triangleClip(type: TriangleType, cornerRadius: Dp): Shape =
    object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            val w = size.width
            val h = size.height
            val r = (cornerRadius.value * density.density)
                .coerceAtMost(minOf(w, h) / 2f)
            val path = Path().apply {
                when (type) {
                    TriangleType.TopRight -> {
                        // Visible region = bottom-left right-triangle.
                        // Right-angle vertex = bottom-left (sharp).
                        // Rounded vertices = top-left and bottom-right.
                        // Cut corner = top-right; hypotenuse (r, 0) → (w, h-r).
                        moveTo(r, 0f)
                        arcTo(
                            rect = Rect(0f, 0f, 2f * r, 2f * r),
                            startAngleDegrees = 270f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        lineTo(0f, h)
                        lineTo(w - r, h)
                        arcTo(
                            rect = Rect(w - 2f * r, h - 2f * r, w, h),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        lineTo(w, r)
                        lineTo(r, 0f)
                        close()
                    }
                    TriangleType.BottomLeft -> {
                        // Visible region = top-right right-triangle.
                        // Right-angle vertex = top-right (sharp).
                        // Rounded vertices = top-left and bottom-right.
                        // Cut corner = bottom-left; hypotenuse (0, r) → (w-r, h).
                        moveTo(r, 0f)
                        lineTo(w, 0f)
                        lineTo(w, h - r)
                        arcTo(
                            rect = Rect(w - 2f * r, h - 2f * r, w, h),
                            startAngleDegrees = 180f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        lineTo(w - r, h)
                        lineTo(0f, r)
                        arcTo(
                            rect = Rect(0f, 0f, 2f * r, 2f * r),
                            startAngleDegrees = 0f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        close()
                    }
                }
            }
            return Outline.Generic(path)
        }
    }

/**
 * Cyclic palette used by the iOS `RandomColorService` — `C68B6CC` (Aqua)
 * is intentionally included even though it matches the screen background
 * so the index math mirrors iOS 1:1. The `index % palette.size` rotation
 * matches `RandomColorService.color(for:)`.
 */
internal val KornerzCardPalette: List<Color> = listOf(
    Color(0xFFE27D5A), // CE27D5A
    Color(0xFFAD598D), // CAD598D
    Color(0xFF86BFA9), // C86BFA9
    Color(0xFF68B6CC), // C68B6CC
    Color(0xFFEBAA4A), // CEBAA4A
)

internal fun kornerzCardColor(index: Int): Color =
    KornerzCardPalette[index.mod(KornerzCardPalette.size)]

/**
 * One kornerz tile in the signup "Pick your Kornerz" grid. 1:1 port of
 * iOS `KornerzSquareView`:
 *  - 135dp square, 16dp corner radius, [background] fill
 *  - 5dp inner padding, then a top-right triangle image clipped with a
 *    13dp corner radius sitting flush against the top-right corner
 *  - bold white Satoshi title anchored to the bottom-leading edge,
 *    width = 2/3 of the tile
 *
 * The image is currently rendered as a neutral gray fill — the bundled
 * `korner_interests.json` catalog does not carry an image URL yet, so
 * this matches the iOS `TriangleImageView` `.background(.gray)` look
 * (and its `WebImage` `ProgressView` placeholder). When images land in
 * the catalog the gray box is a single replacement.
 */
@Composable
fun KornerzSquareCard(
    title: String,
    background: Color,
    modifier: Modifier = Modifier,
    size: Dp = 135.dp,
    cornerRadius: Dp = 16.dp,
    triangle: TriangleType = TriangleType.TopRight,
) {
    val titleWidth = size * 2 / 3
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
                .clip(triangleClip(triangle, cornerRadius = 13.dp))
                .background(Color(0xFFB6B6B6)),
        )
        Text(
            text = title,
            color = Color.White,
            fontFamily = Satoshi,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(titleWidth)
                .padding(8.dp),
        )
    }
}

/**
 * Selection border overlay for [KornerzSquareCard] — a 4dp white
 * `RoundedRectangle` stroke matching the iOS overlay in
 * `SignupKornerzView.kornerzView`. Compose-side the stroke is rendered
 * by the caller wrapping the card, so this is just a thin convenience
 * that applies the stroke to the card's exact footprint.
 */
fun Modifier.kornerzSelectionStroke(
    selected: Boolean,
    cornerRadius: Dp = 16.dp,
): Modifier = this.then(
    if (selected) {
        this.border(
            width = 4.dp,
            color = Color.White,
            shape = RoundedCornerShape(cornerRadius),
        )
    } else {
        this
    },
)
