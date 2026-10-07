package com.kornerz.kornerz

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp

/**
 * Single Hilt entry point for the Kornerz Android app. Lives in the
 * `app/` module because `core/network` is a plain library — only one
 * `@HiltAndroidApp` is allowed per process, so it must live in the
 * application module.
 *
 * On startup we:
 * 1. Initialize the Firebase SDK against the project declared in
 *    `app/google-services.json`.
 * 2. Kick off an anonymous Firebase Auth sign-in so P1+ can link
 *    pre-login activity to the eventual real account. The call is
 *    fire-and-forget so it never blocks the UI thread.
 */
@HiltAndroidApp
class KornerzApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        val options: FirebaseOptions = FirebaseApp.getInstance().options
        Log.i(TAG, "FirebaseApp initialized for project=${options.projectId}")

        FirebaseAuth.getInstance().signInAnonymously()
            .addOnSuccessListener {
                Log.i(TAG, "FirebaseAuth.signInAnonymously succeeded (uid hidden)")
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "FirebaseAuth.signInAnonymously failed", error)
            }
    }

    private companion object {
        const val TAG = "KornerzApplication"
    }
}