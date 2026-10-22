package com.kornerz.feature.shell

/**
 * Clause 3 of the paywall gate — "no active Play entitlement exists for
 * this user".
 *
 * Declared as an interface in `feature/shell` because the gate lives here
 * while the Play SDK lives in `feature/subscription`, and
 * `docs/architecture.md` §2.5 forbids one feature module from depending
 * on another: only `app` imports both. `app` supplies the binding
 * (`AndroidPlayEntitlementChecker`, backed by `SubscriptionRepository`), so
 * the shell stays free of Play vocabulary.
 *
 * This is the one `@Binds` the codebase gains in P8, and it earns its keep
 * for the reason `NetworkModule`'s `AuthApi` binding does: it is the seam
 * that keeps a Play SDK out of the shell.
 */
interface PlayEntitlementChecker {

    /**
     * `true` when this device holds an active, non-consumed subscription
     * purchase.
     *
     * Implementations must not throw: the gate treats an unreachable Play
     * service as "no active entitlement known" and lets the local expiry
     * remain the authority, rather than stranding a paying subscriber
     * behind a paywall.
     */
    suspend fun hasActiveSubscription(): Boolean
}