package com.kornerz.core.network.fcm

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single writer for this device's FCM token.
 *
 * Every FCM token write in the Android port goes through here, so the
 * Firestore layout is defined in exactly one place. The layout is:
 *
 * ```
 * Users/{email}.tokenPush                 → the token (iOS parity)
 * Users/{email}.devices/{deviceId}.token  → the token (multi-device)
 * ```
 *
 * The `tokenPush` field on the user document is what iOS writes
 * (`FireStoreServiceManager.saveUserToken()`,
 * `kornerz/Kornerz/FirestoreService/FireStoreServiceManager.swift:52-55`)
 * and what the backend push sender reads, so it stays authoritative and
 * keeps the two platforms interoperable.
 *
 * The `devices` subcollection is an **addition**, not a replacement: it
 * holds one document per device so a second device no longer overwrites
 * the first device's token. The backend can adopt it without breaking
 * the existing single-token contract.
 */
@Singleton
class DeviceTokenStore @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    /**
     * Writes [token] for the device identified by [deviceId] and owned by
     * the user whose Firestore document is keyed by [email].
     *
     * Both writes are best-effort: a failure of the `devices` subdocument
     * does not prevent the `tokenPush` field from being written, because
     * `tokenPush` is the field the backend actually reads.
     */
    suspend fun upsert(email: String, deviceId: String, token: String): Result<Unit> = runCatching {
        require(email.isNotBlank()) { "Cannot register an FCM token without a user email" }
        require(token.isNotBlank()) { "Cannot register a blank FCM token" }

        val userDocument = firestore.collection(USERS_COLLECTION).document(email)
        userDocument.set(mapOf(FIELD_TOKEN_PUSH to token), SetOptions.merge()).await()

        runCatching {
            userDocument
                .collection(DEVICES_SUBCOLLECTION)
                .document(deviceId)
                .set(
                    mapOf(
                        FIELD_TOKEN to token,
                        FIELD_PLATFORM to PLATFORM_ANDROID,
                        FIELD_UPDATED_AT to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                )
                .await()
        }.onFailure { Log.w(TAG, "Could not write the device sub-document", it) }

        Unit
    }

    /**
     * Removes the device's token from both locations.
     *
     * The `tokenPush` field is only cleared when it still holds
     * [token] — otherwise signing out of device A would wipe the token
     * that device B had just registered. When [token] is `null` the
     * stored token is read back first and compared against itself, so
     * the field is cleared only if it belongs to **this** device.
     */
    suspend fun clear(email: String, deviceId: String, token: String?): Result<Unit> = runCatching {
        require(email.isNotBlank()) { "Cannot clear an FCM token without a user email" }

        val userDocument = firestore.collection(USERS_COLLECTION).document(email)
        val stored = userDocument.get().await().getString(FIELD_TOKEN_PUSH)
        val expected = token ?: stored
        if (stored != null && stored == expected) {
            userDocument.set(mapOf(FIELD_TOKEN_PUSH to null), SetOptions.merge()).await()
        }

        runCatching {
            userDocument
                .collection(DEVICES_SUBCOLLECTION)
                .document(deviceId)
                .delete()
                .await()
        }.onFailure { Log.w(TAG, "Could not delete the device sub-document", it) }

        Unit
    }

    /**
     * Removes the device's registration **unconditionally**.
     *
     * Used when FCM reports the token as invalid: the backend must stop
     * targeting it, and the token it holds can no longer be compared
     * against the one the app knows about. The replacement token arrives
     * through the FCM service's `onNewToken` and is written back to
     * `tokenPush` by the registrar.
     */
    suspend fun clearDevice(email: String, deviceId: String): Result<Unit> = runCatching {
        require(email.isNotBlank()) { "Cannot clear an FCM token without a user email" }

        val userDocument = firestore.collection(USERS_COLLECTION).document(email)
        userDocument.set(mapOf(FIELD_TOKEN_PUSH to null), SetOptions.merge()).await()

        runCatching {
            userDocument
                .collection(DEVICES_SUBCOLLECTION)
                .document(deviceId)
                .delete()
                .await()
        }.onFailure { Log.w(TAG, "Could not delete the device sub-document", it) }

        Unit
    }

    private companion object {
        const val TAG = "DeviceTokenStore"
        const val USERS_COLLECTION = "Users"
        const val DEVICES_SUBCOLLECTION = "devices"
        const val FIELD_TOKEN_PUSH = "tokenPush"
        const val FIELD_TOKEN = "token"
        const val FIELD_PLATFORM = "platform"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val PLATFORM_ANDROID = "android"
    }
}
