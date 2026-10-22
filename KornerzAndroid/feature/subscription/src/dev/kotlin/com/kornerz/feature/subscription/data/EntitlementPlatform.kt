package com.kornerz.feature.subscription.data

/**
 * The value written to `Users/{email}.subscriptionPlatform` when this build
 * grants an entitlement.
 *
 * Resolved per flavor rather than hard-coded in `SubscriptionRepository`:
 *
 * | Variant | Value | Meaning |
 * |---|---|---|
 * | `prod` | `Android` | A real Google Play purchase, matching iOS's `"iOS"` |
 * | `dev` | `AndroidDev` | A simulated purchase from `DevBillingGateway` |
 *
 * **`AndroidDev` exists so simulated entitlements are greppable.** The
 * `kornerz-72fba` project is shared with the iOS app, and premium is
 * derived from `subscriptionExpiryDate` on both platforms — so a
 * simulated subscription really would make that user read as premium in
 * the iOS build too. Writing a distinct value makes every fake entitlement
 * identifiable with one Firestore query, so they can be found and cleared
 * when Play goes live.
 *
 * It is still only ever **written, never branched on** in the entitlement
 * derivation — the same rule `subscriptionPlatform = "Android"` already
 * followed. Nothing in either app reads this field to decide premium.
 */
internal const val ENTITLEMENT_PLATFORM = "AndroidDev"