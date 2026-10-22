package com.kornerz.feature.subscription.navigation

/**
 * Navigation contract for the P8 subscription surfaces. Mirrors the
 * per-feature route sealed classes used by P4 (`nooks`), P5 (`loops`),
 * and P6 (`profile`).
 *
 * Both routes are registered as **top-level** destinations on the root
 * `NavHost` (outside every tab's nested graph) so neither activates a
 * bottom tab and both hide the bottom bar — see the
 * `android-app-shell` spec and `AppShell`'s
 * `videocall/.*|notifications/list|profile/.*|community/admin/.*|subscription/.*`
 * regex.
 */
sealed class SubscriptionRoute(val route: String) {

    /** Inventory 11.1 host + 11.2 view — the plans / paywall screen. */
    data object Plans : SubscriptionRoute("subscription/plans")

    /** Inventory 11.3 — the post-purchase confirmation screen. */
    data object Success : SubscriptionRoute("subscription/success")
}