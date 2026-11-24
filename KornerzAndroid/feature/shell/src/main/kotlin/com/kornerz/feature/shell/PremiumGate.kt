package com.kornerz.feature.shell

import com.kornerz.core.model.IAP_ENABLED
import com.kornerz.core.model.SubscriptionDiagnostics
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
     * Whether the plans route is **on the back stack**.
     *
     * Set by the shell whenever the shell's current destination changes,
     * so clause 1 cannot re-present the paywall while it is on screen —
     * or while a destination pushed **on top of** it is on screen.
     *
     * "On the back stack" rather than "is the current destination" is
     * deliberate. The paywall pushes the Privacy Policy viewer on top of
     * itself (`SubscriptionRoutes.kt`), exactly as iOS pushes
     * `PrivacyPolicyView` onto the paywall's own navigation controller
     * (`SubscriptionController.swift:81-85`). While the viewer is open the
     * plans route is **not** the current destination, but it is still on
     * the stack and the user must not reach the un-gated app by backing
     * out of the viewer.
     *
     * Deriving this from the navigation state — rather than toggling it
     * manually at each call site — is what keeps the latch truthful. A
     * manually-toggled latch drifted: the Privacy Policy path popped the
     * plans route off the stack without clearing it, so the gate believed
     * the paywall was still showing forever and never re-armed it.
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
        SubscriptionDiagnostics.log("5-clause", "evaluating the gate")
        val present = evaluate()
        _shouldPresent.value = present
        SubscriptionDiagnostics.log(
            "5-clause",
            "evaluation finished — shouldPresent=$present",
        )
        present
    }

    /**
     * Evaluates the five clauses and reports **which** one blocked the
     * paywall.
     *
     * Every clause returns `false` for the same outward reason — "do not
     * present" — so from the outside a blocked paywall is
     * indistinguishable from a missing one. Each clause therefore
     * reports its own verdict through
     * [com.kornerz.core.model.SubscriptionDiagnostics] before it
     * returns, so a single logcat filter shows which clause stopped the
     * paywall and what value it stopped on.
     *
     * The clause order is preserved: the clauses are evaluated in the
     * order the KDoc above lists them, so the logged sequence is the
     * evaluation sequence. A clause that is never reached is logged as
     * such by the clause that preceded it, because the evaluation
     * short-circuits.
     */
    private suspend fun evaluate(): Boolean {
        // The paywall is mandatory — it has no close affordance and swallows
        // the back gesture — so it is only presented when a purchase can
        // actually be completed. While IAP is off the gate reports "do not
        // present".
        if (!IAP_ENABLED) {
            block("iap-disabled", "IAP_ENABLED is false; the paywall is never presented")
            return false
        }

        // Clause 5 — the per-process dismissal latch.
        if (dismissedThisProcess || satisfiedThisProcess) {
            block(
                "clause-5-latch",
                "dismissedThisProcess=$dismissedThisProcess satisfiedThisProcess=$satisfiedThisProcess",
            )
            return false
        }

        // Clause 1 — already showing the paywall.
        if (plansRouteOpen) {
            block("clause-1-already-open", "the plans route is already on the back stack")
            return false
        }

        // Clause 2 — authenticated. An anonymous session never reaches the
        // paywall.
        val user = (sessionRepository.data.first() as? SessionState.Authenticated)?.user
        if (user == null) {
            block("clause-2-anonymous", "the session is not authenticated")
            return false
        }

        // Clause 4 — no live entitlement. Checked before clause 3 because
        // it is a pure in-memory comparison, while clause 3 costs a Play
        // round trip; a subscriber should never pay that latency to be
        // told they are a subscriber.
        //
        // The expiry is logged in full: the raw string, the parsed
        // instant, and the comparison against now. A subscriber who is
        // wrongly gated has exactly three possible causes, and each one
        // is visible in this line — the raw value is missing (the server
        // has no expiry), the raw value is present but does not parse
        // (a format mismatch between the platforms), or the parsed value
        // is in the past (the subscription really has lapsed).
        val expiryInstant = user.subscriptionExpiryInstant
        if (user.isUserPremiumNow) {
            block(
                "clause-4-premium-now",
                "isUserPremiumNow=true; subscriptionExpiryDate=${user.subscriptionExpiryDate} " +
                    "parsed=$expiryInstant — the user IS premium",
            )
            return false
        }

        // Clause 3 — no active Play entitlement on this device.
        val hasPlayEntitlement = entitlementChecker.hasActiveSubscription()
        if (hasPlayEntitlement) {
            block(
                "clause-3-play-entitlement",
                "hasActiveSubscription=true; the device holds an active Play purchase",
            )
            return false
        }

        // Every clause passed. The full context is logged so the decision
        // is reproducible from the log alone.
        SubscriptionDiagnostics.log(
            "6-present",
            "all clauses passed — presenting the paywall. " +
                "email=${user.email} " +
                "subscriptionExpiryDate=${user.subscriptionExpiryDate} " +
                "parsed=$expiryInstant " +
                "isUserPremiumNow=${user.isUserPremiumNow} " +
                "hasPlayEntitlement=$hasPlayEntitlement",
        )
        return true
    }

    /**
     * Records that a clause blocked the paywall.
     *
     * The clause name and its verdict are reported together, so the log
     * line is self-describing: it names the clause, and it carries the
     * values that clause decided on.
     */
    private fun block(clause: String, detail: String) {
        SubscriptionDiagnostics.skip(
            "5-clause",
            "$clause blocked the paywall — $detail",
        )
    }

    /**
     * Records that the plans route has been pushed.
     *
     * Called by the shell **before** the navigation commits, so a second
     * gate evaluation in the window between "the gate fired" and "the
     * paywall is on screen" cannot push a second copy.
     */
    fun markPlansRouteOpen(open: Boolean) {
        plansRouteOpen = open
        SubscriptionDiagnostics.log(
            "5-clause",
            "clause-1 latch set — plansRouteOpen=$open",
        )
    }

    /**
     * Reconciles the clause-1 latch with the shell's actual back stack.
     *
     * The shell calls this whenever the current destination changes, with
     * whether the plans route is on the stack. The latch is only ever
     * **cleared** here — never set — so a reconciliation that lands
     * between "the gate fired" and "the navigation committed" cannot open
     * a window for a second paywall.
     *
     * A stale `true` is impossible: this reconciliation re-runs on every
     * destination change, so a plans route that left the stack clears the
     * latch. A stale `false` is impossible: [markPlansRouteOpen] sets it
     * the moment the paywall is pushed.
     */
    fun reconcilePlansRouteOnStack(plansRouteOnStack: Boolean) {
        if (!plansRouteOnStack && plansRouteOpen) {
            SubscriptionDiagnostics.log(
                "5-clause",
                "clause-1 latch cleared — the plans route left the back stack",
            )
        }
        if (!plansRouteOnStack) plansRouteOpen = false
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
        SubscriptionDiagnostics.log(
            "5-clause",
            "clause-5 latch set — dismissedThisProcess=true",
        )
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
        SubscriptionDiagnostics.log(
            "5-clause",
            "clause-5 latch set — satisfiedThisProcess=true",
        )
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
        SubscriptionDiagnostics.log(
            "5-clause",
            "all latches cleared by reset()",
        )
    }
}