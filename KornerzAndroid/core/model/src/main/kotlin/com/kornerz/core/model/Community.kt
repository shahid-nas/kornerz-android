package com.kornerz.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `Communities/{idCommunity}` document. Mirrors iOS
 * `Models/Community.swift` field-for-field (P7, `community-domain`
 * spec). The iOS source-of-truth uses Firestore field names that don't
 * align with Kotlin naming — `@SerialName` maps each Kotlin field to
 * the iOS Firestore field name.
 *
 * P3-P6 home/UI consumers expected [themeColor] / [coverUrl] /
 * [isMember] named parameters — they're preserved as P3-P6 default
 * values for backwards compatibility. New code should use
 * [coverImageURL] / [theme] / [userIsAdmin].
 */
@Serializable
data class Community(
    @SerialName("id") val id: String = "",
    @SerialName("idCommunity") val code: String = "",
    /**
     * The community's public `communityId` field — a **different**
     * value from the Firestore document id and from [id].
     *
     * iOS resolves a community by this field (`APICommunity.swift:475`)
     * and then overwrites the model's id with the document id
     * (`APICommunity.swift:483`). Android keeps [id]'s meaning as it
     * is, so the two identifiers are separately nameable here.
     *
     * Nullable with a `null` default so no existing construction site
     * has to change.
     */
    @SerialName("communityId") val communityId: String? = null,
    @SerialName("name") val name: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("themeId") val themeIdRaw: Int = 0,
    @SerialName("image") val imageURL: String? = null,
    @SerialName("thumbnail") val thumbnailURL: String? = null,
    @SerialName("firebaseImageUrl") val firebaseImageURL: String? = null,
    @SerialName("firebaseThumbnailUrl") val firebaseThumbnailURL: String? = null,
    @SerialName("isCreator") val isCreator: Boolean = false,
    @SerialName("userId") val createdByUserId: String = "",
    @SerialName("createdDate") val createdAt: String? = null,
    // P3-P6 legacy parameters (defaulted, not serialized). Kept so
    // previews and P3-P6 `Community(...)` named-argument callers still
    // compile.
    val coverUrl: String? = null,
    val themeColor: String? = null,
    val isMember: Boolean = true,
) {
    /**
     * The community's `CommunityTheme` enum value, derived from
     * [themeIdRaw]. Unknown values fall back to [CommunityTheme.DEFAULT]
     * (mirrors iOS `CommunityThemeId.from(rawValue:)`).
     */
    val theme: CommunityTheme
        get() = CommunityTheme.fromRawValue(themeIdRaw)

    /**
     * The fully-resolved cover image URL. Prefers the Firebase Storage
     * URL and falls back to the legacy document field.
     */
    val coverImageURL: String?
        get() = firebaseImageURL.takeUnless { it.isNullOrEmpty() }
            ?: imageURL.takeUnless { it.isNullOrEmpty() }
            ?: coverUrl

    /**
     * Returns `true` when [currentUserCode] is the admin/creator of
     * this community. Mirrors iOS
     * `UserProfileVC.isCreatorOfCommunity` lines 36–42.
     */
    fun userIsAdmin(currentUserCode: String): Boolean =
        currentUserCode.isNotEmpty() && createdByUserId == currentUserCode
}
