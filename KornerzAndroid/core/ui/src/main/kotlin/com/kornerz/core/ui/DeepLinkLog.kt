package com.kornerz.core.ui

import android.util.Log

/**
 * The deep-link chain's logger.
 *
 * Every stage of the chain a tapped push notification travels — the FCM
 * service, the activity, the payload parser, the manager, the shell and
 * the destination screen — logs through this object, so the whole
 * journey is captured by **one** logcat filter:
 *
 * ```sh
 * adb logcat -s DeepLink:V
 * ```
 *
 * The chain is a sequence of independent hops, and a broken hop is
 * invisible on its own: the app still opens, the notification still
 * appears, and the user still lands on the Home tab. The logs make the
 * hop that did not happen visible.
 *
 * **The stages are numbered**, so the log reads as a trace of the
 * journey rather than a pile of unrelated lines:
 *
 * | Stage | Logged by | Meaning |
 * |---|---|---|
 * | `1-payload` | `KornerzFcmService` | the raw FCM payload arrived |
 * | `2-tap-intent` | `KornerzFcmService` | the app built the tray tap intent |
 * | `3-intent` | `MainActivity` | an intent reached the activity |
 * | `4-transport` | `MainActivity` | which transport carried the link |
 * | `5-parse` | `DeepLinkPayloadParser` | the payload was parsed into a link |
 * | `6-manager` | `DeepLinkManager` | the link was queued or released |
 * | `7-interstitial` | `AppShell` | the resolving screen was pushed |
 * | `8-resolve` | `DeepLinkResolver` | the link was resolved to a target |
 * | `9-target` | `AppShell` | the target is being executed |
 * | `10-tab-switch` | `AppShell` | the tab was switched to host the target |
 * | `11-consume` | `AppShell` | the pending target reached the tab |
 * | `12-route` | `AppShell` | the destination route was pushed |
 * | `13-destination` | the destination screen | the screen took the id and loaded |
 *
 * A hop that is **missing** from the log is the hop that broke.
 */
object DeepLinkLog {

    /** The logcat tag every stage logs under. */
    const val TAG: String = "DeepLink"

    /**
     * Reports a stage of the chain to logcat.
     *
     * This is the [com.kornerz.core.model.DeepLinkDiagnostics.Reporter]
     * the application installs — `core/model` is a pure JVM module and
     * cannot log itself, so the parser reports through this and the
     * Android side decides how to print it.
     */
    fun log(stage: String, message: String) {
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
        Log.w(TAG, "[$stage] FAILED: $message")
    }

    /**
     * Logs a stage whose outcome could not be determined — the payload
     * arrived but carried nothing the app could act on.
     */
    fun skip(stage: String, message: String) {
        Log.w(TAG, "[$stage] SKIPPED: $message")
    }
}
