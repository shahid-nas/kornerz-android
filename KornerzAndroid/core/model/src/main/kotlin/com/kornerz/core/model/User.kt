package com.kornerz.core.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The canonical Kornerz user record. Mirrors the iOS `User` struct
 * (`kornerz/Kornerz/Models/User.swift`); Android-specific JSON names are
 * chosen to match the iOS `CodingKeys` so the same Firestore document
 * shape works on both platforms.
 *
 * Subscription fields (`subscriptionExpiryDate`, `subscriptionType`,
 * `subscriptionPlatform`) are nullable because the majority of users
 * are free tier. They became live in P8: `SubscriptionFirestoreApi`
 * writes all three on purchase via `updateData` on `Users/{email}`,
 * and `UserFirestoreApi.toUserOrNull()` maps them back on read. The
 * premium decision itself is [isUserPremiumNow], derived from
 * [subscriptionExpiryDate] — never the raw `isPremium` bit, which is
 * write-only on both platforms.
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
    /**
     * **Shared-document compatibility field — NOT the premium gate input.**
     *
     * Written once as `false` at signup by `FirestoreAuthApi.signUp`
     * (`core/network/.../auth/FirestoreAuthApi.kt:90`) and read back by
     * `FirestoreAuthApi.fetchUser` (`:190`). Nothing else in the
     * codebase writes it, on either platform: iOS's
     * `UserRepository.setUserPremium` (`:170-175`) writes only
     * `subscriptionExpiryDate`, `subscriptionType`, and
     * `subscriptionPlatform`, so the bit is permanently `false` in
     * Firestore for every subscriber on both platforms and never flips
     * on renewal or cancellation.
     *
     * **Use [isUserPremiumNow] for every premium decision** — it is the
     * single authority, mirroring iOS `AppHelper.isUserPremium`
     * (`kornerz/Kornerz/Helper/AppHelper.swift:39-45`). See the
     * `premium-entitlement` spec.
     */
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

    /**
     * The parsed [subscriptionExpiryDate], or `null` when the field is
     * absent or malformed.
     *
     * `subscriptionExpiryDate` is stored as an ISO-8601 `String?`
     * because `SessionRecord` serializes the whole [User] into a single
     * DataStore JSON document (a Firestore `Timestamp` is not
     * `@Serializable`) — matching the P1–P7 convention for
     * `createdAt` and `SessionRecord.expiresAt`.
     * `SubscriptionFirestoreApi` converts to a Firestore `Timestamp` on
     * the write path, so the on-wire shape stays identical to iOS's.
     *
     * A malformed value degrades to `null` (free) rather than throwing,
     * so a bad write from the other platform cannot crash the Profile
     * screen. Mirrors the P8 `premium-entitlement` spec.
     */
    val subscriptionExpiryInstant: Instant?
        get() = subscriptionExpiryDate?.let { raw ->
            runCatching { Instant.parse(raw) }.getOrNull()
        }

    /**
     * The **single** input to every premium decision on Android —
     * the paywall gate, the Profile row's trailing label, and any
     * entitlement check.
     *
     * `true` iff [subscriptionExpiryInstant] is strictly after now.
     * Mirrors iOS `AppHelper.isUserPremium`
     * (`kornerz/Kornerz/Helper/AppHelper.swift:39-45`) exactly, which
     * compares `expiryDate > Date()` and ignores the raw `isPremium`
     * bit. Deriving from the expiry is what makes renewal, cancellation,
     * and cross-platform reads correct, none of which the stored bit
     * can express.
     *
     * Uses `>` rather than `Instant.isAfter` because the project pins
     * `kotlinx-datetime` 0.6.1, which predates that extension — this is
     * the same comparison form `SessionRepository.toState()` uses.
     */
    val isUserPremiumNow: Boolean
        get() = subscriptionExpiryInstant?.let { it > Clock.System.now() } == true

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