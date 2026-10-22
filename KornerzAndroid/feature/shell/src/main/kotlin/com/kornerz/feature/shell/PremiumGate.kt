package com.kornerz.feature.shell

import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The paywall gate. Decides whether to present the subscription plans
 * screen by evaluating **five** clauses, all of which must pass.
 *
 * Reproduces iOS's four-clause guard, which appears identically in
 * `AppTabController.showSubscriptionScreen()`
 * (`kornerz/Kornerz/SwiftUI/TabView/AppTabController.swift:209-229`) and
 * `LoginVC.showSubscriptionScreen()`
 * (`kornerz/Kornerz/User/LoginVC/LoginVC.swift:219-232`):
 *
 * | # | Clause | iOS counterpart |
 * |---|---|---|
 * | 1 | Not already on the plans route | `:212` `guard isSubscriptionOpened() == false` |
 * | 2 | The session is authenticated | `:213` `guard isUserLoggedIn` |
 * | 3 | No active Play entitlement | `:214` `guard isSubscriptionActive == false` |
 * | 4 | `User.isUserPremiumNow == false` | `:215` `guard isUserPremium == false` |
 * | 5 | Not dismissed in this process | Android-only latch (no production call site arms it — P8 §19 made the paywall mandatory) |
 *
 * Clause 5 stands in for iOS's implicit `isSubscriptionOpened()`
 * inspecting `presentedViewController`: iOS achieves "do not re-present"
 * by checking whether the paywall is already on screen, which Android's
 * back stack cannot express as a predicate, so the latch is explicit.
 *
 * Clause 4 deliberately reads [com.kornerz.core.model.User.isUserPremiumNow]
 * — the expiry-derived projection — and never the raw `isPremium` bit,
 * which is write-only on both platforms and permanently `false` in
 * Firestore for every subscriber.
 */
@Singleton
class PremiumGate @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val entitlementChecker: PlayEntitlementChecker,
) {

    private val _shouldPresent = MutableStateFlow(false)

    /**
     * Whether the paywall should be on screen. Recomputed by
     * [requestPresent] and latched by [markDismissed] / [markSatisfied] /
     * [reset].
     */
    val shouldPresent: StateFlow<Boolean> = _shouldPresent.asStateFlow()

    private var dismissedThisProcess = false
    private var satisfiedThisProcess = false

    /**
     * Whether the plans route is the current destination. Set by the shell
     * when the paywall opens, so clause 1 does not re-present it while the
     * user is looking at it.
     */
    @Volatile
    private var plansRouteOpen = false

    /** Serialises the clause evaluation against the latch writes. */
    private val gateLock = Mutex()

    /**
     * Recomputes all five clauses against current state and returns `true`
     * only when every one passes. Also publishes the answer on
     * [shouldPresent].
     *
     * Called from exactly two sites, both with a direct iOS counterpart:
     * post-login (the shell graph being entered right after a successful
     * login) and on shell resume. **Not** on destination changes, tab
     * switches, or deep-link arrivals — iOS has no such path, and firing on
     * tab switches would re-present the paywall every time a free user
     * changed tabs.
     */
    suspend fun requestPresent(): Boolean = gateLock.withLock {
        val present = evaluate()
        _shouldPresent.value = present
        present
    }

    private suspend fun evaluate(): Boolean {
        // Clause 5 — the per-process dismissal latch.
        if (dismissedThisProcess || satisfiedThisProcess) return false

        // Clause 1 — already showing the paywall.
        if (plansRouteOpen) return false

        // Clause 2 — authenticated. An anonymous session never reaches the
        // paywall.
        val user = (sessionRepository.data.first() as? SessionState.Authenticated)?.user
            ?: return false

        // Clause 4 — no live entitlement. Checked before clause 3 because
        // it is a pure in-memory comparison, while clause 3 costs a Play
        // round trip; a subscriber should never pay that latency to be
        // told they are a subscriber.
        if (user.isUserPremiumNow) return false

        // Clause 3 — no active Play entitlement on this device.
        if (entitlementChecker.hasActiveSubscription()) return false

        return true
    }

    /** Called by the shell when the plans route is pushed. */
    fun markPlansRouteOpen(open: Boolean) {
        plansRouteOpen = open
    }

    /**
     * The user tapped the close affordance (or the system back gesture).
     * Blocks re-presentation for the rest of the process.
     *
     * **No production call site arms this.** The paywall became mandatory in
     * P8 §19: the plans screen renders no close affordance and swallows the
     * back gesture, so the route can only be popped by a completed purchase.
     * The latch is kept — and clause 5 kept with it — so the guard still
     * holds if a dismissal affordance is ever reintroduced. Do not read this
     * as permission to add one back.
     */
    fun markDismissed() {
        dismissedThisProcess = true
        _shouldPresent.value = false
    }

    /**
     * A purchase completed, or an active entitlement was detected.
     *
     * Clears the **dismissal latch as well**, so a user who purchased
     * mid-session is never re-gated regardless of how they got to the
     * paywall.
     */
    fun markSatisfied() {
        satisfiedThisProcess = true
        dismissedThisProcess = false
        _shouldPresent.value = false
    }

    /**
     * Clears both latches. Invoked on logout and on account deletion, so a
     * subsequent fresh login re-evaluates the gate from scratch — matching
     * iOS, where the post-login gate in `LoginVC` fires unconditionally for
     * a free user.
     */
    fun reset() {
        dismissedThisProcess = false
        satisfiedThisProcess = false
        plansRouteOpen = false
        _shouldPresent.value = false
    }
}