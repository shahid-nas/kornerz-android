package com.kornerz.core.model

/**
 * TEMPORARY — master switch for the whole Android in-app-purchase path.
 *
 * The app is not published on Google Play yet, so Play Billing has no
 * listing for `com.kornerz.kornerz`: every Play call fails with
 * `queryProductDetails failed: ...`, and the mandatory paywall would then
 * lock a tester out of the app with no way out.
 *
 * While this is `false`:
 * - `PremiumGate` (in `feature/shell`) never presents the paywall.
 * - `BillingClientApi` (in `feature/subscription`) never builds a
 *   `BillingClient`, so nothing contacts Play at all.
 * - `SubscriptionRepository.reconcileEntitlement` skips the launch-time
 *   reconciliation, which would otherwise query Play for purchases.
 *
 * Flip it back to `true` — or delete it and the guards that read it —
 * once the Play Console listing and the two products exist.
 */
const val IAP_ENABLED: Boolean = false
