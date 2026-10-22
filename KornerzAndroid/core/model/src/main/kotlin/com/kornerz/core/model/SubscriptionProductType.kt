package com.kornerz.core.model

/**
 * The two subscription products in the Kornerz catalog. Mirrors iOS
 * `InAppManager.InAppProduct` (`kornerz/Kornerz/InAppPurchase/
 * InAppManager.swift:16-19`) byte-for-byte.
 *
 * [rawValue] is the Play Console product id **and** the exact string
 * written to `Users/{email}.subscriptionType`. Because both platforms
 * use the same two raw values, a user who subscribed on iOS reads as
 * premium on Android and vice versa (see the `premium-entitlement`
 * spec § "Cross-platform field compatibility").
 *
 * Compose-free by design: this lives in `core/model`, which has no
 * Compose dependency.
 */
enum class SubscriptionProductType(val rawValue: String) {

    /** iOS `InAppProduct.monthly`; Play product id `video_call_monthly`. */
    MONTHLY("video_call_monthly"),

    /** iOS `InAppProduct.yearly`; Play product id `video_call_yearly_sub`. */
    YEARLY("video_call_yearly_sub"),
    ;

    companion object {

        /** The full catalog, in the order the plans screen renders it. */
        val ALL: List<SubscriptionProductType> = listOf(MONTHLY, YEARLY)

        /**
         * Resolves a stored `subscriptionType` string back to a plan.
         * Returns `null` for a missing or unrecognized value so a
         * document written by another client degrades to the expiry
         * fallback rather than throwing.
         */
        fun fromRawValue(rawValue: String?): SubscriptionProductType? =
            entries.firstOrNull { it.rawValue == rawValue }
    }
}