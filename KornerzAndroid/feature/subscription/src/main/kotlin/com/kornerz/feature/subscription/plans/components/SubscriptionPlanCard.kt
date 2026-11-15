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
 * The card is an `HStack` in iOS — the plan label on the leading side,
 * the price pushed to the trailing side by a `Spacer` — and this
 * composable mirrors that with [Row]. The monthly card renders one price
 * line; the yearly card renders the price plus a grey per-month-
 * equivalent line beneath it.
 *
 * ## The price is smaller than iOS's
 *
 * iOS renders both the label and the price at 24 pt
 * (`SubscriptionScreen.swift:81` and `:84`), and the grey second line at
 * 20 pt (`:115`). On Android the price is 18 sp and the grey line 14 sp,
 * because the two share one row: the price string carries the billing
 * period (`"$95.90/year"`), which is wider than iOS's, and at 24 sp the
 * price and the 24-sp label overflow the card on a narrow screen. The
 * label keeps the 24 sp and stays the dominant element.
 *
 * ## The title is pinned, not read from Play
 *
 * iOS renders the plan's own label (`monthly` / `yearly`) rather than the
 * StoreKit product's display name (`SubscriptionScreen.swift:80`, `:108`).
 * Play's console title is free-form text — the live listing's is
 * `"subscription app kornerz (Kornerz Social Network)"` — so reading it
 * here overflowed the card and pushed the price out of the row entirely.
 * The card therefore uses [SubscriptionProduct.displayTitle], which is
 * resolved from the pinned [SubscriptionProductType].
 *
 * The price uses [SubscriptionProduct.displayPrice], formatted as
 * `"<price>/<periodUnit>"` to match iOS
 * `SubscriptionViewModel.price(for:)`.
 *
 * **Lives here rather than in `core/ui`** because it embeds
 * subscription-specific vocabulary — the per-month-equivalent line and the
 * trial offer — that no other screen has. `core/ui` stays free of feature
 * vocabulary, as it is today.
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
            text = product.displayTitle,
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KornerzTypography.headlineSmall.fontFamily,
        )
        Column(horizontalAlignment = Alignment.End) {
            // The price is rendered smaller than the plan label, and
            // smaller than iOS's 24-pt, because the Android string is
            // longer: iOS renders "$10.99/month" in Lato, but the price
            // has to share one row with the 24-pt plan label, and at
            // 24 sp the two overflow the card on a narrow screen. 18 sp
            // keeps the row intact while the label still leads.
            Text(
                text = product.displayPrice,
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = KornerzTypography.titleLarge.fontFamily,
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = KornerzTypography.titleMedium.fontFamily,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private val CARD_CORNER_RADIUS = 24.dp
private val SELECTED_STROKE_WIDTH = 2.dp

/** iOS `.padding(.horizontal, 20)`. */
private val CARD_HORIZONTAL_PADDING = 20.dp

/** iOS `.padding(.vertical, 8)`. */
private val CARD_VERTICAL_PADDING = 8.dp

/** iOS `AppColor` grey used for the yearly card's secondary line. */
private val SECONDARY_LINE_COLOR = Color(0xFF75748B)

@Preview(showBackground = true, backgroundColor = 0xFF68B6CC)
@Composable
private fun SubscriptionPlanCardMonthlySelectedPreview() {
    SubscriptionPlanCard(
        product = monthlySample.copy(playProductId = SubscriptionProductType.MONTHLY.playProductId),
        isSelected = true,
        onClick = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF68B6CC)
@Composable
private fun SubscriptionPlanCardYearlySelectedPreview() {
    SubscriptionPlanCard(
        product = yearlySample.copy(playProductId = SubscriptionProductType.YEARLY.playProductId),
        isSelected = true,
        onClick = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF68B6CC)
@Composable
private fun SubscriptionPlanCardYearlyUnselectedPreview() {
    SubscriptionPlanCard(
        product = yearlySample.copy(playProductId = SubscriptionProductType.YEARLY.playProductId),
        isSelected = false,
        onClick = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF68B6CC)
@Composable
private fun SubscriptionPlanCardMonthlyUnselectedPreview() {
    SubscriptionPlanCard(
        product = monthlySample.copy(playProductId = SubscriptionProductType.MONTHLY.playProductId),
        isSelected = false,
        onClick = {},
    )
}

/**
 * The Play console's free-form product title, as the live listing spells
 * it. The card must render the pinned plan label instead, so this preview
 * pins the regression: the title stays "Monthly" no matter what Play
 * reports.
 */
@Preview(showBackground = true, backgroundColor = 0xFF68B6CC)
@Composable
private fun SubscriptionPlanCardPlayTitleIgnoredPreview() {
    SubscriptionPlanCard(
        product = monthlySample.copy(
            title = "subscription app kornerz (Kornerz Social Network)",
        ),
        isSelected = true,
        onClick = {},
    )
}

private val monthlySample = SubscriptionProduct(
    playProductId = SubscriptionProductType.MONTHLY.playProductId,
    subscriptionType = SubscriptionProductType.MONTHLY.subscriptionType,
    title = "Monthly",
    priceLabel = "$10.99",
    periodLabel = "per month",
    pricePerMonthEquivalent = null,
    isTrialEligible = true,
    offerTag = "offer-token-monthly",
)

private val yearlySample = SubscriptionProduct(
    playProductId = SubscriptionProductType.YEARLY.playProductId,
    subscriptionType = SubscriptionProductType.YEARLY.subscriptionType,
    title = "Yearly",
    priceLabel = "$95.90",
    periodLabel = "per year",
    pricePerMonthEquivalent = "$7.99/month",
    isTrialEligible = true,
    offerTag = "offer-token-yearly",
)
