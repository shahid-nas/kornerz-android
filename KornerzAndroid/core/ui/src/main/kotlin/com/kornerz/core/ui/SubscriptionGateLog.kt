package com.kornerz.core.ui

import android.util.Log

/**
 * The subscription gate's logger.
 *
 * Every stage of the paywall decision — the user refresh, the entitlement
 * reconciliation, the clause-by-clause gate evaluation — logs through this
 * object, so the whole chain is captured by **one** logcat filter:
 *
 * ```sh
 * adb logcat -s SubscriptionGate:V
 * ```
 *
 * **Debug builds only.** Every method returns immediately when
 * [BuildConfig.DEBUG] is `false`, so a release build emits nothing under
 * this tag. The lines carry the signed-in user's email address, the
 * subscription expiry, and the plan type — none of which belong in a
 * production log — and `android.util.Log` calls are not removed by R8
 * (it cannot know they are side-effect-free), so the guard here is the
 * only thing keeping them out of a release.
 *
 * The same policy applies to [DeepLinkLog], so the two loggers cannot
 * drift apart.
 *
 * The chain is a sequence of independent hops, and a broken hop is
 * invisible on its own: the app opens, the user is signed in, and the
 * paywall simply never appears. The logs make the hop that did not
 * happen visible.
 *
 * **The stages are numbered**, so the log reads as a trace of the
 * decision rather than a pile of unrelated lines:
 *
 * | Stage | Logged by | Meaning |
 * |---|---|---|
 * | `1-refresh` | `UserProfileRefresher` | the cached user was re-read from Firestore |
 * | `2-reconcile` | `SubscriptionRepository` | the Play purchase library was queried |
 * | `3-grant` | `SubscriptionRepository` | the entitlement was written |
 * | `4-revoke` | `SubscriptionRepository` | the entitlement was cleared |
 * | `5-clause` | `PremiumGate` | a clause of the conjunction blocked the paywall |
 * | `6-present` | `PremiumGate` | every clause passed; the paywall is being presented |
 *
 * A hop that is **missing** from the log is the hop that broke.
 */
object SubscriptionGateLog {

    /** The logcat tag every stage logs under. */
    const val TAG: String = "SubscriptionGate"

    /**
     * Reports a stage of the chain to logcat.
     *
     * This is the [com.kornerz.core.model.SubscriptionDiagnostics.Reporter]
     * the application installs — `core/model` is a pure JVM module and
     * cannot log itself, so the reporting modules use this and the
     * Android side decides how to print it.
     */
    fun log(stage: String, message: String) {
        if (!BuildConfig.DEBUG) return
        Log.i(TAG, "[$stage] $message")
    }

    /**
     * Logs a stage that **failed**.
     *
     * A failure is logged at the same tag and stage as the success it
     * replaces, so the two are directly comparable — the success line
     * is simply absent.
     */
    fun fail(stage: String, message: String) {
        if (!BuildConfig.DEBUG) return
        Log.w(TAG, "[$stage] FAILED: $message")
    }

    /**
     * Logs a stage whose outcome could not be determined — the payload
     * arrived but carried nothing the app could act on.
     */
    fun skip(stage: String, message: String) {
        if (!BuildConfig.DEBUG) return
        Log.w(TAG, "[$stage] SKIPPED: $message")
    }
}
