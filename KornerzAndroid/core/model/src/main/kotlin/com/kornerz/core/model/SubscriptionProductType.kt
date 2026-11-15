package com.kornerz.core.model

/**
 * The two subscription products in the Kornerz catalog.
 *
 * Each entry carries **two** identifiers, because they serve two different
 * contracts and the values are not interchangeable:
 *
 * | Property | Purpose | Values |
 * |---|---|---|
 * | [playProductId] | The Google Play Console product id. Used to query the catalog and to launch the purchase sheet. | `kornerz_subscription`, `kornerz_subscription_yearly` |
 * | [subscriptionType] | The canonical, cross-platform value written to `Users/{email}.subscriptionType`. | `video_call_monthly`, `video_call_yearly_sub` |
 *
 * The two diverge because Play's product ids are set by the Play Console
 * listing, while `subscriptionType` is a **shared document field**: iOS
 * writes it in `UserRepository.setUserPremium`
 * (`kornerz/Kornerz/Backend/Repositories/UserRepository.swift:174`) from
 * `InAppProduct.rawValue`, and the `premium-entitlement` spec requires both
 * platforms to agree on it so a subscriber who bought on one platform is
 * readable on the other.
 *
 * Writing the Play product id into `subscriptionType` would silently
 * break that contract: an Android purchase would store
 * `kornerz_subscription`, which is not a value iOS knows how to interpret.
 * The Play product id therefore never leaves the billing layer.
 *
 * iOS's `InAppProduct` (`kornerz/Kornerz/InAppPurchase/InAppManager.swift:16-19`)
 * remains the source of truth for [subscriptionType]; [playProductId] is
 * Android-only and has no iOS counterpart, because StoreKit product ids
 * and Play product ids are independent namespaces.
 *
 * Compose-free by design: this lives in `core/model`, which has no
 * Compose dependency.
 */
enum class SubscriptionProductType(
    val playProductId: String,
    val subscriptionType: String,
    /**
     * The card's title, pinned to the plan rather than read from Play.
     *
     * iOS renders the plan's own label — `monthly` / `yearly`
     * (`kornerz/Kornerz/InAppPurchase/SubscriptionScreen.swift:80` and
     * `:108`) — never the StoreKit product's display name. Play's product
     * title is free-form console text (the live listing's title is
     * `"subscription app kornerz (Kornerz Social Network)"`), so using it
     * here overflows the card and pushes the price off the row. The label
     * therefore comes from the pinned catalog, which is the same value on
     * every platform and in every locale.
     */
    val label: String,
    /**
     * The billing-period unit appended to the card's price, matching iOS
     * `SubscriptionViewModel.price(for:)` (`SubscriptionViewModel.swift:93-110`),
     * which renders `<displayPrice>/<unit>`.
     */
    val periodUnit: String,
) {

    /** Monthly plan. Play id `kornerz_subscription`; iOS `InAppProduct.monthly`. */
    MONTHLY(
        playProductId = "kornerz_subscription",
        subscriptionType = "video_call_monthly",
        label = "Monthly",
        periodUnit = "month",
    ),

    /** Yearly plan. Play id `kornerz_subscription_yearly`; iOS `InAppProduct.yearly`. */
    YEARLY(
        playProductId = "kornerz_subscription_yearly",
        subscriptionType = "video_call_yearly_sub",
        label = "Yearly",
        periodUnit = "year",
    ),
    ;

    companion object {

        /** The full catalog, in the order the plans screen renders it. */
        val ALL: List<SubscriptionProductType> = listOf(MONTHLY, YEARLY)

        /**
         * Resolves a **Play product id** back to a plan.
         *
         * Returns `null` for a missing or unrecognized id so a product
         * outside the pinned catalog degrades rather than throwing.
         */
        fun fromPlayProductId(playProductId: String?): SubscriptionProductType? =
            entries.firstOrNull { it.playProductId == playProductId }

        /**
         * Resolves a stored `subscriptionType` — the cross-platform Firestore
         * value — back to a plan.
         *
         * Returns `null` for a missing or unrecognized value so a document
         * written by the other client degrades to the expiry fallback rather
         * than throwing.
         */
        fun fromSubscriptionType(subscriptionType: String?): SubscriptionProductType? =
            entries.firstOrNull { it.subscriptionType == subscriptionType }
    }
}
