package com.kornerz.feature.subscription.data.network

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.kornerz.core.model.User
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore API for the three subscription fields on `Users/{email}`.
 *
 * Mirrors iOS `UserRepository.setUserPremium(expiryDate:type:)`
 * (`kornerz/Kornerz/Backend/Repositories/UserRepository.swift:165-178`)
 * and `setUserFree()` (`:149-163`) field-for-field, including the
 * identical-expiry no-op guard.
 *
 * **Document key convention: the email**, matching
 * `feature/profile`'s `UserFirestoreApi` (`:12-27` KDoc, `fetchByEmail`
 * at `:38-46`) and iOS `FireStoreServiceManager.swift:21`. Not
 * `codeUser`, which is a field *on* the document holding the public-facing
 * user code, and not the lowercase `users/{firebaseUid}` path that
 * `FirestoreAuthApi.updateFcmToken` uses — that is a pre-existing FCM
 * inconsistency, not a convention to copy.
 *
 * Only three keys are ever written. `isUserPremium` is deliberately left
 * alone because iOS never writes it, and the premium decision is derived
 * from `subscriptionExpiryDate` on both platforms
 * (`User.isUserPremiumNow`).
 */
@Singleton
class SubscriptionFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    /**
     * Grants the entitlement by patching exactly three keys onto
     * `Users/{email}` via **`updateData`**.
     *
     * `updateData`, not `setData`, mirrors iOS (`:171`) and is what
     * guarantees the other ~20 fields on the document survive: a `set`
     * would clobber them.
     *
     * `expiryDate` is written as a Firestore [Timestamp] so the on-wire
     * shape is byte-identical to iOS's `Timestamp.dateValue()`.
     *
     * Implements the identical-expiry no-op guard from
     * `UserRepository.swift:168`
     * (`guard user.subscriptionExpiryDate?.dateValue() != expiryDate`): a
     * repeated restore or a re-entrant purchase issues no redundant write.
     */
    suspend fun setSubscription(
        email: String,
        expiryDate: Instant,
        planId: String,
        platform: String,
    ): Result<Unit> = runCatching {
        if (email.isBlank()) {
            throw IllegalArgumentException("setSubscription requires a non-blank email")
        }
        val document = firestore.collection(USERS_COLLECTION).document(email)

        // Read first for the no-op guard. A read failure here must not
        // silently skip the write, so any error propagates to the caller.
        val snapshot = document.get().await()
        if (snapshot.exists()) {
            // Compared in epoch millis: the stored value round-trips
            // through java.time.Instant, while this API speaks
            // kotlinx-datetime.
            val storedMillis = snapshot.getTimestamp(FIELD_EXPIRY_DATE)
                ?.toDate()
                ?.toInstant()
                ?.toEpochMilli()
            if (storedMillis == expiryDate.toEpochMilliseconds()) return@runCatching Unit
        }

        document.update(
            mapOf<String, Any>(
                FIELD_EXPIRY_DATE to Timestamp(Date(expiryDate.toEpochMilliseconds())),
                FIELD_SUBSCRIPTION_TYPE to planId,
                FIELD_SUBSCRIPTION_PLATFORM to platform,
            ),
        ).await()
    }

    /**
     * Revokes the entitlement by deleting the same three keys via
     * [FieldValue.delete] — the Firestore equivalent of iOS
     * `setUserFree()` (`:149-163`). `isUserPremiumNow` evaluates to
     * `false` on the next read.
     */
    suspend fun clearSubscription(email: String): Result<Unit> = runCatching {
        if (email.isBlank()) {
            throw IllegalArgumentException("clearSubscription requires a non-blank email")
        }
        firestore.collection(USERS_COLLECTION)
            .document(email)
            .update(
                mapOf<String, Any>(
                    FIELD_EXPIRY_DATE to FieldValue.delete(),
                    FIELD_SUBSCRIPTION_TYPE to FieldValue.delete(),
                    FIELD_SUBSCRIPTION_PLATFORM to FieldValue.delete(),
                ),
            )
            .await()
    }

    /**
     * Reads the authoritative `Users/{email}` document.
     *
     * Backs [com.kornerz.feature.subscription.data.SubscriptionRepository.refreshCachedUser],
     * which writes the result through `SessionRepository.updateUser` after
     * every entitlement change. `feature/profile` owns the Profile read
     * path and has its own decoder; this one exists because
     * `feature/subscription` must not depend on `feature/profile`
     * (`docs/architecture.md` §2.5 — only `app` may import the feature
     * modules).
     *
     * All four premium fields are mapped, including `isUserPremium`, which
     * `UserFirestoreApi.toUserOrNull()` historically dropped. The premium
     * *decision* still comes from `User.isUserPremiumNow`, never from that
     * bit — see the `premium-entitlement` spec.
     */
    suspend fun fetchUser(email: String): User? {
        if (email.isBlank()) return null
        val snapshot = firestore.collection(USERS_COLLECTION)
            .document(email)
            .get()
            .await()
        if (!snapshot.exists()) return null
        return snapshot.toUserOrNull()?.copy(id = email)
    }

    private fun DocumentSnapshot.toUserOrNull(): User? {
        val data = data ?: return null
        val documentEmail = (data[FIELD_EMAIL] as? String) ?: return null
        return User(
            id = documentEmail,
            email = documentEmail,
            displayName = data[FIELD_NAME] as? String ?: "",
            nickname = data[FIELD_NICKNAME] as? String ?: "",
            userCode = data[FIELD_USER_CODE] as? String,
            phone = data[FIELD_PHONE] as? String,
            countryCode = data[FIELD_COUNTRY_CODE] as? String,
            language = data[FIELD_LANGUAGE] as? String,
            photoUrl = data[FIELD_PHOTO_URL] as? String,
            fcmToken = data[FIELD_TOKEN_PUSH] as? String,
            isPremium = data[FIELD_IS_USER_PREMIUM] as? Boolean ?: false,
            subscriptionExpiryDate = data[FIELD_EXPIRY_DATE]?.let { value ->
                (value as? Timestamp)?.toDate()?.toInstant()?.toString()
                    ?: value as? String
            },
            subscriptionType = data[FIELD_SUBSCRIPTION_TYPE] as? String,
            subscriptionPlatform = data[FIELD_SUBSCRIPTION_PLATFORM] as? String,
            createdAt = data[FIELD_REGISTRATION_DATE]?.let { value ->
                (value as? Timestamp)?.toDate()?.toInstant()?.toString()
                    ?: value as? String
            },
            firebaseImageUrl = data[FIELD_FIREBASE_IMAGE_URL] as? String,
            firebaseThumbnailUrl = data[FIELD_FIREBASE_THUMBNAIL_URL] as? String,
            image = data[FIELD_IMAGE] as? String,
            thumbnail = data[FIELD_THUMBNAIL] as? String,
            thumbnailType = data[FIELD_THUMBNAIL_TYPE] as? String,
        )
    }

    private companion object {
        const val USERS_COLLECTION = "Users"
        const val FIELD_EXPIRY_DATE = "subscriptionExpiryDate"
        const val FIELD_SUBSCRIPTION_TYPE = "subscriptionType"
        const val FIELD_SUBSCRIPTION_PLATFORM = "subscriptionPlatform"
        const val FIELD_EMAIL = "email"
        const val FIELD_NAME = "name"
        const val FIELD_NICKNAME = "nickname"
        const val FIELD_USER_CODE = "codeUser"
        const val FIELD_PHONE = "number"
        const val FIELD_COUNTRY_CODE = "countryCode"
        const val FIELD_LANGUAGE = "language"
        const val FIELD_PHOTO_URL = "photoUrl"
        const val FIELD_TOKEN_PUSH = "tokenPush"
        const val FIELD_IS_USER_PREMIUM = "isUserPremium"
        const val FIELD_REGISTRATION_DATE = "userDateRegister"
        const val FIELD_FIREBASE_IMAGE_URL = "firebaseImageUrl"
        const val FIELD_FIREBASE_THUMBNAIL_URL = "firebaseThumbnailUrl"
        const val FIELD_IMAGE = "image"
        const val FIELD_THUMBNAIL = "thumbnail"
        const val FIELD_THUMBNAIL_TYPE = "thumbnailType"
    }
}