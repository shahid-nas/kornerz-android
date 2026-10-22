package com.kornerz.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.ui.theme.KornerzColors
import com.kornerz.core.ui.theme.KornerzTypography
import com.kornerz.core.ui.theme.Spacing

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
 * Colors come from [KornerzColors] and typography from
 * [KornerzTypography] rather than inline hex, so the capsule tracks the
 * brand palette. `fillMaxWidth` is always on, matching iOS's
 * `width: .infinite` at every call site.
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
) {
    val (containerColor, contentColor) = when (style) {
        KornerzCapsuleButtonStyle.BlackOnWhite ->
            KornerzColors.InkLight to Color.White
        KornerzCapsuleButtonStyle.WhiteOnBlack ->
            Color.White to KornerzColors.InkLight
    }
    // Disabled capsules keep the pairing recognisable but drop to a
    // translucent fill, so a dev build with no Play listing still reads as
    // "subscribe is unavailable" rather than "subscribe is missing".
    val resolvedContainer = if (enabled) containerColor else containerColor.copy(alpha = 0.4f)
    val resolvedContent = if (enabled) contentColor else contentColor.copy(alpha = 0.7f)

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(CAPSULE_HEIGHT),
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        contentPadding = PaddingValues(
            horizontal = Spacing.space8,
            vertical = Spacing.space4,
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
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = KornerzTypography.labelLarge.fontFamily,
                ),
            )
        }
    }
}

private val CAPSULE_HEIGHT = 56.dp

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