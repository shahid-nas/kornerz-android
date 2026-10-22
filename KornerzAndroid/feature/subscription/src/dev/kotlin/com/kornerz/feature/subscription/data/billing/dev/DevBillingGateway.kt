package com.kornerz.feature.subscription.data.billing.dev

import android.app.Activity
import com.kornerz.core.model.SubscriptionProduct
import com.kornerz.core.model.SubscriptionProductType
import com.kornerz.feature.subscription.data.SubscriptionError
import com.kornerz.feature.subscription.data.SubscriptionException
import com.kornerz.feature.subscription.data.billing.BillingConnectionState
import com.kornerz.feature.subscription.data.billing.BillingPurchaseResult
import com.kornerz.feature.subscription.data.billing.PurchaseRecord
import com.kornerz.feature.subscription.data.billing.SubscriptionBillingGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The `dev` flavor's stand-in for Google Play Billing.
 *
 * **This class is never part of a `prod` build.** It lives in
 * `src/dev/kotlin` and is bound only by [DevBillingModule], which is
 * itself a `dev`-only Hilt module. There is no runtime flag that could
 * switch it on in a release build.
 *
 * ## Why it exists
 *
 * Play Billing has no sandbox. Google ships no mock mode, no local
 * catalog, and no equivalent of the Xcode `.storekit` file iOS uses for
 * this exact problem. The only Play-provided escape hatches — License
 * testing, testing tracks, and static test cards — all require an app
 * that already exists in Play Console with a matching package name and
 * upload certificate. Until `com.kornerz.kornerz` is registered, every
 * Play call on `dev` legitimately fails, so the paywall can only render
 * its error state.
 *
 * This simulates the *shape* of the billing flow, not Play's behaviour:
 * two plans resolve, a purchase takes a moment, acknowledgement succeeds,
 * and the entitlement is written exactly as a real one would be. Nothing
 * here contacts Play, and nothing here can charge anyone.
 *
 * ## What it deliberately does NOT simulate
 *
 * - **Pending purchases.** Play's deferred state has no analogue worth
 *   fabricating, so `BillingPurchaseResult.Pending` is never emitted here.
 * - **Failures.** A gateway that always succeeds would hide every error
 *   path the plans screen must handle. Those are covered by
 *   `SubscriptionViewModelTest` against a mocked gateway instead.
 * - **Renewal, cancellation, and refund.** A `PurchaseRecord` carries a
 *   purchase time and `SubscriptionRepository.deriveExpiryFrom` turns that
 *   into the entitlement expiry, so a simulated subscription expires on
 *   the same 30-/365-day schedule a real one does.
 */
class DevBillingGateway @Inject constructor() : SubscriptionBillingGateway {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * There is no Play license check to run, so the state is `Ready` with
     * a synthetic code rather than `Unavailable`. Consumers must treat the
     * code as data and never branch on it — which the `prod` wrapper also
     * requires, since both build types are signed with the debug keystore
     * and the real fingerprint is environment-dependent.
     */
    private val _connectionState = MutableStateFlow<BillingConnectionState>(
        BillingConnectionState.Ready(SYNTHETIC_LICENSE_CODE),
    )

    override val connectionState: StateFlow<BillingConnectionState> =
        _connectionState.asStateFlow()

    private val _purchaseOutcomes = Channel<BillingPurchaseResult>(Channel.BUFFERED)

    override val purchaseOutcomes: Flow<BillingPurchaseResult> =
        _purchaseOutcomes.receiveAsFlow()

    /** The simulated purchase, so `restore()` and `queryPurchases()` agree. */
    private var activePurchase: PurchaseRecord? = null

    override fun attachActivity(activity: Activity) = Unit

    override fun detachActivity() = Unit

    override fun launchPurchase(productId: String, offerToken: String?): Result<Unit> {
        if (DevCatalog.resolve(productId) == null) {
            return Result.failure(
                SubscriptionException(
                    SubscriptionError.ProductUnavailable,
                    "Unknown product id $productId",
                ),
            )
        }

        val record = PurchaseRecord(
            token = "dev-token-$productId",
            productId = productId,
            purchaseTimeMillis = System.currentTimeMillis(),
            isAcknowledged = false,
        )

        // Recorded before the outcome is emitted, not after: the outcome is
        // delayed so the acknowledge-then-grant sequence is exercised, and
        // an acknowledgement that arrives before the purchase is stored
        // would silently mark nothing. `activePurchase` is what
        // `queryPurchases()` and `restore()` read, so it also backs the
        // paywall gate's "no active Play entitlement" clause.
        activePurchase = record

        // Delayed on purpose: the ViewModel only drains `purchaseOutcomes`
        // while `isPurchasing` is true, and returning synchronously would
        // skip the acknowledge-then-grant sequence entirely — the one part
        // of the flow most worth exercising before the Play app exists.
        scope.launch {
            delay(PURCHASE_SETTLE_DELAY_MILLIS)
            _purchaseOutcomes.trySend(
                BillingPurchaseResult.Purchased(
                    token = record.token,
                    productId = record.productId,
                    purchaseTimeMillis = record.purchaseTimeMillis,
                ),
            )
        }
        return Result.success(Unit)
    }

    override suspend fun queryProductDetails(
        ids: List<String>,
    ): Result<List<SubscriptionProduct>> = Result.success(
        DevCatalog.all().filter { it.planId in ids },
    )

    override suspend fun queryPurchases(): Result<List<PurchaseRecord>> = Result.success(
        listOfNotNull(activePurchase),
    )

    override suspend fun acknowledgePurchase(token: String): Result<Unit> {
        // Play's acknowledgement is idempotent; the failure path that
        // blocks the success navigation is covered by the ViewModel tests.
        activePurchase = activePurchase?.copy(isAcknowledged = true)
        return Result.success(Unit)
    }

    override suspend fun restore(): Result<List<PurchaseRecord>> = queryPurchases()

    override fun endConnection() {
        _connectionState.value = BillingConnectionState.Disconnected
    }

    private companion object {
        /** `BillingClient.BillingResponseCode.OK`, mirrored as a plain int. */
        const val SYNTHETIC_LICENSE_CODE = 0

        /** Long enough for the in-flight button state to be observable. */
        const val PURCHASE_SETTLE_DELAY_MILLIS = 900L
    }
}

/**
 * The two-plan catalog the `dev` flavor renders, with the pricing iOS
 * declares in `Kornerz-SKConfig.storekit` (monthly `10.99`, yearly `95.9`)
 * and the per-month equivalent derived the same way
 * `SubscriptionViewModel.mothlyPriceForYearlySubscription()` derives it
 * (`SubscriptionViewModel.swift:81-91`).
 *
 * `hasFreeTrialOffer` is `true` for **both** plans because
 * `Kornerz-SKConfig.storekit:185-200` gives both products a `P1W`
 * introductory offer. On `prod` the flag is read from Play's own pricing
 * phases instead, so whichever plan the Play Console configures with a
 * trial drives the "Try It Free" label — no code change either way.
 */
internal object DevCatalog {

    fun all(): List<SubscriptionProduct> = listOf(
        SubscriptionProduct(
            planId = SubscriptionProductType.MONTHLY.rawValue,
            title = "Monthly",
            priceLabel = MONTHLY_PRICE,
            periodLabel = "per month",
            pricePerMonthEquivalent = null,
            hasFreeTrialOffer = true,
            offerTag = "dev-offer-monthly",
        ),
        SubscriptionProduct(
            planId = SubscriptionProductType.YEARLY.rawValue,
            title = "Yearly",
            priceLabel = YEARLY_PRICE,
            periodLabel = "per year",
            pricePerMonthEquivalent = YEARLY_PER_MONTH,
            hasFreeTrialOffer = true,
            offerTag = "dev-offer-yearly",
        ),
    )

    fun resolve(planId: String): SubscriptionProduct? = all().firstOrNull { it.planId == planId }

    private const val MONTHLY_PRICE = "$10.99"
    private const val YEARLY_PRICE = "$95.90"

    /** `95.90 / 12 = 7.99`, rounded — the grey second line on the card. */
    private const val YEARLY_PER_MONTH = "$7.99/month"
}