package com.kornerz.feature.subscription.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.kornerz.core.model.SubscriptionProduct
import com.kornerz.core.model.SubscriptionProductType
import com.kornerz.feature.subscription.data.SubscriptionError
import com.kornerz.feature.subscription.data.SubscriptionException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.lang.ref.WeakReference
import java.text.NumberFormat
import java.util.Currency
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The **only** type in the app that imports `com.android.billingclient.*`.
 *
 * Every Play SDK type — `BillingClient`, `ProductDetails`, `Purchase`,
 * `BillingResult`, the query params — is confined to this file and
 * projected into Compose-free `core/model` types ([SubscriptionProduct])
 * or the local [PurchaseRecord] / [BillingPurchaseResult] data classes
 * before crossing the boundary. This mirrors the repository's existing
 * wrapper convention (the `FirestoreApi` classes under
 * `feature/community/data/network/` and
 * `feature/profile/data/network/`), where SDK types never escape the
 * wrapper, and is what lets `SubscriptionViewModel` be exercised on the
 * JVM with no device and no Robolectric.
 *
 * Constructor-injected (`@Singleton` + `@Inject`) per the dominant DI
 * pattern — 13 of 15 DI files here are marker objects, and the only
 * `@Binds` is `NetworkModule` for `AuthApi`. No binding module is
 * introduced for this class.
 *
 * Dispatcher: every `Task`-returning Play call is bridged with
 * `kotlinx.coroutines.tasks.await`, and **no** `withContext(Dispatchers.IO)`
 * is added, matching `core/network`. Play's own client already dispatches
 * its IPC off the main thread, and `await` resumes on the caller's
 * dispatcher (`viewModelScope`, i.e. Main).
 *
 * **Pinned to `com.android.billingclient` 7.0.0**, whose response-code
 * constants live on `BillingClient.BillingResponseCode` (the top-level
 * `BillingResultCode` object and `ProductDetails.freeTrialOfferDetails`
 * arrived in 8.0 / 7.1 respectively). Where the `play-billing` spec's
 * mapping table names a code this version does not expose — `IO_ERROR`,
 * `PRODUCT_NOT_AVAILABLE` — it falls through to
 * [SubscriptionError.Unknown] rather than being faked; the pinned version
 * is the contract.
 */
@Singleton
class BillingClientApi @Inject constructor(
    // The application context, not an Activity: `BillingClient.newBuilder`
    // only needs a context to reach the Play Store, and the Activity
    // required to *present* the purchase sheet is attached separately via
    // [attachActivity]. Qualifying it keeps Hilt from trying to provide a
    // bare `Context` binding, which does not exist.
    @ApplicationContext private val context: Context,
) : SubscriptionBillingGateway {

    /**
     * The Activity Play needs in order to present the purchase sheet.
     * `launchBillingFlow(Activity, …)` requires one; a bare
     * application context cannot show UI.
     *
     * Tracked rather than injected because the app hosts a single
     * `MainActivity` and the Compose layer is the natural owner of the
     * lifecycle: `SubscriptionContent` calls [attachActivity] /
     * [detachActivity] from a `DisposableEffect`. Holding it weakly
     * avoids leaking a destroyed Activity through this `@Singleton`.
     */
    @Suppress("StaticFieldLeak")
    private var activityRef: WeakReference<Activity>? = null

    /**
     * Registers the Activity used to present the Play purchase sheet.
     * Called from the plans screen's `DisposableEffect`.
     */
    override fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    /**
     * Releases the Activity reference once the plans screen leaves the
     * composition, so a rotated or finished Activity is not retained.
     */
    override fun detachActivity() {
        activityRef = null
    }

    private val _connectionState = MutableStateFlow<BillingConnectionState>(
        BillingConnectionState.Disconnected,
    )

    /**
     * Client readiness plus a typed disconnect reason.
     *
     * The Play license-check response code is carried as data on
     * [BillingConnectionState.Ready] and is deliberately **not** gated on:
     * `app/build.gradle.kts` signs both build types with the debug
     * keystore, so the certificate fingerprint registered in Play Console
     * is environment-dependent and untestable.
     */
    override val connectionState: StateFlow<BillingConnectionState> =
        _connectionState.asStateFlow()

    private val _purchaseOutcomes = Channel<BillingPurchaseResult>(Channel.BUFFERED)

    /**
     * Async purchase outcomes from Play's `PurchasesUpdatedListener`.
     *
     * Play's direct analogue of iOS `InAppManager.buyProduct`
     * (`InAppManager.swift:63-89`) awaiting `product.purchase()`, plus the
     * background `listenForTransactions()` listener (`:111-129`). The
     * listener is registered once for the client's lifetime, so a purchase
     * completed while the plans screen is off-screen is still observed on
     * the next resume.
     */
    override val purchaseOutcomes: Flow<BillingPurchaseResult> =
        _purchaseOutcomes.receiveAsFlow()

    /**
     * Play `ProductDetails` retained from the last successful
     * [queryProductDetails], keyed by product id. Play's 7.0.0
     * `launchBillingFlow` takes the resolved `ProductDetails` rather than a
     * bare id, so the catalog query is a prerequisite for a purchase — which
     * is also the natural ordering for the plans screen.
     */
    private val resolvedProducts = mutableMapOf<String, ProductDetails>()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (purchases.isNullOrEmpty()) {
            // USER_CANCELED arrives here as an OK BillingResult carrying
            // a null purchase list. It is a normal return path: delivered
            // so the ViewModel can clear its in-flight flag without ever
            // showing an error message.
            _purchaseOutcomes.trySend(
                BillingPurchaseResult.Failed(billingResult.toSubscriptionError()),
            )
        } else {
            // One launch can deliver several purchases (deferred upgrade).
            // Forward them all in Play's order; the ViewModel takes the
            // first terminal outcome.
            purchases.forEach { purchase ->
                purchase.toOutcome()?.let(_purchaseOutcomes::trySend)
            }
        }
        Unit
    }

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()
            .also { client ->
                _connectionState.value = BillingConnectionState.Connecting
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        _connectionState.value = when (val code = billingResult.responseCode) {
                            BillingClient.BillingResponseCode.OK ->
                                BillingConnectionState.Ready(licenseResponseCode = code)

                            // The `dev` flavor's application id
                            // (`com.kornerz.kornerz.dev`) is not a
                            // registered Play listing, so its license
                            // check fails exactly this way.
                            BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
                                BillingConnectionState.Unavailable(
                                    BillingUnavailableReason.UnsupportedApplicationId,
                                )

                            else ->
                                BillingConnectionState.Unavailable(
                                    billingResult.toUnavailableReason(),
                                )
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        _connectionState.value = BillingConnectionState.Unavailable(
                            BillingUnavailableReason.Disconnected(
                                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                            ),
                        )
                    }
                })
            }
    }

    /**
     * Resolves the Play catalog for [ids].
     *
     * Returns an empty list — never throws — when the ids themselves
     * cannot be resolved, so a misconfigured Play Console renders the
     * plans screen's error state with a working retry instead of an
     * uncaught exception. Returns `Result.failure` when Play is
     * unreachable, which is the `dev` flavor's unregistered-application-id
     * case.
     */
    override suspend fun queryProductDetails(
        ids: List<String>,
    ): Result<List<SubscriptionProduct>> = runCatching {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ids.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        val billingResult = result.billingResult
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            val error = billingResult.toSubscriptionError()
            if (error == SubscriptionError.ProductUnavailable) {
                // Console misconfiguration: the products are absent or
                // unpublished. An empty catalog is the honest outcome —
                // the screen renders "no plans available" plus retry.
                return@runCatching emptyList()
            }
            throw SubscriptionException(
                error,
                "queryProductDetails failed: ${billingResult.debugMessage}",
            )
        }

        result.productDetailsList.orEmpty()
            .onEach { details -> resolvedProducts[details.productId] = details }
            .map { it.toSubscriptionProduct() }
            .sortedBy { product ->
                // Preserve the pinned MONTHLY-then-YEARLY order rather
                // than whatever order Play happens to return.
                SubscriptionProductType.fromRawValue(product.planId)?.ordinal ?: Int.MAX_VALUE
            }
    }.recoverCatching { throwable ->
        throw throwable.asSubscriptionThrowable("queryProductDetails")
    }

    /**
     * Reads the active, non-consumed subscription purchases on this
     * device. Pending purchases are excluded: a deferred purchase has not
     * granted an entitlement yet.
     */
    override suspend fun queryPurchases(): Result<List<PurchaseRecord>> = runCatching {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            throw SubscriptionException(
                result.billingResult.toSubscriptionError(),
                "queryPurchases failed: ${result.billingResult.debugMessage}",
            )
        }
        result.purchasesList.mapNotNull { it.toPurchaseRecord() }
    }.recoverCatching { throwable ->
        throw throwable.asSubscriptionThrowable("queryPurchases")
    }

    /**
     * Re-reads the active subscriptions from Play.
     *
     * The Android equivalent of iOS `InAppManager.restore(completion:)`
     * (`InAppManager.swift:103-109`), which calls `AppStore.sync()` and
     * re-reads `currentEntitlements`. **Purely a re-derivation**: it
     * never creates a purchase and never charges the user, which is why
     * it is a straight delegation to [queryPurchases].
     */
    override suspend fun restore(): Result<List<PurchaseRecord>> = queryPurchases()

    /**
     * Opens the Play purchase sheet.
     *
     * Non-suspend by necessity: `launchBillingFlow` is not a `Task` but a
     * synchronous `BillingResult`. The *outcome* arrives later on
     * [purchaseOutcomes], so a success here means only that the sheet was
     * presented — never that a purchase completed.
     *
     * Requires an Activity attached via [attachActivity]; without one the
     * sheet cannot be presented, so a typed failure is returned rather
     * than crashing. [SubscriptionProduct.offerTag] supplies the base
     * plan's offer token when the Play Console has an introductory offer
     * on it; a null token subscribes to the base plan with no offer
     * attached.
     *
     * The Play response code is translated here rather than returned to
     * the caller, so [SubscriptionBillingGateway] stays free of Play
     * types and a simulated implementation can honour the same contract.
     */
    override fun launchPurchase(
        productId: String,
        offerToken: String?,
    ): Result<Unit> {
        val activity = activityRef?.get()
        // `launchBillingFlow` in 7.0.0 takes the resolved ProductDetails
        // rather than a bare product id, so the catalog query's result is
        // retained here. An unresolved product id means the console is
        // misconfigured for this build.
        val productDetails = resolvedProducts[productId]
        if (activity == null || productDetails == null) {
            val message = when {
                productDetails == null ->
                    "Product $productId was never resolved; call queryProductDetails first"
                else -> "No Activity attached; cannot present the Play purchase sheet"
            }
            return Result.failure(
                SubscriptionException(SubscriptionError.BillingUnavailable, message),
            )
        }

        val detailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .apply { if (!offerToken.isNullOrEmpty()) setOfferToken(offerToken) }
            .build()
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(detailsParams))
                .build(),
        )
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Result.success(Unit)
        } else {
            Result.failure(
                SubscriptionException(
                    result.toSubscriptionError(),
                    "launchBillingFlow failed: ${result.debugMessage}",
                ),
            )
        }
    }

    /**
     * Acknowledges a purchase inside Google's three-day window.
     *
     * Called by `SubscriptionViewModel` for every `PURCHASED` purchase
     * before any entitlement is written — see the `play-billing` spec
     * requirement "Every purchase is acknowledged before entitlement is
     * granted". Google refunds anything left unacknowledged after three
     * days, which would silently reverse an entitlement the user was
     * already shown as active.
     */
    override suspend fun acknowledgePurchase(token: String): Result<Unit> = runCatching {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(token)
            .build()
        val result = billingClient.acknowledgePurchase(params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            throw SubscriptionException(
                SubscriptionError.NotAcknowledged,
                "acknowledgePurchase failed: ${result.debugMessage}",
            )
        }
        Unit
    }.recoverCatching { throwable ->
        throw throwable.asSubscriptionThrowable("acknowledgePurchase")
    }

    /**
     * Releases the client. Idempotent — a second call is a no-op and does
     * not throw, which matters because the plans screen can be dismissed
     * while a purchase is still in flight.
     */
    override fun endConnection() {
        runCatching { billingClient.endConnection() }
        _connectionState.value = BillingConnectionState.Disconnected
    }

    // ---- projections -------------------------------------------------

    private fun Purchase.toOutcome(): BillingPurchaseResult? = when (purchaseState) {
        Purchase.PurchaseState.PURCHASED -> BillingPurchaseResult.Purchased(
            token = purchaseToken,
            productId = products.firstOrNull().orEmpty(),
            purchaseTimeMillis = purchaseTime,
        )
        Purchase.PurchaseState.PENDING -> BillingPurchaseResult.Pending
        else -> null
    }

    private fun Purchase.toPurchaseRecord(): PurchaseRecord? {
        if (purchaseState != Purchase.PurchaseState.PURCHASED) return null
        val productId = products.firstOrNull() ?: return null
        return PurchaseRecord(
            token = purchaseToken,
            productId = productId,
            purchaseTimeMillis = purchaseTime,
            isAcknowledged = isAcknowledged,
        )
    }

    /**
     * Projects a Play `ProductDetails` into the Compose-free
     * [SubscriptionProduct] the plans screen renders.
     *
     * `pricePerMonthEquivalent` is populated **only** for the yearly plan —
     * the grey secondary line on that card — mirroring iOS
     * `SubscriptionViewModel.mothlyPriceForYearlySubscription()`
     * (`:81-91`), which divides the yearly price by twelve and appends
     * "/month".
     *
     * Trial availability is read from Play's own pricing phases rather
     * than hard-coded, so whichever base plan the Play Console configures
     * with a trial drives the "Try It Free" label. **API-surface note:**
     * `ProductDetails.freeTrialOfferDetails` does not exist in
     * `com.android.billingclient` 7.0.0 (it arrived in 7.1), so the trial
     * is derived from the phase list instead: a subscription with an
     * introductory offer reports a leading `priceAmountMicros == 0` phase
     * followed by the paid recurring phase. Same signal, same
     * "Play decides" semantics, using the pinned version's API.
     */
    private fun ProductDetails.toSubscriptionProduct(): SubscriptionProduct {
        val offer = basePlanSubscriptionOffer()
        val phases = offer?.pricingPhases?.pricingPhaseList.orEmpty()
        val paidPhase = phases.lastOrNull { it.priceAmountMicros > 0L } ?: phases.firstOrNull()
        val hasFreeTrial = phases.size > 1 && phases.first().priceAmountMicros == 0L
        val yearly = SubscriptionProductType.fromRawValue(productId) ==
            SubscriptionProductType.YEARLY

        return SubscriptionProduct(
            planId = productId,
            title = title.ifBlank { name },
            priceLabel = paidPhase?.formattedPrice.orEmpty(),
            periodLabel = paidPhase.billingPeriodLabel(),
            pricePerMonthEquivalent = if (yearly && paidPhase != null) {
                formatLocalizedMicros(
                    micros = paidPhase.priceAmountMicros / 12L,
                    currencyCode = paidPhase.priceCurrencyCode,
                ) + PER_MONTH_SUFFIX
            } else {
                null
            },
            hasFreeTrialOffer = hasFreeTrial,
            offerTag = offer?.offerToken,
        )
    }

    /**
     * The base plan's offer details — the entry whose `offerId` is null.
     * Its offer token is what must be passed to [launchPurchase] for the
     * base plan's introductory offer to apply; without it Play would start
     * the paid phase immediately.
     */
    private fun ProductDetails.basePlanSubscriptionOffer():
        ProductDetails.SubscriptionOfferDetails? =
        subscriptionOfferDetails?.firstOrNull { it.offerId.isNullOrEmpty() }
            ?: subscriptionOfferDetails?.firstOrNull()

    /**
     * Play's ISO-8601 billing-period code as a human-readable label. The
     * per-month / per-year wording covers the two plans this app sells;
     * anything else falls through to the raw code so the card never
     * renders an empty period.
     */
    private fun ProductDetails.PricingPhase?.billingPeriodLabel(): String =
        when (this?.billingPeriod) {
            PERIOD_CODE_MONTHLY -> PERIOD_MONTHLY
            PERIOD_CODE_YEARLY -> PERIOD_YEARLY
            else -> this?.billingPeriod.orEmpty()
        }

    private fun formatLocalizedMicros(micros: Long, currencyCode: String): String {
        val format = NumberFormat.getCurrencyInstance()
        currencyCode.takeIf { it.isNotBlank() }?.let { code ->
            runCatching { Currency.getInstance(code) }.getOrNull()?.let(format::setCurrency)
        }
        return format.format(micros / 1_000_000.0)
    }

    private companion object {
        const val PER_MONTH_SUFFIX = "/month"
        const val PERIOD_CODE_MONTHLY = "P1M"
        const val PERIOD_CODE_YEARLY = "P1Y"
        const val PERIOD_MONTHLY = "per month"
        const val PERIOD_YEARLY = "per year"
    }
}

/**
 * A Play subscription purchase projected out of the SDK. Carries no
 * `com.android.billingclient` type, so it can cross into
 * `feature/subscription`'s repository layer and be faked in unit tests.
 */
data class PurchaseRecord(
    val token: String,
    val productId: String,
    val purchaseTimeMillis: Long,
    val isAcknowledged: Boolean,
)

/**
 * The outcome of a purchase launched via [BillingClientApi.launchPurchase].
 *
 * [Purchased] deliberately carries plain data rather than a Play
 * `Purchase`, so the ViewModel can be exercised on the JVM.
 */
sealed interface BillingPurchaseResult {

    data class Purchased(
        val token: String,
        val productId: String,
        val purchaseTimeMillis: Long,
    ) : BillingPurchaseResult

    /** Play's deferred / deferred-upgrade state — no entitlement yet. */
    data object Pending : BillingPurchaseResult

    data class Failed(val error: SubscriptionError) : BillingPurchaseResult
}

/** Client readiness, mirroring `BillingClientStateListener`. */
sealed interface BillingConnectionState {

    /** Not connected yet, or closed by [BillingClientApi.endConnection]. */
    data object Disconnected : BillingConnectionState

    /** `startConnection` / `enablePendingPurchases` in flight. */
    data object Connecting : BillingConnectionState

    /**
     * Connected. [licenseResponseCode] is the Play license check's
     * response code, carried as data and never branched on — the project
     * signs release builds with the debug keystore, so the fingerprint is
     * environment-dependent.
     */
    data class Ready(val licenseResponseCode: Int) : BillingConnectionState

    /** The connection attempt failed, or the app id is not a Play listing. */
    data class Unavailable(val reason: BillingUnavailableReason) : BillingConnectionState
}

/** Why billing is unavailable. Drives the plans screen's non-fatal notice. */
sealed interface BillingUnavailableReason {

    /** The device has no Play Store / Play Services. */
    data object NoPlayStore : BillingUnavailableReason

    /** Play disconnected, or the service was unavailable. */
    data class Disconnected(val errorCode: Int) : BillingUnavailableReason

    /**
     * The license check failed because the application id is not
     * registered in Play Console — the `dev` flavor's
     * `com.kornerz.kornerz.dev` case.
     */
    data object UnsupportedApplicationId : BillingUnavailableReason

    data object Unknown : BillingUnavailableReason
}

/**
 * Maps a Play response code to the [SubscriptionError] taxonomy per the
 * `play-billing` spec table.
 *
 * Declared in the wrapper's own file so a caller can convert a
 * [BillingClientApi.launchPurchase] return value without importing
 * `com.android.billingclient` — the type is only ever inferred.
 *
 * `IO_ERROR` and `PRODUCT_NOT_AVAILABLE` are named in the spec table but
 * do not exist in the pinned 7.0.0 surface; they fall through to
 * [SubscriptionError.Unknown] rather than being synthesised.
 */
fun BillingResult.toSubscriptionError(): SubscriptionError = responseCode.toSubscriptionError()

internal fun Int.toSubscriptionError(): SubscriptionError = when (this) {
    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
    BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
    -> SubscriptionError.BillingUnavailable

    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
    // ITEM_ALREADY_OWNED is distinct from PurchasePending: it means Play
    // already holds this subscription, which is the shape a restore
    // surfaces, not a deferred purchase.
    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED,
    -> SubscriptionError.ProductUnavailable

    BillingClient.BillingResponseCode.USER_CANCELED -> SubscriptionError.UserCancelled

    BillingClient.BillingResponseCode.NETWORK_ERROR,
    BillingClient.BillingResponseCode.SERVICE_TIMEOUT,
    -> SubscriptionError.NetworkError

    else -> SubscriptionError.Unknown("BillingResponseCode $this")
}

private fun BillingResult.toUnavailableReason(): BillingUnavailableReason = when (responseCode) {
    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
    BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
    -> BillingUnavailableReason.NoPlayStore

    BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
        BillingUnavailableReason.UnsupportedApplicationId

    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
    -> BillingUnavailableReason.Disconnected(responseCode)

    else -> BillingUnavailableReason.Unknown
}

/**
 * Re-throws with a [SubscriptionException] carrying a typed
 * [SubscriptionError], so a bare SDK exception still reaches the caller as
 * a typed failure rather than an opaque `RuntimeException`.
 */
internal fun Throwable.asSubscriptionThrowable(operation: String): Throwable = when (this) {
    is SubscriptionException -> this
    else -> {
        // The operation name goes into BOTH the typed error and the
        // RuntimeException message: a bare SDK message ("IBillingController-
        // Service.bind() failed") says nothing about which call failed, and
        // the thrown message is what lands in a crash report.
        val detail = message ?: this::class.simpleName.orEmpty()
        SubscriptionException(
            SubscriptionError.Unknown("$operation failed: $detail"),
            "$operation failed: $detail",
        )
    }
}
