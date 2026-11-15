package com.kornerz.core.model

/**
 * Master switch for the whole Android in-app-purchase path.
 *
 * The Play Console listing for `com.kornerz.kornerz` now exists with the
 * two subscription products (`kornerz_subscription`,
 * `kornerz_subscription_yearly`), so the paywall can be presented and the
 * purchase sheet can be launched.
 *
 * When this is `false`:
 * - `PremiumGate` (in `feature/shell`) never presents the paywall.
 * - `BillingClientApi` (in `feature/subscription`) never builds a
 *   `BillingClient`, so nothing contacts Play at all.
 * - `SubscriptionRepository.reconcileEntitlement` skips the launch-time
 *   reconciliation, which would otherwise query Play for purchases.
 *
 * Flip it back to `false` — or delete it and the guards that read it — if
 * the Play listing is ever withdrawn.
 */
const val IAP_ENABLED: Boolean = true
