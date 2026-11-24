package com.kornerz.feature.subscription.data

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.IAP_ENABLED
import com.kornerz.core.model.SubscriptionDiagnostics
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
import java.security.MessageDigest
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
            SubscriptionProductType.ALL.map { it.playProductId },
        ).fold(
            onSuccess = { AsyncValue.Success(it) },
            onFailure = { AsyncValue.Error(it) },
        )

    /**
     * Classifies an `ITEM_ALREADY_OWNED` failure.
     *
     * Play refuses to open the purchase sheet when this Google account
     * already holds the subscription, and the response says nothing more
     * than that. The app, though, can say a great deal more — the purchase
     * library carries each purchase's obfuscated account id, so the
     * purchase can be attributed to a Kornerz account. That is the
     * difference between:
     *
     *  - [AlreadyOwnedClassification.OwnedByThisAccount] — the user paid
     *    for this already; the only thing missing is the entitlement
     *    write, which **restore** performs. The right instruction is
     *    "tap Restore Purchase", not "sign in elsewhere".
     *  - [AlreadyOwnedClassification.OwnedByAnotherAccount] — the purchase
     *    was made for a different Kornerz account. This is the dead end:
     *    the only way forward is to sign out and sign in as that account.
     *  - [AlreadyOwnedClassification.Unknown] — the purchase library could
     *    not be read, or it holds no attributable purchase. The app falls
     *    back to the generic "already owned" message rather than guessing.
     *
     * A purchase with **no** obfuscated account id is treated as
     * un-attributable and lands in [AlreadyOwnedClassification.Unknown]:
     * it could have been made by anyone, so the app must not claim it for
     * the signed-in account nor accuse the signed-in account of being
     * wrong.
     */
    suspend fun classifyAlreadyOwned(): AlreadyOwnedClassification {
        if (!IAP_ENABLED) return AlreadyOwnedClassification.Unknown

        val email = authenticatedEmail() ?: return AlreadyOwnedClassification.Unknown

        val records = billingGateway.restore().getOrElse {
            return AlreadyOwnedClassification.Unknown
        }

        val purchases = records.filter { record ->
            SubscriptionProductType.fromPlayProductId(record.productId) != null
        }
        if (purchases.isEmpty()) return AlreadyOwnedClassification.Unknown

        val owned = purchases.filter { record ->
            record.obfuscatedAccountId == null ||
                record.obfuscatedAccountId == obfuscatedAccountId(email)
        }
        // Every purchase on this Play account belongs to a different
        // Kornerz account — the wrong-account dead end.
        if (owned.isEmpty()) return AlreadyOwnedClassification.OwnedByAnotherAccount

        // A purchase for this account exists. If every one of them carries
        // an account id, the attribution is certain; if some carry none,
        // the app cannot rule out that the one blocking the purchase is
        // another account's.
        val attributable = purchases.all { record -> record.obfuscatedAccountId != null }
        return if (attributable) {
            AlreadyOwnedClassification.OwnedByThisAccount
        } else {
            AlreadyOwnedClassification.Unknown
        }
    }

    /**
     * The obfuscated account id to attach to a purchase made by the
     * signed-in user, or `null` when there is no session.
     *
     * Exposed so the purchase flow can bind the Play purchase to the app
     * account at checkout. Play has no notion of the app's accounts, so
     * this binding is the only thing that lets a later restore tell
     * "this Play account bought a Kornerz subscription *for you*" apart
     * from "this Play account bought one for someone else".
     */
    suspend fun obfuscatedAccountIdForSession(): String? {
        val email = authenticatedEmail() ?: return null
        return obfuscatedAccountId(email)
    }

    /**
     * Re-derives the entitlement from the Play account's purchase library.
     * **Never** creates a purchase and **never** charges the user — the
     * Android counterpart of iOS `InAppManager.restore` (`:103-109`).
     *
     * The restore path differs from the purchase path in three ways, and
     * all three matter:
     *
     * 1. **The purchase is attributed to an app account.** Play knows
     *    nothing about the app's accounts, so a purchase found on this
     *    device's Play account may belong to a different Kornerz user.
     *    The obfuscated account id attached at checkout is what settles
     *    it — see [RestoreOutcome].
     * 2. **The purchase is acknowledged before the entitlement is
     *    granted.** A restored purchase that was never acknowledged is
     *    refunded by Google within three days, which would silently
     *    revoke the entitlement restore just wrote.
     * 3. **A missing purchase is a normal outcome**, not an error, and is
     *    reported as [RestoreOutcome.NothingFound] so the screen can say
     *    so calmly.
     */
    suspend fun restore(): Result<RestoreOutcome> {
        if (!IAP_ENABLED) return Result.success(RestoreOutcome.NothingFound)

        val email = authenticatedEmail()
            ?: return Result.failure(
                SubscriptionException(
                    SubscriptionError.Unknown("Anonymous session; restore refused"),
                    "Anonymous session; restore refused",
                ),
            )

        val purchases = billingGateway.restore().getOrElse { return Result.failure(it) }

        val records = purchases.filter { record ->
            SubscriptionProductType.fromPlayProductId(record.productId) != null
        }
        if (records.isEmpty()) {
            // No purchase this device's Play account owns for either of
            // our products. The common case for a user who never
            // subscribed, and also the case for a user whose Play account
            // is simply the wrong one — which the user is told about
            // below, when a purchase exists but belongs to another account.
            return Result.success(RestoreOutcome.NothingFound)
        }

        // A purchase that names a different app account must not be
        // granted to the signed-in user. Silently granting it would let
        // one Kornerz account claim another's subscription.
        val owned = records.filter { record ->
            record.obfuscatedAccountId == null ||
                record.obfuscatedAccountId == obfuscatedAccountId(email)
        }
        if (owned.isEmpty()) {
            // Every purchase on this Play account belongs to a different
            // Kornerz account. This is the Android-specific restore
            // failure: the user is signed in to the right app account but
            // the wrong Google account on the device.
            return Result.success(RestoreOutcome.WrongAccount)
        }

        val latest = owned.maxByOrNull { it.purchaseTimeMillis }
            ?: return Result.success(RestoreOutcome.NothingFound)

        val plan = SubscriptionProductType.fromPlayProductId(latest.productId)
            ?: return Result.success(RestoreOutcome.NothingFound)

        // Acknowledge before granting, exactly as the purchase path does.
        // Play refunds an unacknowledged purchase after three days, which
        // would revoke the entitlement this call is about to write.
        if (!latest.isAcknowledged) {
            val ack = acknowledge(latest.token)
            if (ack.isFailure) {
                return Result.failure(
                    SubscriptionException(
                        SubscriptionError.NotAcknowledged,
                        "Could not acknowledge the restored purchase",
                    ),
                )
            }
        }

        val expiry = resolveRestoredExpiry(latest, plan)

        val grant = grantEntitlement(
            email = email,
            plan = plan,
            expiry = expiry,
        )
        if (grant.isFailure) {
            return Result.failure(
                grant.exceptionOrNull()
                    ?: SubscriptionException(
                        SubscriptionError.Unknown("Grant failed"),
                        "Grant failed",
                    ),
            )
        }

        return Result.success(RestoreOutcome.Restored(plan))
    }

    /**
     * The expiry to write for a restored purchase.
     *
     * The server's verdict wins when it is available: `verifySubscription`
     * returns Play's real `expiryTimeMillis`, which is the only figure
     * that accounts for renewals, upgrades and downgrades.
     *
     * The Cloud Function is **external to this repository**, so it may not
     * be deployed. When it is not, the client falls back to
     * [deriveActiveExpiryFrom] — one full period from now — rather than
     * deriving from the purchase time. The purchase time is the
     * *original* purchase date, so `purchaseTime + 30 days` would be
     * already expired for any subscription that has renewed, which would
     * write an entitlement that is dead the moment it lands.
     *
     * A server verdict of `isActive == false` is treated as a failure to
     * verify rather than as a revocation: the restore falls back to the
     * client estimate instead of clearing an entitlement the user
     * demonstrably holds. Revocation is [reconcileEntitlement]'s job.
     */
    private suspend fun resolveRestoredExpiry(
        record: PurchaseRecord,
        plan: SubscriptionProductType,
    ): Instant {
        val verdict = cloudFunctionsApi.verifyPurchase(record.token, record.productId)
            .getOrNull()
        if (verdict != null && verdict.isActive) {
            verdict.expiryDate
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?.let { return it }
        }
        return plan.deriveActiveExpiryFrom()
    }

    /**
     * Whether the signed-in account holds an active subscription **on this
     * device's Play account**.
     *
     * Backs the paywall gate's "no active Play entitlement" clause.
     *
     * The ownership filter matters here as much as it does in
     * [restore]: Play knows nothing about the app's accounts, so a
     * purchase on this device may belong to a different Kornerz user.
     * Counting it would suppress the paywall for a user who has no
     * entitlement at all.
     */
    suspend fun hasActiveSubscriptionForSession(): Boolean {
        val email = authenticatedEmail() ?: return false
        val purchases = billingGateway.queryPurchases().getOrNull().orEmpty()
        val owned = purchases.filter { record ->
            SubscriptionProductType.fromPlayProductId(record.productId) != null &&
                (
                    record.obfuscatedAccountId == null ||
                        record.obfuscatedAccountId == obfuscatedAccountId(email)
                    )
        }
        // This is clause 3 of the paywall gate: "the device holds no active
        // Play purchase". A `true` here suppresses the paywall, so the
        // purchases that produced it are logged — an unexpired-looking
        // purchase on the device would otherwise be invisible.
        SubscriptionDiagnostics.log(
            "2-reconcile",
            "clause-3 play entitlement check for $email — " +
                "purchases=${purchases.size} owned=${owned.size} " +
                "hasActiveSubscription=${owned.isNotEmpty()}",
        )
        return owned.isNotEmpty()
    }

    /**
     * The obfuscated account id attached to purchases made by [email].
     *
     * Play requires this value to be **non-reversible and non-PII**: it
     * is stored on Google's side and surfaced in Play Console tooling.
     * A SHA-256 digest of the lowercased email satisfies both — the
     * digest is stable across devices (so a purchase made on one device
     * is recognised on another) and reveals nothing on its own.
     *
     * The digest is truncated to 32 hex characters: Play's documented
     * limit for this field is 64 characters, and a shorter value keeps
     * the Play Console's purchase columns readable.
     */
    private fun obfuscatedAccountId(email: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
        return digest.take(OBFUSCATED_ID_BYTE_COUNT)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

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
            subscriptionType = plan.subscriptionType,
            platform = ENTITLEMENT_PLATFORM,
        )
        if (write.isFailure) {
            SubscriptionDiagnostics.fail(
                "3-grant",
                "the entitlement could not be written for $email (${write.exceptionOrNull()?.message})",
            )
            return write
        }

        SubscriptionDiagnostics.log(
            "3-grant",
            "the entitlement was written for $email — expiry=$expiry " +
                "type=${plan.subscriptionType} platform=$ENTITLEMENT_PLATFORM",
        )

        return refreshCachedUser(email)
    }

    /**
     * Deletes the three subscription fields, then refreshes the session
     * cache so `isUserPremiumNow` becomes `false` for the session.
     */
    suspend fun revokeEntitlement(email: String): Result<Unit> {
        authenticatedEmail() ?: return anonymousSessionFailure("revokeEntitlement")

        val clear = firestoreApi.clearSubscription(email)
        if (clear.isFailure) {
            SubscriptionDiagnostics.fail(
                "4-revoke",
                "the entitlement could not be cleared for $email " +
                    "(${clear.exceptionOrNull()?.message})",
            )
            return clear
        }

        SubscriptionDiagnostics.log(
            "4-revoke",
            "the entitlement was cleared for $email — the user is free again",
        )

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
        // IAP is switched off: there is no Play purchase to reconcile, so
        // the launch-time reconciliation is skipped entirely.
        if (!IAP_ENABLED) return Result.success(Unit)

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
            SubscriptionDiagnostics.fail(
                "2-reconcile",
                "the Play purchase library could not be queried " +
                    "(${it.message}); the local expiry remains the authority",
            )
            return Result.failure(it)
        }

        SubscriptionDiagnostics.log(
            "2-reconcile",
            "queried the Play purchase library for $email — " +
                "purchases=${purchases.size} " +
                "localExpiry=${user.subscriptionExpiryDate} " +
                "localIsUserPremiumNow=${user.isUserPremiumNow}",
        )

        if (!user.isUserPremiumNow && purchases.isEmpty()) {
            SubscriptionDiagnostics.skip(
                "2-reconcile",
                "nothing to reconcile — the user is not premium and the device holds no purchase",
            )
            return Result.success(Unit)
        }

        // Only purchases made for *this* account may drive the
        // reconciliation. A purchase that names a different app account
        // belongs to another user, and acting on it here could revoke
        // this user's entitlement or grant another user's plan to them.
        val owned = purchases.filter { record ->
            record.obfuscatedAccountId == null ||
                record.obfuscatedAccountId == obfuscatedAccountId(email)
        }

        val latest = owned.maxByOrNull { it.purchaseTimeMillis }
        if (latest == null) {
            // Locally premium but Play reports nothing — a Play-side
            // revocation whose RTDN has not landed, or a purchase tied to
            // a different Play account. No token to verify, so the local
            // expiry remains the authority.
            SubscriptionDiagnostics.skip(
                "2-reconcile",
                "no owned purchase to verify — the local expiry remains the authority " +
                    "(owned=${owned.size} of ${purchases.size} purchases)",
            )
            return Result.success(Unit)
        }

        return cloudFunctionsApi.verifyPurchase(latest.token, latest.productId).fold(
            onSuccess = { verdict ->
                SubscriptionDiagnostics.log(
                    "2-reconcile",
                    "the server verified the purchase — isActive=${verdict.isActive} " +
                        "expiryDate=${verdict.expiryDate} autoRenewing=${verdict.autoRenewing} " +
                        "acknowledged=${verdict.acknowledged}",
                )
                if (verdict.isActive) {
                    val serverExpiry = verdict.expiryDate
                        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        ?: run {
                            SubscriptionDiagnostics.fail(
                                "2-reconcile",
                                "the server reported an active subscription with an " +
                                    "unparseable expiry (${verdict.expiryDate}); " +
                                    "the local expiry remains the authority",
                            )
                            return@fold Result.success(Unit)
                        }
                    grantEntitlement(
                        email = email,
                        // The stored Firestore value is the canonical
                        // cross-platform `subscriptionType`; the Play
                        // product id is the fallback when the document
                        // carries no type (e.g. a purchase made before the
                        // type field existed).
                        plan = SubscriptionProductType.fromSubscriptionType(user.subscriptionType)
                            ?: SubscriptionProductType.fromPlayProductId(latest.productId)
                            ?: SubscriptionProductType.MONTHLY,
                        expiry = serverExpiry,
                    )
                } else {
                    revokeEntitlement(email)
                }
            },
            onFailure = {
                SubscriptionDiagnostics.fail(
                    "2-reconcile",
                    "the purchase could not be verified (${it.message}); " +
                        "the local expiry remains the authority",
                )
                Result.failure(it)
            },
        )
    }

    private companion object {
        /**
         * The number of digest bytes kept for the obfuscated account id.
         * 32 bytes is 64 hex characters, which is Play's documented
         * maximum for `setObfuscatedAccountId`.
         */
        const val OBFUSCATED_ID_BYTE_COUNT = 16
    }
}

/**
 * The outcome of a restore attempt.
 *
 * A restore is a **negotiation**, not a boolean: the three outcomes below
 * each need different wording on screen, and collapsing them into a
 * single "failed" is what made the restore button look inert.
 *
 * | Outcome | Meaning | What the user should be told |
 * |---|---|---|
 * | [Restored] | A purchase for this account was found and the entitlement was written | Nothing — the paywall is dismissed |
 * | [NothingFound] | This device's Play account owns no Kornerz subscription for this app account | "No active subscription found" |
 * | [WrongAccount] | This device's Play account owns a Kornerz subscription, but it was bought for a *different* Kornerz account | "Sign in to the Google account that made the purchase" |
 *
 * [WrongAccount] is the Android-specific failure iOS does not have: on
 * Android a subscription belongs to the **Google Play account** on the
 * device, not to the app account, so a user who is signed in to the right
 * Kornerz account on the wrong Google account sees a paywall they have
 * already paid for.
 */
sealed interface RestoreOutcome {

    /**
     * The entitlement was restored for the signed-in account.
     *
     * @param plan the restored plan.
     */
    data class Restored(val plan: SubscriptionProductType) : RestoreOutcome

    /**
     * No purchase was found for this account. A normal outcome, not an
     * error — a user who never subscribed taps restore and gets this.
     */
    data object NothingFound : RestoreOutcome

    /**
     * A purchase exists on this device's Play account, but it was made
     * for a different Kornerz account.
     */
    data object WrongAccount : RestoreOutcome
}

/**
 * What an `ITEM_ALREADY_OWNED` failure actually means.
 *
 * Play's response only says "this Google account already owns it". The
 * app can say more, because the purchase library carries the obfuscated
 * account id of the Kornerz account each purchase was made for. That
 * extra knowledge is what separates a recoverable situation from a dead
 * end — see [SubscriptionRepository.classifyAlreadyOwned].
 */
sealed interface AlreadyOwnedClassification {

    /**
     * The purchase belongs to the **signed-in** Kornerz account.
     *
     * The user has already paid; only the entitlement write is missing,
     * and **restore** performs it. The right instruction is "tap Restore
     * Purchase", not "sign in elsewhere".
     */
    data object OwnedByThisAccount : AlreadyOwnedClassification

    /**
     * The purchase belongs to a **different** Kornerz account.
     *
     * The dead end: neither subscribing nor restoring can succeed from
     * this session, and the only way forward is to sign out and sign in
     * as the account that made the purchase.
     */
    data object OwnedByAnotherAccount : AlreadyOwnedClassification

    /**
     * The purchase could not be attributed to any Kornerz account.
     *
     * The purchase library was unreadable, held no attributable purchase,
     * or held a purchase made outside the app (which carries no
     * obfuscated account id). The app must not guess in either
     * direction, so it falls back to a generic message.
     */
    data object Unknown : AlreadyOwnedClassification
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
 *
 * ## The renewal case
 *
 * `Purchase.purchaseTime` is the time of the **original** purchase, not
 * the last renewal, so `purchaseTime + 30 days` is *already expired* for
 * a monthly subscription that has been renewing for two months. For a
 * freshly-made purchase the plain derivation is exactly right, because
 * the purchase time is the start of the current period.
 *
 * The two callers need different behaviour:
 *
 * - **Purchase flow** — the purchase was just made, so the current period
 *   starts at the purchase time and the plain derivation is correct.
 * - **Restore** — the purchase may be months old and renewing. Play only
 *   reports subscriptions that are **currently active**, so the
 *   entitlement is valid *now*; the remaining period is best estimated
 *   as one full period from now.
 *
 * [deriveActiveExpiryFrom] implements the second rule.
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

/**
 * The entitlement expiry for a purchase that Play reports as **currently
 * active**, whatever its age.
 *
 * A subscription that Play still lists as owned is in its paid period,
 * so the entitlement runs for at least the remainder of that period.
 * The purchase time cannot be used to compute the remainder — it is the
 * original purchase date — so the estimate is one full period from now.
 *
 * This is deliberately conservative in the safe direction: the
 * authoritative `expiryTimeMillis` from the `verifySubscription` Cloud
 * Function overwrites it as soon as that function is deployed, and
 * `reconcileEntitlement()` re-derives the value on every launch.
 */
fun SubscriptionProductType.deriveActiveExpiryFrom(): Instant {
    val now = Clock.System.now()
    return when (this) {
        SubscriptionProductType.MONTHLY -> now.plus(30.days)
        SubscriptionProductType.YEARLY -> now.plus(365.days)
    }
}