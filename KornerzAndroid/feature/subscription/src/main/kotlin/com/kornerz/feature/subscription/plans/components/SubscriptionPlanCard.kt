package com.kornerz.feature.subscription.plans.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.kornerz.core.model.SubscriptionProduct
import com.kornerz.core.model.SubscriptionProductType
import com.kornerz.core.ui.theme.KornerzTypography

/**
 * One selectable plan card. Reproduces iOS `SubscriptionScreen.swift`
 * `:74-133`: a white `RoundedRectangle(cornerRadius: 24)` with a black
 * foreground, stroked with a 2-pt black outline when selected and
 * unstroked when not.
 *
 * **Lives here rather than in `core/ui`** because it embeds
 * subscription-specific vocabulary — the per-month-equivalent line and the
 * trial offer — that no other screen has. `core/ui` stays free of feature
 * vocabulary, as it is today.
 *
 * The Monthly card renders one price line; the Yearly card renders the
 * price plus a grey 20-pt per-month-equivalent line beneath it. No
 * "save X %" badge is rendered — neither exists in the live iOS screen.
 */
@Composable
fun SubscriptionPlanCard(
    product: SubscriptionProduct,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CARD_CORNER_RADIUS))
            .background(Color.White)
            .then(
                if (isSelected) Modifier.border(
                    width = SELECTED_STROKE_WIDTH,
                    color = Color.Black,
                    shape = RoundedCornerShape(CARD_CORNER_RADIUS),
                ) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = CARD_HORIZONTAL_PADDING, vertical = CARD_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = product.title,
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KornerzTypography.headlineSmall.fontFamily,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = product.priceLabel,
                color = Color.Black,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = KornerzTypography.headlineSmall.fontFamily,
                textAlign = TextAlign.End,
            )
            // The grey per-month-equivalent line renders only on the yearly
            // card, because `pricePerMonthEquivalent` is non-null only for
            // SubscriptionProductType.YEARLY.
            product.pricePerMonthEquivalent?.let { equivalent ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = equivalent,
                    color = SECONDARY_LINE_COLOR,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = KornerzTypography.titleLarge.fontFamily,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private val CARD_CORNER_RADIUS = 24.dp
private val SELECTED_STROKE_WIDTH = 2.dp
private val CARD_HORIZONTAL_PADDING = 20.dp
private val CARD_VERTICAL_PADDING = 20.dp

/** iOS `AppColor` grey used for the yearly card's secondary line. */
private val SECONDARY_LINE_COLOR = Color(0xFF75748B)

@Preview(showBackground = true, backgroundColor = 0xFF68B6CC)
@Composable
private fun SubscriptionPlanCardMonthlySelectedPreview() {
    SubscriptionPlanCard(
        product = monthlySample.copy(planId = SubscriptionProductType.MONTHLY.rawValue),
        isSelected = true,
        onClick = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF68B6CC)
@Composable
private fun SubscriptionPlanCardYearlySelectedPreview() {
    SubscriptionPlanCard(
        product = yearlySample.copy(planId = SubscriptionProductType.YEARLY.rawValue),
        isSelected = true,
        onClick = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF68B6CC)
@Composable
private fun SubscriptionPlanCardYearlyUnselectedPreview() {
    SubscriptionPlanCard(
        product = yearlySample.copy(planId = SubscriptionProductType.YEARLY.rawValue),
        isSelected = false,
        onClick = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF68B6CC)
@Composable
private fun SubscriptionPlanCardMonthlyUnselectedPreview() {
    SubscriptionPlanCard(
        product = monthlySample.copy(planId = SubscriptionProductType.MONTHLY.rawValue),
        isSelected = false,
        onClick = {},
    )
}

private val monthlySample = SubscriptionProduct(
    planId = SubscriptionProductType.MONTHLY.rawValue,
    title = "Monthly",
    priceLabel = "$10.99",
    periodLabel = "per month",
    pricePerMonthEquivalent = null,
    hasFreeTrialOffer = true,
    offerTag = "offer-token-monthly",
)

private val yearlySample = SubscriptionProduct(
    planId = SubscriptionProductType.YEARLY.rawValue,
    title = "Yearly",
    priceLabel = "$95.90",
    periodLabel = "per year",
    pricePerMonthEquivalent = "$7.99/month",
    hasFreeTrialOffer = true,
    offerTag = "offer-token-yearly",
)
