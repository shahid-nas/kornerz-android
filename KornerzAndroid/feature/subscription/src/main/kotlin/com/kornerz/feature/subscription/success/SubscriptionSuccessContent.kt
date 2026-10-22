package com.kornerz.feature.subscription.success

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.components.KornerzCapsuleButton
import com.kornerz.core.ui.components.KornerzCapsuleButtonStyle
import com.kornerz.core.ui.theme.KornerzColors
import com.kornerz.core.ui.theme.KornerzTypography

/**
 * The subscription success screen (inventory **11.3**).
 *
 * Reproduces iOS `SubscriptionSuccessScreen.swift` (43 lines): a
 * `#68B6CC` background with white foreground, a 120×120 checkmark-seal
 * glyph (iOS `Image(systemName: "checkmark.seal")`; Android's Material
 * equivalent is `Icons.Filled.Verified`), "Subscription activated
 * successfully" at 26-pt bold, and a full-width white-capsule "Done"
 * button.
 *
 * **No back affordance and no app bar** — iOS hides the navigation back
 * button at `:37`, and the "Done" button is the only exit. This screen is
 * pure presentation and performs **no** Firestore write: the entitlement
 * is granted upstream in the purchase flow, exactly as on iOS.
 */
@Composable
fun SubscriptionSuccessContent(
    onDoneTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KornerzColors.AquaLight)
            // The background extends behind the system bars, matching iOS
            // `SubscriptionScreen`'s full-bleed treatment. Content stays
            // clear of them.
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CONTENT_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(SEAL_SIZE)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = ACTIVATED_DESCRIPTION,
                    tint = Color.White,
                    modifier = Modifier.size(SEAL_SIZE),
                )
            }

            Spacer(Modifier.height(TEXT_SPACER))

            Text(
                // iOS `subscription_activated_msg`
                text = ACTIVATED_MESSAGE,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = KornerzTypography.headlineMedium.fontFamily,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(BUTTON_SPACER))

            KornerzCapsuleButton(
                // iOS `done`
                text = DONE,
                onClick = onDoneTap,
                style = KornerzCapsuleButtonStyle.WhiteOnBlack,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionSuccessContentPreview() {
    SubscriptionSuccessContent(onDoneTap = {})
}

/**
 * The `en-rXA` pseudo-locale mirrors every string and flips layout
 * direction, which is how the RTL-safety claim (`docs/phases.md`
 * Principle 7) is checked for this screen. The layout is fully centred
 * with `horizontal` padding only, so there is no start/end dependency to
 * mirror.
 */
@Preview(showBackground = true, locale = "en-rXA")
@Composable
private fun SubscriptionSuccessContentRtlPreview() {
    SubscriptionSuccessContent(onDoneTap = {})
}

private val SEAL_SIZE = 120.dp
private val CONTENT_PADDING = 24.dp
private val TEXT_SPACER = 24.dp
private val BUTTON_SPACER = 40.dp

/** iOS `subscription_activated_msg`. */
private const val ACTIVATED_MESSAGE = "Subscription activated successfully"

/** iOS `done`. */
private const val DONE = "Done"

private const val ACTIVATED_DESCRIPTION = "Subscription activated"