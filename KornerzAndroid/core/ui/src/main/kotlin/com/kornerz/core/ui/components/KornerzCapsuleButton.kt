package com.kornerz.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.theme.KornerzColors
import com.kornerz.core.ui.theme.KornerzTypography

/**
 * The capsule variant of the Kornerz brand button. Mirrors iOS
 * `KornerzButton.capsuleWhiteOnBlack` (`KornerzButton.swift:60-84`,
 * `:263-277`) and `.capsuleBlackOnWhite` (`:279-294`), which the P8
 * subscription screens need for their full-width primary actions.
 *
 * **Why this is a new primitive rather than a `KornerzButton` style:**
 * `KornerzButton` hardcodes its height to 52 dp, its shape to
 * `KornerzShapes.large` (16 dp), and its filled variant to the theme
 * primary color — all three are relied on by every P0–P7 screen.
 * Retrofitting them would change the button used across the app; adding a
 * sibling with capsule geometry and explicit foreground/background pairs
 * keeps that blast radius at zero.
 *
 * ## Typography
 *
 * The label is **Satoshi 16 bold**, matching iOS's `.large` button size:
 * `KornerzButtonSize.large` carries `fontSize = 16`
 * (`KornerzButton.swift:35-44`) and the capsule content renders
 * `Text(title).font(.satoshi(size.fontSize, weight: .bold))`
 * (`:146-149`). It is deliberately **not** `typography.labelLarge`
 * (Satoshi 14 medium) — that role is smaller and lighter than iOS's
 * button label, which made the plans screen's "Try It Free" CTA read
 * visibly smaller than the copy above it.
 *
 * ## Geometry
 *
 * iOS's `.large` capsule pads its label by `EdgeInsets(h: 18, v: 12)`
 * (`:24-33`) and sizes itself to its content — a 16-pt line plus 24-pt
 * of vertical padding, roughly 40 pt tall. The Android twin keeps the
 * intrinsic sizing (vertical padding only, no fixed height) so the
 * capsule's height follows its label exactly as iOS's does.
 *
 * Colors come from [KornerzColors] and typography from
 * [KornerzTypography] rather than inline hex, so the capsule tracks the
 * brand palette. `fillMaxWidth` is always on, matching iOS's
 * `width: .infinite` at every call site.
 *
 * [containerColor] / [contentColor] override the style's pair — the
 * Korner detail's About sheet paints its Follow action in the brand
 * purple, which is iOS' `.capsuleStyle(.primary)`.
 *
 * Modifier is the last parameter (architecture §4).
 */
enum class KornerzCapsuleButtonStyle {
    /** Black capsule, white label. The plans screen's subscribe CTA. */
    BlackOnWhite,

    /** White capsule, black label. The success screen's "Done" CTA. */
    WhiteOnBlack,
}

@Composable
fun KornerzCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KornerzCapsuleButtonStyle = KornerzCapsuleButtonStyle.BlackOnWhite,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    val (styleContainer, styleContent) = when (style) {
        KornerzCapsuleButtonStyle.BlackOnWhite ->
            KornerzColors.InkLight to Color.White
        KornerzCapsuleButtonStyle.WhiteOnBlack ->
            Color.White to KornerzColors.InkLight
    }
    // An explicit pair wins over the style's pair — the Korner detail's
    // About sheet paints the button in the brand purple, which is iOS'
    // `.capsuleStyle(.primary)`.
    val container = containerColor ?: styleContainer
    val content = contentColor ?: styleContent
    // Disabled capsules keep the pairing recognisable but drop to a
    // translucent fill, so a dev build with no Play listing still reads as
    // "subscribe is unavailable" rather than "subscribe is missing".
    val resolvedContainer = if (enabled) container else container.copy(alpha = 0.4f)
    val resolvedContent = if (enabled) content else content.copy(alpha = 0.7f)

    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        // iOS `KornerzButtonSize.large.padding` is `EdgeInsets(h: 18, v: 12)`
        // (`KornerzButton.swift:24-33`). The vertical padding reproduces
        // the capsule's intrinsic height; the horizontal padding is
        // generous but never clips a label, since the capsule grows with
        // its content.
        contentPadding = PaddingValues(
            horizontal = CAPSULE_HORIZONTAL_PADDING,
            vertical = CAPSULE_VERTICAL_PADDING,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = resolvedContainer,
            contentColor = resolvedContent,
            disabledContainerColor = resolvedContainer,
            disabledContentColor = resolvedContent,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                // iOS `.satoshi(size.fontSize, weight: .bold)` for the
                // `.large` size — Satoshi 16 bold
                // (`KornerzButton.swift:35-44`, `:146-149`).
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = KornerzTypography.labelLarge.fontFamily,
                    fontSize = CAPSULE_LABEL_SIZE,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

/** iOS `KornerzButtonSize.large.fontSize`. */
private val CAPSULE_LABEL_SIZE = 16.sp

/** iOS `KornerzButtonSize.large.padding` horizontal inset (18 pt). */
private val CAPSULE_HORIZONTAL_PADDING = 18.dp

/** iOS `KornerzButtonSize.large.padding` vertical inset (12 pt). */
private val CAPSULE_VERTICAL_PADDING = 12.dp

@Preview(showBackground = true)
@Composable
private fun KornerzCapsuleButtonBlackOnWhitePreview() {
    KornerzCapsuleButton(
        text = "Try It Free",
        onClick = {},
        style = KornerzCapsuleButtonStyle.BlackOnWhite,
    )
}

@Preview(showBackground = true)
@Composable
private fun KornerzCapsuleButtonWhiteOnBlackPreview() {
    KornerzCapsuleButton(
        text = "Done",
        onClick = {},
        style = KornerzCapsuleButtonStyle.WhiteOnBlack,
    )
}

@Preview(showBackground = true)
@Composable
private fun KornerzCapsuleButtonDisabledPreview() {
    KornerzCapsuleButton(
        text = "Subscribe",
        onClick = {},
        enabled = false,
    )
}