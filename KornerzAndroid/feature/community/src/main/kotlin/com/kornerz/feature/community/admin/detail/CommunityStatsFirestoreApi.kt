package com.kornerz.feature.community.admin.detail

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.kornerz.feature.community.data.network.CommunityMembersFirestoreApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore API for the seven per-community counts surfaced on
 * `CommunityDetailAsAdmin`. Mirrors the read counters on iOS
 * `OwnedCommunityViewModel.swift` (lines 33–92) — only the
 * collection/field names are translated to the Android Firestore
 * backend (the iOS source uses different field conventions on a
 * handful of collections; see each count's doc-comment).
 *
 * All counts use Firestore's server-side `count()` aggregation
 * (`count().get(AggregateSource.SERVER).await()`) so each call
 * transfers only an `Int`. Document models are never parsed here —
 * this API is read-only and stateless.
 *
 * **Talking now** is intentionally omitted: iOS hard-codes the value
 * to "0" (see `CommunityDetailAsAdminVC.setupUI()` lines 186–191);
 * the Android port does the same.
 */
@Singleton
class CommunityStatsFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    /**
     * `Notifications.whereField("notificationCommunityId", == communityId)`
     * count. iOS queries `Notifications.whereField("communityId", ==)`
     * directly against iOS model; the Android backend stores the
     * community filter on `notificationCommunityId` (per
     * `feature/notifications/.../NotificationFirestoreApi.kt` line 75),
     * so we mirror that.
     */
    suspend fun fetchNotificationsCount(communityId: String): Int =
        countWhere(NOTIFICATIONS, FIELD_NOTIFICATION_COMMUNITY_ID, communityId)

    /**
     * `Events.whereField("eventCommunityId", == communityId)` count.
     * iOS uses `eventCommunityId IN [communityName, communityId]`
     * (`APIUserOwnedCommunity.getEventsCountForCommunity` lines
     * 48–80); the Android port filters on the community code only,
     * which matches the Android `Event` storage convention.
     */
    suspend fun fetchEventsCount(communityId: String): Int =
        countWhere(EVENTS, FIELD_EVENT_COMMUNITY_ID, communityId)

    /**
     * `Posts.whereField("postCommunity", == communityId)` count
     * (iOS calls these "articles" — they're rendered on the
     * `Articles` stat tile on `CommunityDetailAsAdminVC`). Mirrors
     * `APIUserOwnedCommunity.getArticlesCountForCommunity` lines
     * 83–113.
     */
    suspend fun fetchArticlesCount(communityId: String): Int =
        countWhere(POSTS, FIELD_POST_COMMUNITY, communityId)

    /**
     * `Videos.whereField("videoCommunity", == communityId)` count.
     * Mirrors `APIUserOwnedCommunity.getVideosCountForCommunity`
     * lines 117–148.
     */
    suspend fun fetchVideosCount(communityId: String): Int =
        countWhere(VIDEOS, FIELD_VIDEO_COMMUNITY, communityId)

    /**
     * `Forum.whereField("forumCommunityId", == communityId)` count.
     * Mirrors `APIUserOwnedCommunity.getForumsCountForCommunity`
     * lines 150–160.
     */
    suspend fun fetchForumsCount(communityId: String): Int =
        countWhere(FORUM, FIELD_FORUM_COMMUNITY_ID, communityId)

    /**
     * `RequestEvent.whereField("codeCommunity", == communityId) AND
     * "analysed" == false` count. Mirrors iOS
     * `APIUserOwnedCommunity.getAllPendingEventRequestsFor` lines
     * 197–231 — only the integer count is consumed by the Android
     * detail screen; the per-row `[RequestEventParticipation]` list
     * is `PendingRequestEventListVC`'s payload and is deferred.
     */
    suspend fun fetchPendingEventRequestsCount(communityId: String): Int = runCatching {
        val snapshot = firestore.collection(REQUEST_EVENT)
            .whereEqualTo(FIELD_REQUEST_CODE_COMMUNITY, communityId)
            .whereEqualTo(FIELD_REQUEST_ANALYSED, false)
            .count()
            .get(AggregateSource.SERVER)
            .await()
        snapshot.count.toInt()
    }.getOrDefault(0)

    /**
     * `CommunityMembers.whereField("communityId", == communityId)`
     * — delegated to the existing count API. iOS reads from the
     * `Fellow` collection by `communityName` instead, but the
     * Android backend stores admin-membership rows in
     * `CommunityMembers` (see `CommunityMembersFirestoreApi`).
     */
    suspend fun fetchMembersCount(communityId: String, membersApi: CommunityMembersFirestoreApi): Int =
        membersApi.fetchCount(communityId)

    private suspend fun countWhere(
        collectionName: String,
        field: String,
        value: String,
    ): Int = runCatching {
        val snapshot = firestore.collection(collectionName)
            .whereEqualTo(field, value)
            .count()
            .get(AggregateSource.SERVER)
            .await()
        snapshot.count.toInt()
    }.getOrDefault(0)

    /**
     * Runs the seven independent count queries in parallel and pairs
     * each result with its slot name. Mirrors iOS' fire-and-forget
     * `OwnedCommunityViewModel` calls — the dashboard resolves
     * per-tile as each callback returns.
     */
    suspend fun fetchAllInParallel(
        communityId: String,
        membersApi: CommunityMembersFirestoreApi,
    ): Map<String, Int> = coroutineScope {
        listOf(
            async { "members" to fetchMembersCount(communityId, membersApi) },
            async { "events" to fetchEventsCount(communityId) },
            async { "articles" to fetchArticlesCount(communityId) },
            async { "videos" to fetchVideosCount(communityId) },
            async { "forums" to fetchForumsCount(communityId) },
            async { "notifications" to fetchNotificationsCount(communityId) },
            async { "pendingEventRequests" to fetchPendingEventRequestsCount(communityId) },
        ).awaitAll().toMap()
    }

    companion object {
        const val NOTIFICATIONS = "Notifications"
        const val EVENTS = "Events"
        const val POSTS = "Posts"
        const val VIDEOS = "Videos"
        const val FORUM = "Forum"
        const val REQUEST_EVENT = "RequestEvent"

        const val FIELD_NOTIFICATION_COMMUNITY_ID = "notificationCommunityId"
        const val FIELD_EVENT_COMMUNITY_ID = "eventCommunityId"
        const val FIELD_POST_COMMUNITY = "postCommunity"
        const val FIELD_VIDEO_COMMUNITY = "videoCommunity"
        const val FIELD_FORUM_COMMUNITY_ID = "forumCommunityId"
        const val FIELD_REQUEST_CODE_COMMUNITY = "codeCommunity"
        const val FIELD_REQUEST_ANALYSED = "analysed"
    }
}
