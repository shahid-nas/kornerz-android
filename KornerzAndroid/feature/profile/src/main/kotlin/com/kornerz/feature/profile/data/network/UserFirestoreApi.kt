package com.kornerz.feature.profile.data.network

import android.graphics.Bitmap
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.kornerz.core.model.User
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore API for the `Users/{email}` document.
 *
 * **Document key convention: the iOS app uses `Users/{email}` as the
 * document key (see iOS `FireStoreServiceManager.swift` line 21
 * `db.collection("Users").document(user.email).setData(from: user)`
 * and line 54 `db.collection("Users").document(userId).setData(...)`
 * where `userId` is set to `first.documentID` = email in
 * `loginUserWith`).** The Android P1 auth layer
 * (`FirestoreAuthApi.signUp`) follows the same convention; this P6
 * module matches both.
 *
 * Methods that take `userCode` as the parameter use a
 * `whereEqualTo("codeUser", userCode)` query to find the document
 * before writing. Methods that take `email` use it directly as the
 * document key.
 */
@Singleton
class UserFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    /**
     * Fetches the [User] document at `Users/{email}`. Returns `null`
     * if the document is missing.
     */
    suspend fun fetchByEmail(email: String): User? {
        if (email.isBlank()) return null
        val snapshot = firestore.collection(USERS_COLLECTION)
            .document(email)
            .get()
            .await()
        if (!snapshot.exists()) return null
        return snapshot.toUserOrNull()?.copy(id = email)
    }

    /**
     * Resolves a user by their `codeUser` (a.k.a. `userCode`) field
     * via `whereEqualTo` — used by the P7 `AdminCommunitiesRepository`
     * and the P5 `Loops` detail screens.
     */
    suspend fun fetchByCode(userCode: String): User? {
        if (userCode.isBlank()) return null
        val snapshot = firestore.collection(USERS_COLLECTION)
            .whereEqualTo(FIELD_USER_CODE, userCode)
            .limit(1)
            .get()
            .await()
        val doc = snapshot.documents.firstOrNull() ?: return null
        return doc.toUserOrNull()?.copy(id = doc.id)
    }

    suspend fun updateName(email: String, newName: String): Result<Unit> = runCatching {
        firestore.collection(USERS_COLLECTION)
            .document(email)
            .set(mapOf(FIELD_NAME to newName), SetOptions.merge())
            .await()
    }

    suspend fun updateNickName(email: String, newNickName: String): Result<Unit> = runCatching {
        firestore.collection(USERS_COLLECTION)
            .document(email)
            .set(mapOf(FIELD_NICKNAME to newNickName), SetOptions.merge())
            .await()
    }

    /**
     * Writes the `firebaseImageUrl` + `firebaseThumbnailUrl` + legacy
     * `image` + `thumbnail` fields in one batch (mirrors iOS
     * `UserProfileRepository.uploadUserProfileImage` lines 107–118
     * which writes all four). The legacy `image` / `thumbnail` fields
     * are kept populated for backwards compatibility with the iOS
     * computed `imageURL` / `thumbnailURL` IPFS branches — even
     * though the v4 Firebase Storage migration makes them unused.
     *
     * @param storageHash A content-hash style identifier (e.g. the
     *   Firebase Storage download URL or a legacy IPFS CID). iOS uses
     *   the upload result hash here; the Android port uses the
     *   download URL string for the legacy fields, mirroring the
     *   v4 migration.
     */
    suspend fun updateProfileImage(
        email: String,
        firebaseImageUrl: String,
        firebaseThumbnailUrl: String,
        legacyImageHash: String,
        legacyThumbnailHash: String,
    ): Result<Unit> = runCatching {
        firestore.collection(USERS_COLLECTION)
            .document(email)
            .set(
                mapOf(
                    FIELD_FIREBASE_IMAGE_URL to firebaseImageUrl,
                    FIELD_FIREBASE_THUMBNAIL_URL to firebaseThumbnailUrl,
                    FIELD_IMAGE to legacyImageHash,
                    FIELD_THUMBNAIL to legacyThumbnailHash,
                ),
                SetOptions.merge(),
            )
            .await()
    }

    /**
     * Mirrors iOS `FireStoreServiceManager.updateRegistrationDate
     * (userId:)` (lines 78–83): if the user document has no
     * `userDateRegister` field, stamp it with the seed date
     * `2024-03-25T00:00:00Z`. Used by `UserProfileRepository.refresh`
     * on the first session after the v4 migration.
     */
    suspend fun updateRegistrationDateIfNeeded(email: String): Result<Unit> = runCatching {
        val snapshot = firestore.collection(USERS_COLLECTION)
            .document(email)
            .get()
            .await()
        if (!snapshot.exists()) return@runCatching
        val existing = snapshot.getTimestamp(FIELD_REGISTRATION_DATE)
        if (existing != null) return@runCatching
        val seedCalendar = Calendar.getInstance().apply {
            clear()
            set(2024, Calendar.MARCH, 25, 0, 0, 0)
        }
        firestore.collection(USERS_COLLECTION)
            .document(email)
            .set(
                mapOf(FIELD_REGISTRATION_DATE to com.google.firebase.Timestamp(seedCalendar.time)),
                SetOptions.merge(),
            )
            .await()
    }

    /**
     * Decodes a `Users/{email}` document into a [User].
     *
     * P8 hard fix: the four premium/subscription fields are now mapped.
     * Previously this function dropped all four, so **every** Profile read
     * returned a user that looked permanently free — which made both the
     * Profile row's trailing label and the paywall gate's entitlement
     * input wrong for every user, including an iOS subscriber whose
     * `subscriptionExpiryDate` was months in the future.
     *
     * Note that mapping `isUserPremium` does **not** make it the premium
     * decision input: the gate and the row both read
     * [User.isUserPremiumNow], which is derived from
     * `subscriptionExpiryDate`. The bit is still mapped because it is part
     * of the shared document shape and the DataStore round trip.
     */
    private fun com.google.firebase.firestore.DocumentSnapshot.toUserOrNull(): User? {
        val data = data ?: return null
        val email = (data[FIELD_EMAIL] as? String) ?: return null
        val name = (data[FIELD_NAME] as? String) ?: ""
        val nickname = (data[FIELD_NICKNAME] as? String) ?: ""
        return User(
            id = email,
            email = email,
            displayName = name,
            nickname = nickname,
            userCode = data[FIELD_USER_CODE] as? String,
            phone = data[FIELD_PHONE] as? String,
            countryCode = data[FIELD_COUNTRY_CODE] as? String,
            language = data[FIELD_LANGUAGE] as? String,
            photoUrl = data[FIELD_PHOTO_URL] as? String,
            fcmToken = data[FIELD_TOKEN_PUSH] as? String,
            // P8: the four premium fields, previously dropped on the floor.
            isPremium = data[FIELD_IS_USER_PREMIUM] as? Boolean ?: false,
            subscriptionExpiryDate = data[FIELD_SUBSCRIPTION_EXPIRY]?.toInstantString(),
            subscriptionType = data[FIELD_SUBSCRIPTION_TYPE] as? String,
            subscriptionPlatform = data[FIELD_SUBSCRIPTION_PLATFORM] as? String,
            firebaseImageUrl = data[FIELD_FIREBASE_IMAGE_URL] as? String,
            firebaseThumbnailUrl = data[FIELD_FIREBASE_THUMBNAIL_URL] as? String,
            image = data[FIELD_IMAGE] as? String,
            thumbnail = data[FIELD_THUMBNAIL] as? String,
            thumbnailType = data[FIELD_THUMBNAIL_TYPE] as? String,
        )
    }

    /**
     * Normalises a Firestore subscription-expiry value to the ISO-8601
     * `String?` the [User] model stores.
     *
     * The Kotlin-side type is `String?` because `SessionRecord` serializes
     * the whole [User] into one DataStore JSON document (a Firestore
     * `Timestamp` is not `@Serializable`). Android writes a `Timestamp`;
     * an older document may hold an ISO-8601 string. Both are accepted, and
     * an unrecognised shape degrades to `null` — which makes
     * `isUserPremiumNow` `false` rather than crashing the Profile screen.
     */
    private fun Any.toInstantString(): String? = when (this) {
        is com.google.firebase.Timestamp -> runCatching {
            kotlinx.datetime.Instant.fromEpochMilliseconds(toDate().time).toString()
        }.getOrNull()
        is String -> this
        else -> null
    }

    /**
     * Compress a bitmap to JPEG bytes at [quality] (0–100).
     */
    companion object {
        fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            return out.toByteArray()
        }

        /**
         * Scale [bitmap] to fit inside a [maxDimension] x [maxDimension]
         * box (mirrors iOS `UserProfileRepository.resizeImageTo`
         * lines 81–90, which uses 200-pt max dimension).
         */
        fun resizeForThumbnail(bitmap: Bitmap, maxDimension: Int = THUMBNAIL_MAX_PT): Bitmap {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= maxDimension && h <= maxDimension) return bitmap
            val ratio = maxDimension.toFloat() / maxOf(w, h)
            return Bitmap.createScaledBitmap(
                bitmap,
                (w * ratio).toInt().coerceAtLeast(1),
                (h * ratio).toInt().coerceAtLeast(1),
                true,
            )
        }

        private const val THUMBNAIL_MAX_PT = 200
        private const val USERS_COLLECTION = "Users"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_NAME = "name"
        private const val FIELD_NICKNAME = "nickname"
        private const val FIELD_USER_CODE = "codeUser"
        private const val FIELD_PHONE = "number"
        private const val FIELD_COUNTRY_CODE = "countryCode"
        private const val FIELD_LANGUAGE = "language"
        private const val FIELD_PHOTO_URL = "photoUrl"
        private const val FIELD_TOKEN_PUSH = "tokenPush"
        // P8 — the four premium/subscription keys, added alongside the
        // existing P1/P6 set. `isUserPremium` is mapped but never used as
        // the premium decision input; see `User.isUserPremiumNow`.
        private const val FIELD_IS_USER_PREMIUM = "isUserPremium"
        private const val FIELD_SUBSCRIPTION_EXPIRY = "subscriptionExpiryDate"
        private const val FIELD_SUBSCRIPTION_TYPE = "subscriptionType"
        private const val FIELD_SUBSCRIPTION_PLATFORM = "subscriptionPlatform"
        private const val FIELD_FIREBASE_IMAGE_URL = "firebaseImageUrl"
        private const val FIELD_FIREBASE_THUMBNAIL_URL = "firebaseThumbnailUrl"
        private const val FIELD_IMAGE = "image"
        private const val FIELD_THUMBNAIL = "thumbnail"
        private const val FIELD_THUMBNAIL_TYPE = "thumbnailType"
        private const val FIELD_REGISTRATION_DATE = "userDateRegister"
    }
}
