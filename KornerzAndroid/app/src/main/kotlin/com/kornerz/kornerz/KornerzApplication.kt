package com.kornerz.kornerz

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.kornerz.core.network.fcm.FcmTokenRegistrar
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Single Hilt entry point for the Kornerz Android app. Lives in the
 * `app/` module because `core/network` is a plain library — only one
 * `@HiltAndroidApp` is allowed per process, so it must live in the
 * application module.
 *
 * On startup we:
 * 1. Initialize the Firebase SDK against the project declared in
 *    `app/google-services.json`.
 * 2. Set the in-app default values for Remote Config so the splash
 *    Composable can read `android_min_supported_version` without a
 *    network round-trip on first launch.
 * 3. Start the [FcmTokenRegistrar] which silently uploads the device
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

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        val options: FirebaseOptions = FirebaseApp.getInstance().options
        Log.i(TAG, "FirebaseApp initialized for project=${options.projectId}")

        FirebaseRemoteConfig.getInstance().apply {
            setDefaultsAsync(R.xml.remote_config_defaults)
        }

        fcmTokenRegistrar.registerOnStart()
    }

    private companion object {
        const val TAG = "KornerzApplication"
    }
}