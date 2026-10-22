package com.kornerz.feature.subscription.data

/**
 * The failure taxonomy for the P8 premium / IAP vertical.
 *
 * Feature-scoped by design: no general `AppError` type exists anywhere
 * in this codebase, so P8 introduces the subscription-scoped equivalent
 * rather than inventing a global one. Mirrors the established
 * `AuthError` + `AuthException` pair (`core/network/.../auth/
 * AuthResult.kt:19-34` and `AuthRepository.kt:203-206`).
 *
 * Every case corresponds to one or more `BillingResultCode` values via
 * [com.kornerz.feature.subscription.data.billing.BillingClientApi]'s
 * mapping; no Play type escapes the wrapper, so consumers switch on
 * these alone.
 */
sealed interface SubscriptionError {

    /**
     * Play Billing could not be reached at all.
     *
     * From `SERVICE_UNAVAILABLE`, `SERVICE_DISCONNECTED`,
     * `BILLING_UNAVAILABLE`, `FEATURE_NOT_SUPPORTED`, and the
     * `Unavailable` connection state (no Play Store, or the
     * application id is not a registered Play listing — the `dev`
     * flavor's `com.kornerz.kornerz.dev` case).
     */
    data object BillingUnavailable : SubscriptionError

    /**
     * The catalog could not be resolved: `ITEM_UNAVAILABLE`,
     * `PRODUCT_NOT_AVAILABLE`, or an empty product list from Play.
     *
     * Usually means the two products are missing or misconfigured in
     * the Play Console, which is not under version control.
     */
    data object ProductUnavailable : SubscriptionError

    /**
     * The user dismissed the Play purchase sheet. A **normal** return
     * path, not a failure to report: the plans screen clears
     * `isPurchasing` and leaves `error` null.
     */
    data object UserCancelled : SubscriptionError

    /** From `NETWORK_ERROR`, `IO_ERROR`, `SERVICE_TIMEOUT`. */
    data object NetworkError : SubscriptionError

    /**
     * The purchase is in Play's deferred / deferred-upgrade state
     * (`PurchasesUpdatedListener` fired with a `PENDING` purchase). The
     * entitlement is **not** granted yet; it lands once Google resolves
     * the purchase.
     */
    data object PurchasePending : SubscriptionError

    /**
     * The purchase succeeded but `acknowledgePurchase` failed. Google
     * refunds any purchase left unacknowledged after three days, so this
     * **blocks** the success navigation rather than being swallowed —
     * deliberately stricter than iOS, which has no acknowledgement
     * concept because that is StoreKit's responsibility.
     */
    data object NotAcknowledged : SubscriptionError

    /** Any unmapped `BillingResultCode`, or a client-side exception. */
    data class Unknown(val message: String) : SubscriptionError
}

/**
 * Carries a [SubscriptionError] through a thrown channel so the
 * `runCatching { … await() }` wrapper shape every data-layer API uses
 * can surface a typed cause inside `Result.failure`.
 */
class SubscriptionException(
    val errorKind: SubscriptionError,
    message: String,
) : RuntimeException(message)