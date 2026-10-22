package com.kornerz.core.model

import kotlinx.serialization.Serializable

/**
 * Compose-free projection of a Play `ProductDetails`, shaped after the
 * iOS `SubscriptionScreen` plan card (`kornerz/Kornerz/InAppPurchase/
 * SubscriptionScreen.swift:74-133`).
 *
 * Built by `feature/subscription`'s `BillingClientApi` — the only type
 * in the app that touches `com.android.billingclient`. No Play type
 * crosses this boundary (see the `play-billing` spec).
 *
 * @param planId the Play product id, equal to the matching
 *   [SubscriptionProductType.rawValue].
 * @param title Play's localized product name ("Monthly" / "Yearly").
 * @param priceLabel Play's localized `formattedPrice`, already carrying
 *   the user's currency symbol and locale formatting.
 * @param periodLabel Play's localized billing period, e.g. "per month".
 * @param pricePerMonthEquivalent the yearly price divided by 12 plus a
 *   localized "/month" suffix — the grey secondary line on the yearly
 *   card. Mirrors iOS `SubscriptionViewModel.mothlyPriceForYearly-
 *   Subscription()`. **Non-null only for [SubscriptionProductType.YEARLY].**
 * @param hasFreeTrialOffer read from Play's `freeTrialOfferDetails` /
 *   `offerDetails`, never hard-coded, so whichever base plan the Play
 *   Console configures with a trial drives the "Try It Free" label.
 * @param offerTag the base-plan offer token to pass to
 *   `launchBillingFlow`, or `null` when the plan has no offer.
 */
@Serializable
data class SubscriptionProduct(
    val planId: String,
    val title: String,
    val priceLabel: String,
    val periodLabel: String,
    val pricePerMonthEquivalent: String? = null,
    val hasFreeTrialOffer: Boolean = false,
    val offerTag: String? = null,
) {
    /** The [SubscriptionProductType] this product belongs to, or `null`
     *  when Play returned an id outside the pinned catalog. */
    val productType: SubscriptionProductType?
        get() = SubscriptionProductType.fromRawValue(planId)

    /** `true` for the yearly plan, which is the only card carrying a
     *  per-month-equivalent second line. */
    val isYearly: Boolean
        get() = productType == SubscriptionProductType.YEARLY
}

/**
 * A resolved catalog entry paired with its plan type. Convenience
 * wrapper so callers can switch on the typed plan without re-resolving
 * [SubscriptionProduct.productType] at every use site.
 */
@Serializable
data class SubscriptionPlan(
    val type: SubscriptionProductType,
    val product: SubscriptionProduct,
) {
    val planId: String get() = product.planId
    val priceLabel: String get() = product.priceLabel
    val periodLabel: String get() = product.periodLabel
    val pricePerMonthEquivalent: String? get() = product.pricePerMonthEquivalent
    val hasFreeTrialOffer: Boolean get() = product.hasFreeTrialOffer
    val offerTag: String? get() = product.offerTag
}