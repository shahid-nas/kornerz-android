package com.kornerz.feature.shell

/**
 * Server-side entitlement reconciliation, run once per app launch
 * **before** the paywall gate is evaluated.
 *
 * Declared in `feature/shell` for the same reason as
 * [PlayEntitlementChecker]: the implementation
 * (`SubscriptionRepository.reconcileEntitlement`) lives in
 * `feature/subscription`, which `feature/shell` may not depend on
 * (`docs/architecture.md` §2.5). `app` supplies the binding.
 *
 * Ordering matters: reconciling first means the gate evaluates against the
 * server's verdict rather than a stale local expiry, which is what makes
 * a delayed RTDN self-correcting instead of permanent
 * (`docs/project-brief.md:264`).
 *
 * Implementations must not throw. A failure leaves local state untouched by
 * design — an unreachable backend must never cost a paying subscriber
 * their premium state.
 */
interface SubscriptionEntitlementReconciler {
    suspend fun reconcile()
}