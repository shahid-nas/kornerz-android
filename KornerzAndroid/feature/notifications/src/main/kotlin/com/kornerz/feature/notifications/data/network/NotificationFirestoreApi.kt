package com.kornerz.feature.notifications.data.network

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kornerz.core.model.UserNotification
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore API for the `Notifications` collection. Mirrors iOS
 * `NotificationRepository.fetchNotifications` (P6, task 4.1 / 4.2).
 */
@Singleton
class NotificationFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    /**
     * Fetches one page of notifications for [userCode] ordered by
     * `dateNotification` descending, with a 10-item page size. When
     * [lastSnapshot] is non-null, the query resumes after it.
     *
     * Returns the page and the new cursor (or `null` if the page is
     * empty).
     */
    suspend fun fetchPage(
        userCode: String,
        lastSnapshot: DocumentSnapshot?,
    ): Result<Pair<List<UserNotification>, DocumentSnapshot?>> = runCatching {
        var query: Query = firestore.collection(NOTIFICATIONS_COLLECTION)
            .whereEqualTo(FIELD_USER_ID, userCode)
            .orderBy(FIELD_DATE, Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }
        val snapshot = query.get().await()
        val items = snapshot.documents.mapNotNull { it.toUserNotification() }
        val next = if (items.isEmpty()) null else snapshot.documents.last()
        items to next
    }

    /**
     * Fetches one page of notifications for the given [communityIds]
     * filter. Mirrors iOS
     * `NotificationRepository.fetchNotificationsForCommunities`
     * (P6, task 4.2). Kept available even though the chip filter is
     * not surfaced in the live UI this phase.
     */
    suspend fun fetchNotificationsForCommunities(
        userCode: String,
        communityIds: List<String>,
        lastSnapshot: DocumentSnapshot?,
    ): Result<Pair<List<UserNotification>, DocumentSnapshot?>> = runCatching {
        if (communityIds.isEmpty()) return@runCatching emptyList<UserNotification>() to null
        var query: Query = firestore.collection(NOTIFICATIONS_COLLECTION)
            .whereEqualTo(FIELD_USER_ID, userCode)
            .whereIn(FIELD_COMMUNITY_ID, communityIds)
            .orderBy(FIELD_DATE, Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }
        val snapshot = query.get().await()
        val items = snapshot.documents.mapNotNull { it.toUserNotification() }
        val next = if (items.isEmpty()) null else snapshot.documents.last()
        items to next
    }

    private companion object {
        const val NOTIFICATIONS_COLLECTION = "Notifications"
        const val FIELD_USER_ID = "notificationUserId"
        const val FIELD_COMMUNITY_ID = "notificationCommunityId"
        const val FIELD_DATE = "dateNotification"
        const val PAGE_SIZE = 10L
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toUserNotification(): UserNotification? {
    val data = data ?: return null
    val id = (data["notificationId"] as? String) ?: return null
    val title = data["notificationTitle"] as? String
    val description = data["notificationDescription"] as? String
    val content = data["notificationContent"] as? String
    val image = data["notificationCommunityImage"] as? String
    val date = (data["dateNotification"] as? com.google.firebase.Timestamp)?.toDate()
        ?.toInstant()?.toKotlinInstant()
    val reference = data["notificationReferenceId"] as? String
    val type = data["notificationType"] as? String
    val userId = data["notificationUserId"] as? String
    val communityId = data["notificationCommunityId"] as? String
    return UserNotification(
        id = id,
        title = title,
        description = description,
        content = content,
        image = image,
        date = date,
        reference = reference,
        type = type,
        userId = userId,
        communityId = communityId,
    )
}

private fun java.time.Instant.toKotlinInstant(): kotlinx.datetime.Instant =
    kotlinx.datetime.Instant.fromEpochMilliseconds(this.toEpochMilli())
