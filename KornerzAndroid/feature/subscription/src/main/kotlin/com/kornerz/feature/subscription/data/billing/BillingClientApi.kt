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
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.kornerz.core.model.IAP_ENABLED
import com.kornerz.core.model.SubscriptionProduct
import com.kornerz.core.model.SubscriptionProductType
import com.kornerz.feature.subscription.data.SubscriptionError
import com.kornerz.feature.subscription.data.SubscriptionException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeoutOrNull
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
 * wrapper, and is what keeps the Play types from leaking into
 * `SubscriptionViewModel` and the Compose layer.
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
 * **Pinned to `com.android.billingclient` 8.0.0**, whose response-code
 * constants live on `BillingClient.BillingResponseCode`. The 8.0 upgrade
 * is source-compatible with this file apart from the removal of the
 * no-arg `BillingClient.Builder.enablePendingPurchases()`, which is now
 * called with an explicit `PendingPurchasesParams` carrying
 * `enableOneTimeProducts()` — the equivalence Google documents for the
 * removed overload. `BillingResponseCode` keeps the same constants, and
 * the ktx `queryProductDetails` extension still returns a
 * `ProductDetailsResult` carrying `billingResult` and
 * `productDetailsList`. Where the `play-billing` spec's mapping table
 * names a code this version does not expose — `IO_ERROR`,
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
        // While [IAP_ENABLED] is `false` the client is never built, so the
        // state would otherwise sit at `Disconnected` forever and the plans
        // screen would render as if billing were merely still connecting.
        // `Unavailable` is the honest shape here.
        if (IAP_ENABLED) {
            BillingConnectionState.Disconnected
        } else {
            BillingConnectionState.Unavailable(BillingUnavailableReason.Unknown)
        },
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
     * [queryProductDetails], keyed by product id. Play's
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

    /**
     * The Play client — `null` while IAP is off.
     *
     * TEMPORARY: the nullable type is what makes "the Play SDK is never
     * constructed while IAP is off" a *compile-time* guarantee: every
     * call site has to deal with the absent client, so a new call path
     * cannot silently reach Play. The `!IAP_ENABLED` branch returns
     * before `BillingClient.newBuilder` is ever called, so the Play
     * services are never contacted.
     *
     * **Mutable rather than `by lazy`** so a dropped Play service
     * connection can be replaced. A `by lazy` client is a one-shot
     * singleton: once Play's service drops the connection
     * ([BillingClientStateListener.onBillingServiceDisconnected]), every
     * subsequent query would be issued against a dead client and fail
     * with `SERVICE_DISCONNECTED` forever. The retry affordance on the
     * plans screen would then be useless. Replacing the instance is what
     * makes a retry actually able to succeed.
     *
     * Guarded by [clientLock] because the replacement happens on a
     * background thread (the ViewModel's coroutine) while the Play
     * listener callbacks arrive on the main thread.
     */
    private var billingClient: BillingClient? = null

    private val clientLock = Any()

    /**
     * Builds a fresh Play client and starts connecting it.
     *
     * The `PurchasesUpdatedListener` is re-registered on every instance,
     * so a purchase completed while the previous client was disconnected
     * is still delivered to [purchaseOutcomes].
     */
    private fun createClient(): BillingClient {
        val client = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build(),
            )
            .build()

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
        return client
    }

    /**
     * The current client, creating one on first use.
     *
     * Reading the field is synchronized so a concurrent rebuild cannot
     * hand two callers two different clients.
     *
     * A client whose connection has already been reported as
     * disconnected is **not** returned: it is bound to a dead service
     * and every query against it would fail with `SERVICE_DISCONNECTED`.
     * It is replaced here instead, so the replacement happens once per
     * dropped connection rather than once per call.
     */
    private fun currentClient(): BillingClient? {
        if (!IAP_ENABLED) return null
        synchronized(clientLock) {
            billingClient?.let { client ->
                val state = _connectionState.value
                val disconnected = state is BillingConnectionState.Unavailable &&
                    state.reason is BillingUnavailableReason.Disconnected
                if (!disconnected) return client
                // Drop the dead client and fall through to a fresh one.
                runCatching { client.endConnection() }
                billingClient = null
            }
            val client = createClient()
            billingClient = client
            return client
        }
    }

    /**
     * Discards the current client and starts a new connection.
     *
     * Called when Play's service has dropped the connection. The old
     * client is closed first — it is bound to a dead service — and the
     * new one starts the IPC bind from scratch.
     */
    private fun rebuildClient() {
        synchronized(clientLock) {
            runCatching { billingClient?.endConnection() }
            billingClient = null
            if (IAP_ENABLED) {
                billingClient = createClient()
            }
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
    ): Result<List<SubscriptionProduct>> {
        // IAP is switched off, so the catalog is served from the offline
        // copy instead of a Play round trip that can only fail with
        // "queryProductDetails failed: ...".
        if (!IAP_ENABLED) {
            // The offline copy carries the store's *configuration*; the
            // per-user half of the trial rule still applies, so a user
            // who already holds a purchase is offered "Subscribe" rather
            // than "Try It Free". This mirrors what Play would report.
            val purchases = queryPurchases().getOrDefault(emptyList())
            return Result.success(
                OfflineCatalog.all(isTrialEligible = purchases.isEmpty())
                    .filter { it.playProductId in ids },
            )
        }

        return runCatching {
            // The catalog query is the app's *first* Play call, and the
            // one most likely to land inside the connection window —
            // the plans screen opens it. Waiting for readiness here is
            // what keeps "Service connection is disconnected" off the
            // screen.
            ensureConnected()

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

            val result = currentClient()?.queryProductDetails(params)
                ?: throw SubscriptionException(
                    SubscriptionError.BillingUnavailable,
                    "In-app purchases are disabled in this build",
                )
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
                    SubscriptionProductType.fromPlayProductId(product.playProductId)?.ordinal
                        ?: Int.MAX_VALUE
                }
        }.recoverCatching { throwable ->
            throw throwable.asSubscriptionThrowable("queryProductDetails")
        }
    }

    /**
     * Reads the active, non-consumed subscription purchases on this
     * device. Pending purchases are excluded: a deferred purchase has not
     * granted an entitlement yet.
     */
    override suspend fun queryPurchases(): Result<List<PurchaseRecord>> {
        // No Play client is built while IAP is off, so there are no local
        // purchases to report.
        if (!IAP_ENABLED) {
            return Result.success(emptyList())
        }

        return runCatching {
            ensureConnected()
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            val result = currentClient()?.queryPurchasesAsync(params)
                ?: throw SubscriptionException(
                    SubscriptionError.BillingUnavailable,
                    "In-app purchases are disabled in this build",
                )
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
    }

    /**
     * Re-reads the active subscriptions from Play, ensuring the client is
     * connected first.
     *
     * ## How Android "restore" actually works
     *
     * iOS has an explicit server-side primitive: `AppStore.sync()` pulls
     * the signed-in Apple ID's transaction history down to the device.
     * **Android has no equivalent.** `queryPurchaseHistoryAsync` — the
     * closest thing — was removed from `BillingClient` in Play Billing
     * 8.0.0 (verified against the 8.0.0 artifact: the method is absent
     * from the class; only the orphaned `PurchaseHistoryRecord` data
     * classes remain). There is deliberately no way for an app to walk a
     * user's historical purchases.
     *
     * What Android offers instead is `queryPurchasesAsync(SUBS)`, which
     * returns the **currently owned** subscriptions for the Play account
     * signed in on the device. Play's client performs that sync against
     * Google's servers as part of the call, which is what makes a
     * fresh install able to see a purchase made on another device:
     * Google's own guidance lists "a user may buy an item on one device
     * and then expect to see the item when they switch devices" as one of
     * the situations this query exists to handle.
     *
     * Two consequences follow, and they drive the rest of this file:
     *
     * 1. The query is scoped to the **Play account**, not to the app
     *    account. A user who signed in to Kornerz with a different email
     *    than the one that made the purchase still sees the purchase —
     *    Play has no notion of the app's accounts. The app account is
     *    matched separately, via the obfuscated account id attached at
     *    checkout (see [PurchaseRecord.obfuscatedAccountId]).
     * 2. The sync only happens when the call actually reaches Play, so a
     *    client that is still connecting would report an empty list and
     *    the user would see "nothing to restore" for a subscription they
     *    demonstrably own. [ensureConnected] waits for readiness first so
     *    the restore is never a false negative.
     *
     * The call **never** creates a purchase and **never** charges the
     * user: it only re-derives the entitlement.
     */
    override suspend fun restore(): Result<List<PurchaseRecord>> {
        if (!IAP_ENABLED) {
            return Result.success(emptyList())
        }

        return runCatching {
            ensureConnected()
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            val result = currentClient()?.queryPurchasesAsync(params)
                ?: throw SubscriptionException(
                    SubscriptionError.BillingUnavailable,
                    "In-app purchases are disabled in this build",
                )
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                throw SubscriptionException(
                    result.billingResult.toSubscriptionError(),
                    "restore failed: ${result.billingResult.debugMessage}",
                )
            }
            result.purchasesList.mapNotNull { it.toPurchaseRecord() }
        }.recoverCatching { throwable ->
            throw throwable.asSubscriptionThrowable("restore")
        }
    }

    /**
     * Suspends until the Play client is ready to serve a query.
     *
     * The client is built lazily and connected asynchronously, so a
     * restore tapped immediately after launch can arrive while the
     * connection is still being established. Querying in that window
     * returns an empty list rather than an error, which the restore flow
     * would report as "no subscription found" — a false negative that
     * tells a paying subscriber they have nothing.
     *
     * `queryPurchasesAsync` already retries internally once the service is
     * connected (the library's own reconnect behaviour), so this only has
     * to cover the window before the *first* connection completes.
     *
     * ## Why every Play call goes through here
     *
     * `startConnection` binds to the Play Store service over IPC and is
     * **asynchronous**. A query issued in that window is answered by Play
     * with `SERVICE_DISCONNECTED` and the debug message "Service
     * connection is disconnected" — not because anything is wrong with the
     * account or the console, but because the bind had not completed yet.
     * That is a race, and it is the reason the plans screen could open
     * onto an error state whose only remedy was a retry button.
     *
     * So this gate is not only for `restore()`: every Play call waits
     * for readiness first.
     */
    private suspend fun ensureConnected() {
        val client = currentClient() ?: throw SubscriptionException(
            SubscriptionError.BillingUnavailable,
            "In-app purchases are disabled in this build",
        )
        if (client.isReady) return

        val state = awaitConnectionSettled()
        if (state is BillingConnectionState.Unavailable) {
            // A disconnected service is recoverable — the client can be
            // rebuilt and reconnected — so the disconnect is retried once
            // before the failure is reported. Every other reason (no Play
            // Store installed, unregistered application id) is terminal:
            // reconnecting would fail identically.
            if (state.reason is BillingUnavailableReason.Disconnected) {
                rebuildClient()
                val retried = awaitConnectionSettled()
                if (retried is BillingConnectionState.Ready) return
                throw SubscriptionException(
                    SubscriptionError.BillingUnavailable,
                    "Play Billing is unavailable: ${retried.reasonOrState()}",
                )
            }
            throw SubscriptionException(
                SubscriptionError.BillingUnavailable,
                "Play Billing is unavailable: ${state.reasonOrState()}",
            )
        }
    }

    /**
     * Waits for the current connection attempt to settle.
     *
     * The wait is bounded: Play's service can fail to bind entirely
     * (no Play Store on the device, Play Store updating), and a callback
     * that never arrives would otherwise suspend the caller forever.
     *
     * The wait starts from the state the *current* attempt published.
     * A `Connecting` state means the attempt is in flight and the
     * callback is still to come; anything already settled is returned
     * immediately.
     */
    private suspend fun awaitConnectionSettled(): BillingConnectionState {
        val settled = withTimeoutOrNull(CONNECTION_TIMEOUT_MILLIS) {
            connectionState.first {
                it is BillingConnectionState.Ready || it is BillingConnectionState.Unavailable
            }
        }
        return settled ?: BillingConnectionState.Unavailable(
            BillingUnavailableReason.Unknown,
        )
    }

    /**
     * The failure description for a settled-but-unusable connection.
     *
     * A [BillingConnectionState.Ready] never reaches this — the caller
     * only asks for a reason once the connection has failed — but the
     * branch keeps the function total so it can be interpolated
     * unconditionally.
     */
    private fun BillingConnectionState.reasonOrState(): String = when (this) {
        is BillingConnectionState.Unavailable -> reason.toString()
        else -> toString()
    }

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
        obfuscatedAccountId: String?,
    ): Result<Unit> {
        // IAP is switched off, so there is no Play sheet to present.
        // Report the same typed failure the ViewModel already renders as
        // "Billing is unavailable right now".
        if (!IAP_ENABLED) {
            return Result.failure(
                SubscriptionException(
                    SubscriptionError.BillingUnavailable,
                    "In-app purchases are disabled in this build",
                ),
            )
        }

        val activity = activityRef?.get()
        // `launchBillingFlow` takes the resolved ProductDetails
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

        // The sheet can only be presented by a connected client. The
        // catalog query already established readiness, but the service
        // can drop the connection between the query and the tap.
        val client = currentClient()
        if (client == null || !client.isReady) {
            return Result.failure(
                SubscriptionException(
                    SubscriptionError.BillingUnavailable,
                    "Play Billing is not connected; cannot present the purchase sheet",
                ),
            )
        }

        val detailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .apply { if (!offerToken.isNullOrEmpty()) setOfferToken(offerToken) }
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(detailsParams))
            // Attaching the app account id is what makes a later restore
            // able to attribute this purchase to the account that made it.
            // Play echoes it back with the purchase, so it survives a
            // fresh install on a different device.
            .apply {
                if (!obfuscatedAccountId.isNullOrEmpty()) {
                    setObfuscatedAccountId(obfuscatedAccountId)
                }
            }
            .build()
        val result = client.launchBillingFlow(activity, flowParams)
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
    override suspend fun acknowledgePurchase(token: String): Result<Unit> {
        // No purchase can be made while IAP is off, so there is nothing to
        // acknowledge.
        if (!IAP_ENABLED) {
            return Result.failure(
                SubscriptionException(
                    SubscriptionError.NotAcknowledged,
                    "In-app purchases are disabled in this build",
                ),
            )
        }

        return runCatching {
            ensureConnected()
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(token)
                .build()
            val result = currentClient()?.acknowledgePurchase(params)
                ?: throw SubscriptionException(
                    SubscriptionError.NotAcknowledged,
                    "In-app purchases are disabled in this build",
                )
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
    }

    /**
     * Rebuilds the client and restarts the Play connection.
     *
     * Public counterpart of the private [reconnect] worker, so the plans
     * screen's retry affordance can recover from a dropped service
     * instead of re-querying a dead client.
     */
    override fun reconnect() {
        if (!IAP_ENABLED) return
        rebuildClient()
    }

    /**
     * Releases the client. Idempotent — a second call is a no-op and does
     * not throw, which matters because the plans screen can be dismissed
     * while a purchase is still in flight.
     *
     * The field is cleared as well as the client closed: a closed
     * `BillingClient` cannot be reused, so the next call must build a
     * fresh one rather than issue a query against a dead instance.
     */
    override fun endConnection() {
        synchronized(clientLock) {
            // The client is `null` while IAP is off, so the Play client is
            // never even constructed here.
            runCatching { billingClient?.endConnection() }
            billingClient = null
        }
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
            // The obfuscated account id is attached to the purchase at
            // checkout and comes back with it, so it survives a fresh
            // install: it is stored on Google's side, not on the device.
            // A purchase made outside the app carries no id, which is
            // exactly the case the restore flow treats as "grant to the
            // signed-in user" (Play's own guidance).
            obfuscatedAccountId = accountIdentifiers?.obfuscatedAccountId,
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
     * `com.android.billingclient` 8.0.0 (nor in 7.x), so the trial is
     * derived from the phase list instead: a subscription with an
     * introductory offer reports a leading `priceAmountMicros == 0` phase
     * followed by the paid recurring phase. Same signal, same
     * "Play decides" semantics, using the pinned version's API.
     *
     * ## This is a per-user verdict, not a product flag
     *
     * Play filters offers **server-side, per user**: the introductory
     * phase is only present in a `QueryProductDetails` response for a
     * user who may actually redeem it. A user who has already consumed
     * the trial, or who is outside the offer's eligible region, is
     * served the base plan without the free-trial phase. So the presence
     * of a zero-priced leading phase *is* the eligibility signal — the
     * same semantics iOS gets from StoreKit, where
     * `Product.introductoryOffer` is non-`nil` only for an eligible
     * user (`SubscriptionViewModel.swift:73-79`).
     *
     * The zero phase must also be a genuine **trial** rather than a
     * $0 promotional phase that bills later within the same period: the
     * phase is only counted when it is followed by a **paid** phase, so
     * a free-but-recurring plan is never mistaken for a trial.
     */
    private fun ProductDetails.toSubscriptionProduct(): SubscriptionProduct {
        val offer = basePlanSubscriptionOffer()
        val phases = offer?.pricingPhases?.pricingPhaseList.orEmpty()
        val paidPhase = phases.lastOrNull { it.priceAmountMicros > 0L } ?: phases.firstOrNull()
        val hasFreeTrial = phases.size > 1 &&
            phases.first().priceAmountMicros == 0L &&
            phases.any { it.priceAmountMicros > 0L }
        // The Play product id and the canonical cross-platform
        // `subscriptionType` are distinct namespaces. The canonical value is
        // resolved from the pinned catalog; a product id outside it has no
        // canonical counterpart, so it falls back to the Play id and the
        // entitlement write is skipped rather than corrupting the shared
        // Firestore field.
        val type = SubscriptionProductType.fromPlayProductId(productId)

        return SubscriptionProduct(
            playProductId = productId,
            subscriptionType = type?.subscriptionType ?: productId,
            // The pinned plan label, **not** Play's console product title.
            // Play's title is free-form operator text — the live listing's
            // is `"subscription app kornerz (Kornerz Social Network)"` —
            // and iOS renders the plan's own label rather than the
            // StoreKit product's display name
            // (`kornerz/Kornerz/InAppPurchase/SubscriptionScreen.swift:80`).
            // Keeping the console string out of the model entirely means
            // no screen can accidentally render it: the plan card reads
            // `SubscriptionProduct.displayTitle`, which is the pinned
            // label. Play's title is retained only as a fallback for a
            // product outside the pinned catalog, where there is no label
            // to fall back on.
            title = type?.label ?: title.ifBlank { name },
            priceLabel = paidPhase?.formattedPrice.orEmpty(),
            periodLabel = paidPhase.billingPeriodLabel(),
            pricePerMonthEquivalent = if (type == SubscriptionProductType.YEARLY && paidPhase != null) {
                formatLocalizedMicros(
                    micros = paidPhase.priceAmountMicros / 12L,
                    currencyCode = paidPhase.priceCurrencyCode,
                ) + PER_MONTH_SUFFIX
            } else {
                null
            },
            isTrialEligible = hasFreeTrial,
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

        /**
         * How long a Play connection attempt may take before it is
         * treated as failed.
         *
         * The bind is normally sub-second; the bound exists so a
         * Play Store that is mid-update — or absent — cannot suspend a
         * caller indefinitely. Ten seconds is long enough to ride out a
         * slow bind and short enough that the user sees an error rather
         * than an endless spinner.
         */
        const val CONNECTION_TIMEOUT_MILLIS = 10_000L
    }
}

/**
 * A Play subscription purchase projected out of the SDK. Carries no
 * `com.android.billingclient` type, so it can cross into
 * `feature/subscription`'s repository layer and be faked in unit tests.
 *
 * @param obfuscatedAccountId the app-account id that was attached to the
 *   purchase at checkout, via
 *   `BillingFlowParams.Builder.setObfuscatedAccountId`. `null` for a
 *   purchase made outside the app (a Play Console promo redemption) or
 *   on a build that predates the id being attached.
 *
 *   This is the only signal that ties a Play purchase to *this* app
 *   account, and it is what makes the restore flow able to tell
 *   "this device's Play account has never bought a Kornerz subscription"
 *   apart from "this device's Play account bought one, but for a
 *   different Kornerz account". See
 *   [com.kornerz.feature.subscription.data.SubscriptionRepository.restore].
 * @param isAcknowledged whether Play already considers this purchase
 *   acknowledged. A restored purchase that is *not* acknowledged must be
 *   acknowledged before the entitlement is granted, or Google refunds it
 *   within three days and silently revokes the entitlement.
 */
data class PurchaseRecord(
    val token: String,
    val productId: String,
    val purchaseTimeMillis: Long,
    val isAcknowledged: Boolean,
    val obfuscatedAccountId: String? = null,
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
 * do not exist in the pinned 8.0.0 surface; they fall through to
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
    -> SubscriptionError.ProductUnavailable

    // ITEM_ALREADY_OWNED is **not** a catalog failure: the plan is
    // configured correctly, the device's Play account simply already
    // holds it. It gets its own case so the app can tell "the plans are
    // broken" apart from "you already own this", and so the
    // already-owned case can be classified further against the purchase
    // library's account id.
    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED,
    -> SubscriptionError.ItemAlreadyOwned

    BillingClient.BillingResponseCode.USER_CANCELED -> SubscriptionError.UserCancelled

    BillingClient.BillingResponseCode.NETWORK_ERROR,
    // SERVICE_TIMEOUT is deprecated in PBL 8.0.0 — Play has not returned
    // it since PBL 6.0, where timeouts became SERVICE_UNAVAILABLE. The
    // constant is kept in the mapping because the `play-billing` spec's
    // table names it explicitly under `NetworkError`, and the code is
    // still a valid value on the wire for older Play Store clients.
    @Suppress("DEPRECATION")
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
