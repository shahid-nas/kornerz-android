package com.kornerz.core.network.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.User
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
 * the signed-in user document.
 *
 * Lifecycle:
 * 1. `registerOnStart()` is called from `Application.onCreate()` and tries
 *    to read a token (with 3 retries on `null`); if a session is restored
 *    from DataStore, the token is uploaded via [DeviceTokenStore].
 * 2. [observeRotation] is collected while the app is running; a rotation
 *    triggers another upload.
 * 3. `clearOnLogout()` is called by the shell's logout handler to stop
 *    server-side targeting.
 *
 * **The token is always uploaded to the same place the iOS app writes
 * it** — `Users/{email}.tokenPush` — through [DeviceTokenStore]. The
 * earlier split, where the startup path wrote `users/{firebaseAuthUid}`
 * and the rotation path wrote `Users/{email}`, meant the backend could
 * not find the token for a device whose startup upload had won the race.
 *
 * **A token that rotates while the user is signed out is not dropped.**
 * The pending token is held in [pendingToken] and uploaded by the next
 * `registerOnStart()` / rotation upload once a session exists.
 *
 * `@Singleton` scope (architecture §2.6).
 */
@Singleton
class FcmTokenRegistrar @Inject constructor(
    private val messaging: FirebaseMessaging,
    private val sessionRepository: SessionRepository,
    private val deviceTokenStore: DeviceTokenStore,
    private val deviceIdProvider: DeviceIdProvider,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableSharedFlow<AsyncValue<String?>>(extraBufferCapacity = 1)
    val state: SharedFlow<AsyncValue<String?>> = _state.asSharedFlow()

    private var lastUploadedToken: String? = null

    /**
     * The most recent token that could not be uploaded because no
     * session was active. Uploaded on the next opportunity.
     */
    private var pendingToken: String? = null

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
            upload(token)
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
                scope.launch { upload(next) }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "FCM token rotation failed", error)
            }
    }

    /** Called from the shell's logout handler. */
    suspend fun clearOnLogout() {
        val token = lastUploadedToken ?: pendingToken ?: fetchTokenWithRetry()
        val user = authenticatedUser()
        if (token != null && user != null) {
            val deviceId = deviceIdProvider.deviceId()
            deviceTokenStore.clear(user.email, deviceId, token)
                .onFailure { Log.w(TAG, "Could not clear the FCM token", it) }
        }
        lastUploadedToken = null
        pendingToken = null
    }

    /**
     * Uploads [token] for the signed-in user.
     *
     * When no session is active the token is **kept** in [pendingToken]
     * rather than discarded: a token that rotates before the user signs
     * in still has to reach the backend once they do.
     */
    private suspend fun upload(token: String) {
        val user = authenticatedUser()
        if (user == null) {
            pendingToken = token
            Log.i(TAG, "No active session; holding the FCM token until sign-in")
            return
        }
        val deviceId = deviceIdProvider.deviceId()
        deviceTokenStore.upsert(email = user.email, deviceId = deviceId, token = token)
            .onSuccess {
                lastUploadedToken = token
                pendingToken = null
            }
            .onFailure { error ->
                Log.w(TAG, "Could not upload the FCM token", error)
                pendingToken = token
            }
    }

    /**
     * The signed-in user, or `null` when the session is not (or not yet)
     * authenticated.
     */
    private suspend fun authenticatedUser(): User? =
        (sessionRepository.data.first() as? SessionState.Authenticated)?.user

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
