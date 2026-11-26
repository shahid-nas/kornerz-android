package com.kornerz.kornerz

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.kornerz.core.model.DeepLinkDiagnostics
import com.kornerz.core.model.SubscriptionDiagnostics
import com.kornerz.core.network.crashlytics.CrashReporter
import com.kornerz.core.network.crashlytics.CrashlyticsSessionBinder
import com.kornerz.core.network.fcm.FcmTokenRegistrar
import com.kornerz.core.ui.DeepLinkLog
import com.kornerz.core.ui.SubscriptionGateLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Single Hilt entry point for the Kornerz Android app. Lives in the
 * `app/` module because `core/network` is a plain library — only one
 * `@HiltAndroidApp` is allowed per process, so it must live in the
 * application module.
 *
 * On startup we:
 * 1. Initialize the Firebase SDK against the project declared in the
 *    active flavor's `google-services.json`:
 *    - `dev` flavor  → `app/src/dev/google-services.json`  (staging)
 *    - `prod` flavor → `app/src/prod/google-services.json` (production)
 * 2. Enable Crashlytics collection, stamp the report with build
 *    context, and bind the signed-in user id to every subsequent
 *    report ([CrashReporter] + [CrashlyticsSessionBinder]).
 * 3. Set the in-app default values for Remote Config so the splash
 *    Composable can read `android_min_supported_version` without a
 *    network round-trip on first launch.
 * 4. Start the [FcmTokenRegistrar] which silently uploads the device
 *    token whenever a session is present.
 *
 * The P0 anonymous Firebase Auth sign-in is intentionally removed in
 * P1 (see `specs/android-app-shell/spec.md` §"No anonymous Firebase
 * user at launch in P1") — authentication is handled exclusively by the
 * custom-auth client.
 */
@HiltAndroidApp
class KornerzApplication : Application() {

    @Inject lateinit var fcmTokenRegistrar: FcmTokenRegistrar
    @Inject lateinit var crashReporter: CrashReporter
    @Inject lateinit var crashlyticsSessionBinder: CrashlyticsSessionBinder

    /** Application-lifetime scope for background wiring (session → Crashlytics). */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Route `core/model`'s deep-link diagnostics to logcat. The
        // parser lives in a pure JVM module and cannot log itself, so
        // it reports through a sink the Android side installs here —
        // the earliest point in the process, so no event is missed.
        DeepLinkDiagnostics.reporter = DeepLinkDiagnostics.Reporter { stage, message ->
            DeepLinkLog.log(stage, message)
        }

        // Route the subscription gate's diagnostics to logcat under the
        // single `SubscriptionGate` tag. Same reason as the deep-link
        // reporter above: the reporting modules cannot log themselves,
        // and the paywall gate is a five-clause conjunction whose
        // blocking clause is otherwise invisible from the outside.
        SubscriptionDiagnostics.reporter = SubscriptionDiagnostics.Reporter { stage, message ->
            SubscriptionGateLog.log(stage, message)
        }

        FirebaseApp.initializeApp(this)
        val options: FirebaseOptions = FirebaseApp.getInstance().options
        Log.i(TAG, "FirebaseApp initialized for project=${options.projectId}")

        // Crashlytics: turn collection on explicitly (never left to a
        // remote default), stamp every report with the build context,
        // and keep userId in step with the auth session for the life
        // of the process. Uncaught exceptions are reported by the SDK
        // without further help; non-fatals go through CrashReporter.
        crashReporter.setCollectionEnabled(true)
        crashReporter.setCustomKey("app_version", BuildConfig.VERSION_NAME)
        crashReporter.setCustomKey("build_type", BuildConfig.BUILD_TYPE)
        crashReporter.setCustomKey("flavor", BuildConfig.FLAVOR)
        crashReporter.log("startup: application created")
        crashlyticsSessionBinder.start(applicationScope)

        FirebaseRemoteConfig.getInstance().apply {
            setDefaultsAsync(R.xml.remote_config_defaults)
        }

        fcmTokenRegistrar.registerOnStart()
    }

    private companion object {
        const val TAG = "KornerzApplication"
    }
}