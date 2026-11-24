package com.kornerz.core.model

/**
 * The subscription gate's diagnostics sink.
 *
 * `core/model` is a **pure JVM module** — it has no Android dependency
 * and therefore cannot write to logcat. The premium gate and the
 * entitlement repository live in other modules, but they all depend on
 * `core/model`, so they report through this sink and the Android side
 * decides how to print it.
 *
 * This mirrors [DeepLinkDiagnostics] exactly: a single nullable callback,
 * installed by the application at the earliest point in the process so no
 * event is missed.
 *
 * **What it is for.** The paywall gate is a five-clause conjunction
 * (`PremiumGate.evaluate()`): every clause must pass for the paywall to
 * be presented. When it does not appear, the question is always *which
 * clause blocked it* — and the answer is not visible from the outside,
 * because every clause returns `false` for the same reason ("do not
 * present"). Without this sink the gate is a black box: the app looks
 * broken and the cause is unrecoverable from logs.
 *
 * With it, one logcat filter shows the whole chain:
 *
 * ```sh
 * adb logcat -s SubscriptionGate:V
 * ```
 *
 * The stages are numbered in the order the decision is made, so the log
 * reads as a trace of the evaluation rather than a pile of unrelated
 * lines:
 *
 * | Stage | Logged by | Meaning |
 * |---|---|---|
 * | `1-refresh` | `UserProfileRefresher` | the cached user was re-read from Firestore |
 * | `2-reconcile` | `SubscriptionRepository` | the Play purchase library was queried |
 * | `3-grant` | `SubscriptionRepository` | the entitlement was written |
 * | `4-revoke` | `SubscriptionRepository` | the entitlement was cleared |
 * | `5-clause` | `PremiumGate` | a clause of the conjunction blocked the paywall |
 * | `6-present` | `PremiumGate` | every clause passed; the paywall is being presented |
 */
object SubscriptionDiagnostics {

    /**
     * Reports what a stage of the subscription chain did.
     *
     * @param stage the numbered stage, e.g. `"5-clause"`.
     * @param message what happened at this stage.
     */
    fun interface Reporter {
        fun report(stage: String, message: String)
    }

    /** The active reporter, or `null` when nothing is listening. */
    @Volatile
    var reporter: Reporter? = null

    /** Reports a stage that completed. */
    fun log(stage: String, message: String) {
        reporter?.report(stage, message)
    }

    /** Reports a stage that failed. */
    fun fail(stage: String, message: String) {
        reporter?.report(stage, "FAILED: $message")
    }

    /** Reports a stage that was skipped, or that declined to act. */
    fun skip(stage: String, message: String) {
        reporter?.report(stage, "SKIPPED: $message")
    }
}
