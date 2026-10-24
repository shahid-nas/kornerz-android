package com.kornerz.feature.subscription.data.billing

import com.kornerz.core.model.SubscriptionProduct
import com.kornerz.core.model.SubscriptionProductType

/**
 * The two-plan catalog the app renders when Google Play is not reachable.
 *
 * It has two consumers:
 *
 * - The `dev` flavor's [com.kornerz.feature.subscription.data.billing.dev.DevBillingGateway],
 *   which simulates the whole purchase flow against it.
 * - The `prod` flavor's [BillingClientApi] while [com.kornerz.core.model.IAP_ENABLED]
 *   is `false` — i.e. while the app is not on the Play Store, so the Play
 *   catalog query would fail with `queryProductDetails failed: ...`.
 *
 * The pricing mirrors iOS `Kornerz-SKConfig.storekit` (monthly `10.99`,
 * yearly `95.9`) and the per-month equivalent derived the same way
 * `SubscriptionViewModel.mothlyPriceForYearlySubscription()` derives it
 * (`SubscriptionViewModel.swift:81-91`).
 *
 * `hasFreeTrialOffer` is `true` for **both** plans because
 * `Kornerz-SKConfig.storekit:185-200` gives both products a `P1W`
 * introductory offer. On `prod` with IAP enabled the flag is read from
 * Play's own pricing phases instead, so whichever plan the Play Console
 * configures with a trial drives the "Try It Free" label — no code change
 * either way.
 */
internal object OfflineCatalog {

    fun all(): List<SubscriptionProduct> = listOf(
        SubscriptionProduct(
            planId = SubscriptionProductType.MONTHLY.rawValue,
            title = "Monthly",
            priceLabel = MONTHLY_PRICE,
            periodLabel = "per month",
            pricePerMonthEquivalent = null,
            hasFreeTrialOffer = true,
            offerTag = "offline-offer-monthly",
        ),
        SubscriptionProduct(
            planId = SubscriptionProductType.YEARLY.rawValue,
            title = "Yearly",
            priceLabel = YEARLY_PRICE,
            periodLabel = "per year",
            pricePerMonthEquivalent = YEARLY_PER_MONTH,
            hasFreeTrialOffer = true,
            offerTag = "offline-offer-yearly",
        ),
    )

    fun resolve(planId: String): SubscriptionProduct? = all().firstOrNull { it.planId == planId }

    private const val MONTHLY_PRICE = "$10.99"
    private const val YEARLY_PRICE = "$95.90"

    /** `95.90 / 12 = 7.99`, rounded — the grey second line on the card. */
    private const val YEARLY_PER_MONTH = "$7.99/month"
}
