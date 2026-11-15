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
 * - The `prod` flavor's [BillingClientApi] while
 *   [com.kornerz.core.model.IAP_ENABLED] is `false` — i.e. while the Play
 *   catalog query cannot succeed.
 *
 * The pricing mirrors iOS `Kornerz-SKConfig.storekit` (monthly `10.99`,
 * yearly `95.9`) and the per-month equivalent derived the same way
 * `SubscriptionViewModel.mothlyPriceForYearlySubscription()` derives it
 * (`SubscriptionViewModel.swift:81-91`).
 *
 * ## The trial flag is the store's *configuration*
 *
 * Both products carry a `P1W` introductory offer in
 * `Kornerz-SKConfig.storekit:185-200`, so the catalog reports
 * [SubscriptionProduct.isTrialEligible] as `true` — the store would
 * grant the trial to an eligible user.
 *
 * That is the **product-level** half of the question, and it is not the
 * whole of it: the same iOS store file grants the trial to a user who
 * has already redeemed it, and StoreKit does not populate
 * `Product.introductoryOffer` for that user — so iOS's button reads
 * "Subscribe", not "Try It Free".
 *
 * The per-user half is applied **on top** of this catalog by the
 * `dev` flavor's gateway and by the `prod` flavor's Play query, both of
 * which know whether *this* user has consumed the trial. Play enforces
 * that server-side by withholding the introductory-offer phase from an
 * ineligible user's `QueryProductDetails` response; the `dev` gateway
 * reproduces it from the local purchase history.
 *
 * @param isTrialEligible whether the store would grant the trial to the
 *   user reading this catalog. The caller is responsible for applying
 *   the per-user half of the rule.
 */
internal object OfflineCatalog {

    /**
     * The catalog as the store describes it, with the trial available.
     *
     * @param isTrialEligible whether the store grants the trial to the
     *   user this catalog is being served to.
     */
    fun all(isTrialEligible: Boolean = true): List<SubscriptionProduct> = listOf(
        SubscriptionProduct(
            playProductId = SubscriptionProductType.MONTHLY.playProductId,
            subscriptionType = SubscriptionProductType.MONTHLY.subscriptionType,
            title = SubscriptionProductType.MONTHLY.label,
            priceLabel = MONTHLY_PRICE,
            periodLabel = "per month",
            pricePerMonthEquivalent = null,
            isTrialEligible = isTrialEligible,
            offerTag = "offline-offer-monthly",
        ),
        SubscriptionProduct(
            playProductId = SubscriptionProductType.YEARLY.playProductId,
            subscriptionType = SubscriptionProductType.YEARLY.subscriptionType,
            title = SubscriptionProductType.YEARLY.label,
            priceLabel = YEARLY_PRICE,
            periodLabel = "per year",
            pricePerMonthEquivalent = YEARLY_PER_MONTH,
            isTrialEligible = isTrialEligible,
            offerTag = "offline-offer-yearly",
        ),
    )

    fun resolve(playProductId: String): SubscriptionProduct? =
        all().firstOrNull { it.playProductId == playProductId }

    private const val MONTHLY_PRICE = "$10.99"
    private const val YEARLY_PRICE = "$95.90"

    /** `95.90 / 12 = 7.99`, rounded — the grey second line on the card. */
    private const val YEARLY_PER_MONTH = "$7.99/month"
}
