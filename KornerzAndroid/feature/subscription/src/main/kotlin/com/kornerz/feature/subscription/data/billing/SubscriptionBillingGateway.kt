package com.kornerz.feature.subscription.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The seam every consumer of billing talks to, and the reason
 * `feature/subscription` can ship a working paywall on the `dev` flavor
 * while `com.kornerz.kornerz.dev` is not a registered Play listing.
 *
 * **Why this exists.** Google publishes no sandbox, mock mode, or local
 * catalog for Play Billing — there is no equivalent of the Xcode
 * `.storekit` file iOS uses (`Kornerz-SKConfig.storekit`). The only
 * Play-provided escape hatches are Play Console **License testing**,
 * **testing tracks**, and **static test cards**, and all three require an
 * app that already exists in Play Console with a matching package name and
 * upload certificate. Until then, every Play call on `dev` legitimately
 * fails, and without a seam the only honest UI is an error screen.
 *
 * **Why the surface is Play-free.** The interface deliberately does *not*
 * mirror `BillingClientApi`'s signatures: `launchPurchase` returns
 * `Result<Unit>` rather than Play's `BillingResult`, so a simulated
 * implementation can exist without importing `com.android.billingclient`
 * at all. `BillingClientApi` remains the single file in the app that
 * imports Play types.
 *
 * Two implementations exist, selected by the `env` flavor dimension:
 *
 * | Variant | Binding | Behaviour |
 * |---|---|---|
 * | `dev` | `DevBillingGateway` (`src/dev`) | Two hard-coded plans, a simulated purchase, in-memory restore |
 * | `prod` | `BillingClientApi` (`src/main`) | Real Google Play Billing |
 *
 * `SubscriptionRepository`, `SubscriptionViewModel`, and the paywall gate
 * are flavor-agnostic: they only ever see the plain-data types declared
 * here.
 */
interface SubscriptionBillingGateway {

    /**
     * Client readiness plus a typed disconnect reason.
     *
     * The simulated gateway reports `Ready` with a synthetic license code,
     * because there is no license check to fail when no Play listing is
     * involved. Consumers must treat the code as data and never branch on
     * it — both build types are signed with the debug keystore, so the
     * real code is environment-dependent anyway.
     */
    val connectionState: StateFlow<BillingConnectionState>

    /**
     * Purchase outcomes. Mirrors the real wrapper's
     * `PurchasesUpdatedListener`: the outcome arrives asynchronously and
     * the listener stays live for the client's lifetime, so the simulated
     * gateway deliberately delays its `Purchased` emission rather than
     * returning it synchronously — otherwise the ViewModel's
     * acknowledge-then-grant path would never be exercised on `dev`.
     */
    val purchaseOutcomes: Flow<BillingPurchaseResult>

    /**
     * Registers the Activity the real Play sheet needs. Ignored by the
     * simulated gateway, which presents no UI.
     */
    fun attachActivity(activity: Activity)

    /** Releases the Activity reference. Ignored by the simulated gateway. */
    fun detachActivity()

    /**
     * Opens the purchase sheet for [productId].
     *
     * Returns `Result.failure` with a typed
     * [com.kornerz.feature.subscription.data.SubscriptionException] when
     * Play refuses to present the sheet (no Activity attached, product
     * never resolved). Success only means *the sheet opened* — the
     * outcome still arrives on [purchaseOutcomes].
     */
    fun launchPurchase(productId: String, offerToken: String?): Result<Unit>

    /** Resolves the catalog for [ids]. */
    suspend fun queryProductDetails(
        ids: List<String>,
    ): Result<List<com.kornerz.core.model.SubscriptionProduct>>

    /** Reads the active, non-consumed subscription purchases on this device. */
    suspend fun queryPurchases(): Result<List<PurchaseRecord>>

    /** Acknowledges a purchase. A failure is `SubscriptionError.NotAcknowledged`. */
    suspend fun acknowledgePurchase(token: String): Result<Unit>

    /**
     * Re-derives the entitlement from local purchase state. Never creates a
     * purchase and never charges the user.
     */
    suspend fun restore(): Result<List<PurchaseRecord>>

    /** Releases the client. Idempotent. */
    fun endConnection()
}