package com.kornerz.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Canonical loop record used by every loop-related capability. Mirrors
 * the iOS `Loop` struct (`kornerz/Kornerz/Models/Loop.swift`) field-for-field
 * so the same Firestore document can be decoded by both apps.
 *
 * Every field is nullable to match iOS — the iOS `Loop.init(from decoder:)`
 * calls `decodeIfPresent` for every property (see `Loop.swift:85-105`).
 *
 * Computed properties (`iOsThumbnailUrl`, `iOsImageUrl`, `iOsVideoUrl`,
 * `isVideo`, `videoColorHex`) resolve exclusively from the Firebase Storage
 * URL fields.
 *
 * The P2 fields (`title`, `thumbnailUrl`, `color`, `durationSeconds`,
 * `createdAt`, `publisherName`, `publisherAvatarUrl`, `communityName`)
 * are retained so the P2 Home "Recent at Kornerz" carousel and the
 * existing `core/network/loop/LoopRepository` keep working unchanged.
 * P5 adds the iOS-parity fields on top.
 */
@Serializable
data class Loop(
    @SerialName("id")
    val id: String? = null,
    @SerialName("communityId")
    val communityId: String? = null,
    @SerialName("publisherId")
    val publisherId: String? = null,
    @SerialName("active")
    val active: Boolean? = null,
    @SerialName("title")
    val title: String? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("descriptionHtml")
    val descriptionHtml: String? = null,
    @SerialName("firebaseImageUrl")
    val firebaseImageUrl: String? = null,
    @SerialName("firebaseThumbnailUrl")
    val firebaseThumbnailUrl: String? = null,
    @SerialName("firebaseVideoUrl")
    val firebaseVideoUrl: String? = null,
    @SerialName("viewCounts")
    val viewCounts: Int? = null,
    @SerialName("commentsCount")
    val commentsCount: Int? = null,
    @SerialName("likesCount")
    val likesCount: Int? = null,
    @SerialName("theme")
    val theme: String? = null,
    @SerialName("creationDate")
    val creationDate: kotlinx.datetime.Instant? = null,

    // P2 fields kept for backward-compat with the existing
    // `core/network/loop/LoopRepository` and Home carousel consumers.
    @SerialName("thumbnailUrl")
    val thumbnailUrl: String? = null,
    @SerialName("color")
    val color: String? = null,
    @SerialName("durationSeconds")
    val durationSeconds: Int? = null,
    @SerialName("createdAt")
    val createdAt: kotlinx.datetime.Instant? = null,
    @SerialName("publisherName")
    val publisherName: String? = null,
    @SerialName("publisherAvatarUrl")
    val publisherAvatarUrl: String? = null,
    @SerialName("communityName")
    val communityName: String? = null,
) {

    /** Firebase thumbnail URL, falling back to the Firebase image URL. */
    val iOsThumbnailUrl: String?
        get() = firebaseThumbnailUrl?.takeIf { it.isNotEmpty() }
            ?: firebaseImageUrl?.takeIf { it.isNotEmpty() }

    /** Firebase image URL. */
    val iOsImageUrl: String?
        get() = firebaseImageUrl?.takeIf { it.isNotEmpty() }

    /** Firebase video URL. */
    val iOsVideoUrl: String?
        get() = firebaseVideoUrl?.takeIf { it.isNotEmpty() }

    /** iOS `Loop.isVideo` — true when firebaseVideoUrl is non-empty. */
    val isVideo: Boolean
        get() = !firebaseVideoUrl.isNullOrEmpty()

    /** iOS `Loop.videoColor` — `#FFA500` when video, `#000000` otherwise. */
    val videoColorHex: String
        get() = if (isVideo) "#FFA500" else "#000000"
}
