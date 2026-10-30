package com.kornerz.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The canonical Kornerz user-notification record. Mirrors the iOS
 * `UserNotification` struct
 * (`kornerz/Kornerz/Models/UserNotification.swift` lines 11–42)
 * field-for-field. Android's `SerialName`s match the iOS `CodingKeys`
 * so the same Firestore document shape works on both platforms.
 *
 * The Android port exposes a [thumbnailURL] projection that resolves
 * Firebase Storage URLs directly and falls back to the legacy IPFS path.
 */
@Serializable
data class UserNotification(
    @SerialName("notificationId") val id: String,
    @SerialName("notificationTitle") val title: String? = null,
    @SerialName("notificationDescription") val description: String? = null,
    @SerialName("notificationContent") val content: String? = null,
    @SerialName("notificationCommunityImage") val image: String? = null,
    @SerialName("dateNotification") val date: kotlinx.datetime.Instant? = null,
    @SerialName("notificationReferenceId") val reference: String? = null,
    @SerialName("notificationType") val type: String? = null,
    @SerialName("notificationUserId") val userId: String? = null,
    @SerialName("notificationCommunityId") val communityId: String? = null,
) {
    /**
     * Derived thumbnail URL. The iOS port builds
     * `IPFS.baseUrl + image`; the Android port keeps the same fallback
     * while preferring an already-resolved Firebase Storage URL.
     */
    val thumbnailURL: String?
        get() = image?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else IPFS_BASE_URL + it
        }

    /**
     * The deeplink type for the notification, derived from [type].
     * Mirrors iOS `UserNotification.notificationType` (lines 44–76).
     */
    val deeplinkType: UserNotificationDeeplinkType
        get() = UserNotificationDeeplinkType.fromRaw(type)

    companion object {
        const val IPFS_BASE_URL: String = "https://ipfs.kornerz.io/ipfs/"
    }
}

/**
 * The deeplink type for a [UserNotification]. Mirrors iOS
 * `UserNotification.UserNotificationType` (P6, lines 44–76).
 */
enum class UserNotificationDeeplinkType {
    NEW_EVENT,
    NEW_ARTICLE,
    NEW_FORUM,
    VIDEO_CALL,
    NEW_COMMENT,
    NEW_LOOP,
    LOOP_COMMENT,
    UNKNOWN;

    companion object {
        /**
         * Parses the raw `notificationType` the server writes.
         *
         * The raw values are iOS's `DeeplinkType` raw values
         * (`kornerz/Kornerz/DeepLinks/DeeplinkManager.swift:56–64`) —
         * `NewEvent`, `NewArticle`, `NewForum`, `VideoCall`,
         * `NewComment`, `NewLoop`, `LoopComment`. The comparison is
         * **case-insensitive**, so the legacy lower-camel spellings
         * (`newEvent`, …) keep parsing too.
         *
         * Before P10 the comparison was case-**sensitive** against the
         * lower-camel spellings only, so every real notification —
         * which carries a capitalised value — parsed as
         * [UNKNOWN] and no notification in the app was routable.
         */
        fun fromRaw(raw: String?): UserNotificationDeeplinkType = when (raw?.lowercase()) {
            "newevent" -> NEW_EVENT
            "newarticle" -> NEW_ARTICLE
            "newforum" -> NEW_FORUM
            "videocall" -> VIDEO_CALL
            "newcomment" -> NEW_COMMENT
            "newloop" -> NEW_LOOP
            "loopcomment" -> LOOP_COMMENT
            else -> UNKNOWN
        }
    }
}

/**
 * A [UserNotification] extension that resolves the notification's
 * deep link, given the resolved [Community]. Mirrors iOS
 * `UserNotification.deeplink(with: Community?)` (lines 50–75).
 *
 * The iOS per-case field requirements are honoured exactly:
 *
 * | type | requires | resolves to |
 * |---|---|---|
 * | `newEvent` | reference + community id | [DeepLink.NewEvent] |
 * | `newArticle` | reference + community id | [DeepLink.NewArticle] |
 * | `newForum` | reference + community id | [DeepLink.NewForum] |
 * | `videoCall` | community id | [DeepLink.VideoCall] |
 * | `newComment` | reference | [DeepLink.NewComment] |
 * | `newLoop` | reference + community id | [DeepLink.NewLoop] |
 * | `loopComment` | reference | [DeepLink.NewLoopComment] |
 *
 * **This deliberately does not port iOS's `newComment` bug.** iOS
 * `Models/UserNotification.swift:65–67` returns `.videoCall(...)` for
 * the `.newComment` case; that branch is unreachable in iOS because
 * `NotificationsViewModel.notificationOpened` short-circuits
 * `.newComment` before calling `deeplink(with:)`. Android resolves
 * `.newComment` to [DeepLink.NewComment].
 *
 * The community id is read from [Community.communityId], falling back
 * to [Community.id] when the former is null — the repositories
 * populate both, but a community assembled elsewhere may only carry
 * the legacy id.
 */
fun UserNotification.deeplink(with: Community?): DeepLink? {
    val communityId = with?.communityId?.takeIf { it.isNotBlank() }
        ?: with?.id?.takeIf { it.isNotBlank() }
    return when (deeplinkType) {
        UserNotificationDeeplinkType.NEW_EVENT ->
            if (reference != null && communityId != null) {
                DeepLink.NewEvent(eventId = reference, communityId = communityId)
            } else {
                null
            }

        UserNotificationDeeplinkType.NEW_ARTICLE ->
            if (reference != null && communityId != null) {
                DeepLink.NewArticle(articleId = reference, communityId = communityId)
            } else {
                null
            }

        UserNotificationDeeplinkType.NEW_FORUM ->
            if (reference != null && communityId != null) {
                DeepLink.NewForum(forumId = reference, communityId = communityId)
            } else {
                null
            }

        UserNotificationDeeplinkType.VIDEO_CALL ->
            communityId?.let { DeepLink.VideoCall(communityId = it) }

        UserNotificationDeeplinkType.NEW_COMMENT ->
            reference?.let { DeepLink.NewComment(forumId = it) }

        UserNotificationDeeplinkType.NEW_LOOP ->
            if (reference != null && communityId != null) {
                DeepLink.NewLoop(communityId = communityId, loopId = reference)
            } else {
                null
            }

        UserNotificationDeeplinkType.LOOP_COMMENT ->
            reference?.let { DeepLink.NewLoopComment(loopId = it) }

        UserNotificationDeeplinkType.UNKNOWN -> null
    }
}
