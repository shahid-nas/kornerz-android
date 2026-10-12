package com.kornerz.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The canonical Kornerz user record. Mirrors the iOS `User` struct
 * (`kornerz/Kornerz/Models/User.swift`); Android-specific JSON names are
 * chosen to match the iOS `CodingKeys` so the same Firestore document
 * shape works on both platforms.
 *
 * Subscription fields (`subscriptionExpiryDate`, `subscriptionType`,
 * `subscriptionPlatform`) are nullable because the majority of users in
 * P1 are free tier; they are populated by the billing flow introduced in
 * P8.
 *
 * P6 adds the avatar URL projection fields (`imageURL`, `thumbnailURL`,
 * `thumbnailType`) which mirror `Models/User.swift` lines 61–84 — the
 * iOS port derives the avatar URL from the document's
 * `firebaseImageUrl` / `firebaseThumbnailUrl` / `image` / `thumbnail`
 * fields plus the IPFS base URL. The Android port stores the same
 * fields but exposes them as nullable projections since the Firebase
 * Storage v4 migration drops the IPFS path (see
 * `FIREBASE_STORAGE_MIGRATION.md`).
 */
@Serializable
data class User(
    val id: String,
    val email: String,
    @SerialName("name") val displayName: String,
    @SerialName("nickname") val nickname: String,
    @SerialName("codeUser") val userCode: String? = null,
    @SerialName("number") val phone: String? = null,
    @SerialName("countryCode") val countryCode: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("photoUrl") val photoUrl: String? = null,
    @SerialName("tokenPush") val fcmToken: String? = null,
    @SerialName("isUserPremium") val isPremium: Boolean = false,
    val subscriptionExpiryDate: String? = null,
    val subscriptionType: String? = null,
    val subscriptionPlatform: String? = null,
    @SerialName("userDateRegister") val createdAt: String? = null,
    // P6 avatar projection (mirrors iOS `Models/User.swift` lines 61–84)
    @SerialName("firebaseImageUrl") val firebaseImageUrl: String? = null,
    @SerialName("firebaseThumbnailUrl") val firebaseThumbnailUrl: String? = null,
    @SerialName("image") val image: String? = null,
    @SerialName("thumbnail") val thumbnail: String? = null,
    @SerialName("thumbnailType") val thumbnailType: String? = null,
) {
    /**
     * The fully-resolved avatar URL. Prefers the Firebase Storage URL
     * (v4 migration) and falls back to the legacy IPFS path. Mirrors
     * the iOS `user.imageURL` computed property.
     */
    val imageURL: String?
        get() = firebaseImageUrl.takeUnless { it.isNullOrEmpty() }
            ?: image?.let { IPFS_BASE_URL + it }

    /**
     * The fully-resolved avatar thumbnail URL. Prefers the Firebase
     * Storage URL (v4 migration) and falls back to the legacy IPFS
     * path. Mirrors the iOS `User.thumnailURL` computed property
     * (`Models/User.swift` lines 61–67).
     */
    val thumbnailURL: String?
        get() = firebaseThumbnailUrl.takeUnless { it.isNullOrEmpty() }
            ?: firebaseImageUrl.takeUnless { it.isNullOrEmpty() }
            ?: (thumbnail ?: image)?.let { IPFS_BASE_URL + it }

    /** Returns a copy with [newName] applied. */
    fun updateName(newName: String): User = copy(displayName = newName)

    /** Returns a copy with [newNickName] applied. */
    fun updateNickName(newNickName: String): User = copy(nickname = newNickName)

    private companion object {
        /**
         * Legacy IPFS gateway. The v4 Firebase Storage migration
         * (`FIREBASE_STORAGE_MIGRATION.md`) makes the IPFS branch
         * unreachable in practice, but the iOS source-of-truth keeps
         * the fallback for backwards compatibility.
         */
        const val IPFS_BASE_URL = "https://ipfs.kornerz.io/ipfs/"
    }
}

/**
 * The editable fields surfaced by the EditBasicInfo screen. Mirrors
 * iOS `EditBasicInfoVC.BasicInfoCellType` (P6, `phase-6-profile-and-
 * notifications` task 5.1). Only `NAME` and `NICKNAME` rows expose the
 * "Change" affordance; `EMAIL`, `PHONE`, `PASSWORD` are read-only.
 */
enum class EditableField(val key: String) {
    NAME("name"),
    EMAIL("email"),
    PHONE("phone"),
    NICKNAME("nickname"),
    PASSWORD("password"),
}