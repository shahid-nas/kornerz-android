package com.kornerz.core.network.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.AuthRepository
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Silently registers the device's Firebase Cloud Messaging token against
 * the signed-in user document. No POST_NOTIFICATIONS prompt is shown in
 * P1 — that lands in P6 when notifications UI ships.
 *
 * Lifecycle:
 * 1. `registerOnStart()` is called from `Application.onCreate()` and tries
 *    to read a token (with 3 retries on `null`); if a session is
 *    restored from DataStore, the token is uploaded via [AuthRepository].
 * 2. [observeToken] is collected while the user is signed in; rotation
 *    triggers another `updateFcmToken` call.
 * 3. `clearOnLogout()` is called by `AuthRepository.logout()` (via the
 *    `clearFcmToken` Cloud Function) to stop server-side targeting.
 *
 * `@Singleton` scope (architecture §2.6).
 */
@Singleton
class FcmTokenRegistrar @Inject constructor(
    private val messaging: FirebaseMessaging,
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableSharedFlow<AsyncValue<String?>>(extraBufferCapacity = 1)
    val state: SharedFlow<AsyncValue<String?>> = _state.asSharedFlow()

    private var lastUploadedToken: String? = null

    /**
     * Hooks into `Application.onCreate()`. Awaits the first FCM token
     * (up to 3 attempts with back-off) and uploads it if a session is
     * present. Always resolves silently — failures are logged at WARN.
     */
    fun registerOnStart() {
        scope.launch {
            val token = fetchTokenWithRetry()
            if (token == null) {
                Log.w(TAG, "FCM token unavailable after retries")
                return@launch
            }
            _state.tryEmit(AsyncValue.Success(token))
            if (sessionRepository.data.first() is SessionState.Authenticated) {
                lastUploadedToken = token
                authRepository.updateFcmToken(token)
            }
        }
        scope.launch { observeRotation() }
    }

    /**
     * Subscribes to the FCM token flow; whenever it changes AND the user
     * is signed in, re-uploads the new token. Cancels implicitly when the
     * Singleton process is torn down.
     */
    private suspend fun observeRotation() {
        messaging.token
            .addOnSuccessListener { next ->
                if (next.isNullOrEmpty() || next == lastUploadedToken) return@addOnSuccessListener
                lastUploadedToken = next
                scope.launch {
                    if (sessionRepository.data.first() is SessionState.Authenticated) {
                        authRepository.updateFcmToken(next)
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "FCM token rotation failed", error)
            }
    }

    /** Called from `AuthRepository.clearFcmToken(...)` via the UI layer. */
    suspend fun clearOnLogout() {
        val token = lastUploadedToken ?: fetchTokenWithRetry()
        if (token != null) {
            authRepository.clearFcmToken(token)
        }
        lastUploadedToken = null
    }

    private suspend fun fetchTokenWithRetry(): String? {
        repeat(MAX_ATTEMPTS) { attempt ->
            val token = withTimeoutOrNull(TOKEN_FETCH_TIMEOUT_MS) {
                runCatching { messaging.token.await() }
                    .onFailure { Log.w(TAG, "FCM token fetch failed (attempt $attempt)", it) }
                    .getOrNull()
            }
            if (!token.isNullOrEmpty()) return token
            delay(BACKOFF_BASE_MS * (1L shl attempt))
        }
        return null
    }

    private companion object {
        const val TAG = "FcmTokenRegistrar"
        const val MAX_ATTEMPTS = 3
        const val TOKEN_FETCH_TIMEOUT_MS = 5_000L
        const val BACKOFF_BASE_MS = 1_000L
    }
}