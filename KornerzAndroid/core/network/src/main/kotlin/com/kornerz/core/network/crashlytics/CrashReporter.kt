package com.kornerz.core.network.crashlytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [FirebaseCrashlytics] so feature and core code
 * never talks to the SDK directly (architecture §4.1 — repositories
 * hide the data-source details).
 *
 * Uncaught exceptions are collected by the SDK automatically once the
 * Crashlytics Gradle plugin and `google-services.json` are in place.
 * This class covers everything the automatic path does not do:
 *
 *  - [setUserId] — attribute crashes to the signed-in user
 *  - [setCustomKey] — version / flavor / build-type context on every report
 *  - [recordException] — non-fatal errors that were handled
 *  - [log] — breadcrumbs attached to the next crash
 *  - [setCollectionEnabled] — explicit opt-in so collection is never
 *    left to a remote-config default
 *
 * All methods are safe to call from any thread and are no-ops if the
 * underlying SDK throws (e.g. Firebase not yet initialized on a
 * pathological startup order) so reporting can never crash the app
 * it is trying to report on.
 */
@Singleton
class CrashReporter @Inject constructor(
    private val crashlytics: FirebaseCrashlytics,
) {

    /**
     * Enables or disables crash collection. Collection is turned **on**
     * at application startup — [KornerzApplication] calls this with
     * `true` so reports are never silently dropped.
     */
    fun setCollectionEnabled(enabled: Boolean) {
        runCatching { crashlytics.setCrashlyticsCollectionEnabled(enabled) }
    }

    /**
     * Sets the user id attached to every subsequent report. Pass a
     * blank string (or null) on logout so the next user's crashes are
     * not attributed to the previous account.
     *
     * The Kornerz **user code** (`codeUser`) is preferred over the
     * document id because that is the stable cross-device identity the
     * backend and iOS both use; fall back to the document id when the
     * code has not been written yet.
     */
    fun setUserId(userId: String?) {
        runCatching { crashlytics.setUserId(userId.orEmpty()) }
    }

    /** Attaches a string key/value to every report from this point on. */
    fun setCustomKey(key: String, value: String) {
        runCatching { crashlytics.setCustomKey(key, value) }
    }

    /** Attaches a boolean key/value to every report from this point on. */
    fun setCustomKey(key: String, value: Boolean) {
        runCatching { crashlytics.setCustomKey(key, value) }
    }

    /**
     * Records a **non-fatal** error. The app keeps running; the stack
     * trace is sent to Firebase with the current keys and breadcrumbs.
     *
     * Use for handled failures that indicate a bug (unexpected
     * exceptions, failed repository calls) — not for expected user
     * errors such as a wrong password.
     */
    fun recordException(throwable: Throwable) {
        runCatching { crashlytics.recordException(throwable) }
    }

    /**
     * Adds a breadcrumb line to the crash log. Breadcrumbs are kept
     * for the current session and attached to the next fatal or
     * non-fatal report.
     */
    fun log(message: String) {
        runCatching { crashlytics.log(message) }
    }
}
