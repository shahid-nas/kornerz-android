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
 * The Android port exposes a [thumbnailURL] projection; unlike the iOS
 * port (which derives it from `IPFS.baseUrl + image`), the Android
 * port returns `null` per the Firebase Storage v4 migration (see
 * `FIREBASE_STORAGE_MIGRATION.md`).
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
     * `IPFS.baseUrl + image`; the Android port returns `null` per the
     * Firebase Storage v4 migration (see
     * `FIREBASE_STORAGE_MIGRATION.md`).
     */
    val thumbnailURL: String? get() = null

    /**
     * The deeplink type for the notification, derived from [type].
     * Mirrors iOS `UserNotification.notificationType` (lines 44–76).
     */
    val deeplinkType: UserNotificationDeeplinkType
        get() = UserNotificationDeeplinkType.fromRaw(type)
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
        fun fromRaw(raw: String?): UserNotificationDeeplinkType = when (raw) {
            "newEvent" -> NEW_EVENT
            "newArticle" -> NEW_ARTICLE
            "newForum" -> NEW_FORUM
            "videoCall" -> VIDEO_CALL
            "newComment" -> NEW_COMMENT
            "newLoop" -> NEW_LOOP
            "loopComment" -> LOOP_COMMENT
            else -> UNKNOWN
        }
    }
}

/**
 * A [UserNotification] extension that returns the deeplink URI for
 * the notification given the resolved [Community]. Mirrors iOS
 * `UserNotification.deeplink(with: Community?)` (lines 44–76).
 */
fun UserNotification.deeplink(with: Community?): String? = when (deeplinkType) {
    UserNotificationDeeplinkType.NEW_LOOP, UserNotificationDeeplinkType.LOOP_COMMENT ->
        reference?.let { "kornerz://loop/$it" }
    UserNotificationDeeplinkType.NEW_COMMENT ->
        reference?.let { "kornerz://forum/$it" }
    UserNotificationDeeplinkType.NEW_EVENT -> with?.let { "kornerz://community/${it.id}/event" }
    UserNotificationDeeplinkType.NEW_ARTICLE -> with?.let { "kornerz://community/${it.id}/article" }
    UserNotificationDeeplinkType.NEW_FORUM -> with?.let { "kornerz://community/${it.id}/forum" }
    UserNotificationDeeplinkType.VIDEO_CALL -> with?.let { "kornerz://community/${it.id}/call" }
    UserNotificationDeeplinkType.UNKNOWN -> null
}
