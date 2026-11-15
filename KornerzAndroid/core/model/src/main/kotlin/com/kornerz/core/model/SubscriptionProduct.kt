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
 * @param playProductId the Play Console product id, equal to the matching
 *   [SubscriptionProductType.playProductId]. Used to query the catalog and
 *   to launch the purchase sheet.
 * @param subscriptionType the canonical cross-platform value written to
 *   `Users/{email}.subscriptionType`, equal to the matching
 *   [SubscriptionProductType.subscriptionType]. **Never** the Play product
 *   id — the two namespaces differ and the Firestore field is shared with
 *   iOS.
 * @param title Play's localized product name ("Monthly" / "Yearly").
 *   **Not** what the plan card renders — the card uses the pinned
 *   [SubscriptionProductType.label] instead, because Play's console title
 *   is free-form text that overflows the card. See
 *   [displayTitle] and [displayPrice].
 * @param priceLabel Play's localized `formattedPrice`, already carrying
 *   the user's currency symbol and locale formatting.
 * @param periodLabel Play's localized billing period, e.g. "per month".
 * @param pricePerMonthEquivalent the yearly price divided by 12 plus a
 *   localized "/month" suffix — the grey secondary line on the yearly
 *   card. Mirrors iOS `SubscriptionViewModel.mothlyPriceForYearly-
 *   Subscription()`. **Non-null only for [SubscriptionProductType.YEARLY].**
 * @param isTrialEligible whether **this user** may start the plan's
 *   free trial — the store's own per-user verdict, never a local timer
 *   and never hard-coded. See [SubscriptionConstants.TRIAL_PERIOD_DAYS]
 *   for why the 7-day figure is deliberately inert.
 * @param offerTag the base-plan offer token to pass to
 *   `launchBillingFlow`, or `null` when the plan has no offer.
 */
@Serializable
data class SubscriptionProduct(
    val playProductId: String,
    val subscriptionType: String,
    val title: String,
    val priceLabel: String,
    val periodLabel: String,
    val pricePerMonthEquivalent: String? = null,
    val isTrialEligible: Boolean = false,
    val offerTag: String? = null,
) {
    /** The [SubscriptionProductType] this product belongs to, or `null`
     *  when Play returned an id outside the pinned catalog. */
    val productType: SubscriptionProductType?
        get() = SubscriptionProductType.fromPlayProductId(playProductId)

    /** `true` for the yearly plan, which is the only card carrying a
     *  per-month-equivalent second line. */
    val isYearly: Boolean
        get() = productType == SubscriptionProductType.YEARLY

    /**
     * The title the plan card renders: the pinned plan label ("Monthly" /
     * "Yearly"), never Play's console product title.
     *
     * iOS pins these labels in the screen itself
     * (`SubscriptionScreen.swift:80` and `:108`), so the rendered card is
     * identical on both platforms regardless of how the Play Console
     * listing is worded. Falls back to Play's title only for a product
     * outside the pinned catalog.
     */
    val displayTitle: String
        get() = productType?.label ?: title

    /**
     * The price the plan card renders, formatted as iOS does:
     * `"<formattedPrice>/<periodUnit>"` — e.g. `"$10.99/month"`.
     *
     * Mirrors iOS `SubscriptionViewModel.price(for:)`
     * (`SubscriptionViewModel.swift:93-110`), which appends the StoreKit
     * subscription-period unit to the localized display price.
     */
    val displayPrice: String
        get() {
            val unit = productType?.periodUnit ?: return priceLabel
            return if (priceLabel.isBlank()) priceLabel else "$priceLabel/$unit"
        }
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
    val playProductId: String get() = product.playProductId
    val priceLabel: String get() = product.priceLabel
    val periodLabel: String get() = product.periodLabel
    val pricePerMonthEquivalent: String? get() = product.pricePerMonthEquivalent
    val isTrialEligible: Boolean get() = product.isTrialEligible
    val offerTag: String? get() = product.offerTag

    /** The pinned plan label the card renders ("Monthly" / "Yearly"). */
    val displayTitle: String get() = product.displayTitle

    /** The card's price, formatted as `"<price>/<periodUnit>"`. */
    val displayPrice: String get() = product.displayPrice
}
