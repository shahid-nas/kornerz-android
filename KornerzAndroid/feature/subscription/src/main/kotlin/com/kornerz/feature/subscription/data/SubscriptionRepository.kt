package com.kornerz.feature.subscription.data

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.SubscriptionProduct
import com.kornerz.core.model.SubscriptionProductType
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.subscription.data.billing.PurchaseRecord
import com.kornerz.feature.subscription.data.billing.SubscriptionBillingGateway
import com.kornerz.feature.subscription.data.network.SubscriptionCloudFunctionsApi
import com.kornerz.feature.subscription.data.network.SubscriptionFirestoreApi
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The UDF-facing surface for the P8 premium vertical.
 *
 * Mirrors iOS `InAppManager` (`kornerz/Kornerz/InAppPurchase/
 * InAppManager.swift`) for the query/purchase/acknowledge/restore
 * operations, and iOS `AppHelper.isUserPremium`
 * (`kornerz/Kornerz/Helper/AppHelper.swift:39-45`) for the entitlement
 * derivation.
 *
 * ViewModels talk only to this repository, never to `BillingClientApi` or
 * a Firebase SDK type directly — the same repository boundary every
 * P0–P7 screen has.
 */
@Singleton
class SubscriptionRepository @Inject constructor(
    private val billingGateway: SubscriptionBillingGateway,
    private val firestoreApi: SubscriptionFirestoreApi,
    private val cloudFunctionsApi: SubscriptionCloudFunctionsApi,
    private val sessionRepository: SessionRepository,
) {

    /**
     * Resolves the two-product Play catalog for the plans screen.
     *
     * Wrapped in [AsyncValue] rather than `Result` because the screen's
     * render states dispatch on it (`Loading` → `Success` / `Error`),
     * matching how every other section in the codebase exposes async
     * data.
     */
    suspend fun observeCatalog(): AsyncValue<List<SubscriptionProduct>> =
        billingGateway.queryProductDetails(
            SubscriptionProductType.ALL.map { it.rawValue },
        ).fold(
            onSuccess = { AsyncValue.Success(it) },
            onFailure = { AsyncValue.Error(it) },
        )

    /**
     * Reads the active purchases held by this device. Backs the paywall
     * gate's "no active Play entitlement" clause.
     */
    suspend fun queryPurchases(): Result<List<PurchaseRecord>> =
        billingGateway.queryPurchases()

    /**
     * Re-derives the entitlement from Play's local purchase history.
     * **Never** creates a purchase and **never** charges the user — the
     * Android equivalent of iOS `InAppManager.restore` (`:103-109`).
     */
    suspend fun restore(): Result<List<PurchaseRecord>> = billingGateway.restore()

    /**
     * Acknowledges a purchase. Called before any entitlement write; a
     * failure surfaces as [SubscriptionError.NotAcknowledged] and blocks
     * the success navigation, because Google refunds anything left
     * unacknowledged after three days.
     */
    suspend fun acknowledge(token: String): Result<Unit> =
        billingGateway.acknowledgePurchase(token)

    /**
     * Writes the entitlement to `Users/{email}` with
     * `subscriptionPlatform = "Android"`, then refreshes the session cache
     * so `User.isUserPremiumNow` reflects the purchase immediately.
     *
     * The session is consulted first, for two reasons the
     * `premium-entitlement` spec names: an anonymous session must fail
     * cleanly **without issuing a Firestore write**, and the write must
     * land on the signed-in user's own document. A mismatched [email] is
     * refused rather than trusted, because `Users/{email}` is a
     * cross-account write.
     */
    suspend fun grantEntitlement(
        email: String,
        plan: SubscriptionProductType,
        expiry: Instant,
    ): Result<Unit> {
        authenticatedEmail()?.let { sessionEmail ->
            if (!sessionEmail.equals(email, ignoreCase = true)) {
                return Result.failure(
                    SubscriptionException(
                        SubscriptionError.Unknown(
                            "Refusing to grant entitlement to $email while signed in as " +
                                sessionEmail,
                        ),
                        "Subscription email does not match the signed-in user",
                    ),
                )
            }
        } ?: return anonymousSessionFailure("grantEntitlement")

        val write = firestoreApi.setSubscription(
            email = email,
            expiryDate = expiry,
            planId = plan.rawValue,
            platform = ENTITLEMENT_PLATFORM,
        )
        if (write.isFailure) return write

        return refreshCachedUser(email)
    }

    /**
     * Deletes the three subscription fields, then refreshes the session
     * cache so `isUserPremiumNow` becomes `false` for the session.
     */
    suspend fun revokeEntitlement(email: String): Result<Unit> {
        authenticatedEmail() ?: return anonymousSessionFailure("revokeEntitlement")

        val clear = firestoreApi.clearSubscription(email)
        if (clear.isFailure) return clear

        return refreshCachedUser(email)
    }

    /**
     * The signed-in user's email, resolved from
     * `SessionRepository.data` exactly as
     * `GamificationRepository.getUserPoints()` resolves the signed-in user
     * — or `null` when the session is anonymous or the email is blank.
     */
    private suspend fun authenticatedEmail(): String? =
        (sessionRepository.data.first() as? SessionState.Authenticated)
            ?.user
            ?.email
            ?.takeIf { it.isNotBlank() }

    private fun anonymousSessionFailure(operation: String): Result<Unit> = Result.failure(
        SubscriptionException(
            SubscriptionError.Unknown("Anonymous session; $operation refused"),
            "Anonymous session; $operation refused",
        ),
    )

    /**
     * Re-reads the authoritative user document from Firestore and writes it
     * through [SessionRepository.updateUser], so the cached `User` never
     * diverges from the server for longer than one purchase flow.
     *
     * The whole cached user is replaced rather than patched: a field-wise
     * merge would let a stale subscription field survive the refresh.
     */
    suspend fun refreshCachedUser(email: String): Result<Unit> {
        val authoritative = firestoreApi.fetchUser(email)
            ?: return Result.failure(
                SubscriptionException(
                    SubscriptionError.Unknown("No user document at $email"),
                    "No user document at $email",
                ),
            )
        return sessionRepository.updateUser { authoritative }
    }

    /**
     * Server-side reconciliation, run once per app launch **before** the
     * premium gate is evaluated.
     *
     * This is what makes the `docs/project-brief.md:264` RTDN-delay risk
     * manageable: a delayed RTDN is corrected on the next launch rather
     * than persisting indefinitely. See the `subscription-verification`
     * spec.
     *
     * 1. Skip entirely when the local state says free **and** the device
     *    holds no active Play purchase — nothing to reconcile, so no
     *    network call is made.
     * 2. Otherwise verify the most recent local purchase. On
     *    `isActive == false`, clear the entitlement locally and
     *    server-side; on `isActive == true` with a server expiry, let the
     *    server's value overwrite the locally-derived one.
     * 3. On `Result.failure`, leave local state untouched — an
     *    unreachable backend must never cost a paying subscriber their
     *    premium state.
     */
    suspend fun reconcileEntitlement(): Result<Unit> {
        val user = (sessionRepository.data.first() as? SessionState.Authenticated)?.user
            ?: return Result.failure(
                SubscriptionException(
                    SubscriptionError.Unknown("Anonymous session; nothing to reconcile"),
                    "Anonymous session; nothing to reconcile",
                ),
            )
        val email = user.email
        if (email.isBlank()) {
            return Result.failure(
                SubscriptionException(
                    SubscriptionError.Unknown("Authenticated session with a blank email"),
                    "Authenticated session with a blank email",
                ),
            )
        }

        // A Play failure here is indistinguishable from "cannot ask", so
        // it is non-destructive: leave local state alone and let the gate
        // keep enforcing the local expiry.
        val purchases = billingGateway.queryPurchases().getOrElse {
            return Result.failure(it)
        }

        if (!user.isUserPremiumNow && purchases.isEmpty()) return Result.success(Unit)

        val latest = purchases.maxByOrNull { it.purchaseTimeMillis }
        if (latest == null) {
            // Locally premium but Play reports nothing — a Play-side
            // revocation whose RTDN has not landed, or a purchase tied to
            // a different Play account. No token to verify, so the local
            // expiry remains the authority.
            return Result.success(Unit)
        }

        return cloudFunctionsApi.verifyPurchase(latest.token, latest.productId).fold(
            onSuccess = { verdict ->
                if (verdict.isActive) {
                    val serverExpiry = verdict.expiryDate
                        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        ?: return@fold Result.success(Unit)
                    grantEntitlement(
                        email = email,
                        plan = SubscriptionProductType.fromRawValue(user.subscriptionType)
                            ?: SubscriptionProductType.fromRawValue(latest.productId)
                            ?: SubscriptionProductType.MONTHLY,
                        expiry = serverExpiry,
                    )
                } else {
                    revokeEntitlement(email)
                }
            },
            onFailure = { Result.failure(it) },
        )
    }
}

/**
 * Derives the entitlement expiry for a purchase Play has just confirmed.
 *
 * Play's `Purchase` carries the purchase time but not the renewal time, so
 * the expiry is computed from the plan's billing period: a monthly
 * purchase grants 30 days, a yearly purchase 365. Mirrors what iOS reads
 * back from the App Store transaction once `product.purchase()` succeeds.
 *
 * Deliberately a client-side estimate: the server's `expiryTimeMillis`
 * from `verifyPurchase` is authoritative and overwrites this during
 * reconciliation, so the local value only has to be right for the
 * duration of one purchase flow.
 */
fun SubscriptionProductType.deriveExpiryFrom(purchaseTimeMillis: Long): Instant {
    val purchaseInstant = Instant.fromEpochMilliseconds(purchaseTimeMillis)
    val now = Clock.System.now()
    // A future-dated purchase timestamp would otherwise produce an expiry
    // beyond the user's entitlement; clamp to now.
    val start = if (purchaseInstant > now) now else purchaseInstant
    return when (this) {
        SubscriptionProductType.MONTHLY -> start.plus(30.days)
        SubscriptionProductType.YEARLY -> start.plus(365.days)
    }
}