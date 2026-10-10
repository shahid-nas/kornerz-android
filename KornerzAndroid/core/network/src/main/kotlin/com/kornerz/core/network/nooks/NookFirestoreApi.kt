package com.kornerz.core.network.nooks

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kornerz.core.model.Korner
import com.kornerz.core.model.Nook
import com.kornerz.core.model.NookDetail
import com.kornerz.core.model.UpcomingNookItem
import com.kornerz.core.model.User
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore reads for the Nooks tab and Nook detail screen.
 *
 * The Firestore calls mirror the iOS `NooksTabViewModel` /
 * `EventsRepository` / `CommunitiesRepository` / `UsersRepository`
 * contract so the same documents surface in both apps:
 *
 *  - **Live tab** (iOS `fetchKornerz()`):
 *    `Communities.whereField("communityApproved", true)
 *                .orderBy("viewsCount", desc)
 *                .limit(30)`
 *  - **Upcoming tab** (iOS `fetchUpcommingNooks()`):
 *    `Events.whereField("eventDateFinish", >=, now)
 *           .whereField("eventDateFinish", <=, now + 30d)
 *           .whereField("eventType", ==, "open")
 *           .limit(20)`
 *    then 3 parallel joins:
 *      `Users.whereField("codeUser", in, publisherIds)`,
 *      `Communities.whereField("communityId", in, communityIds)`.
 *  - **Nook detail** (iOS detail VM):
 *    `Events/{nookId}.get()` then
 *    `Communities.whereField("communityId", ==, eventCommunityId).get()`.
 *
 * Each `suspend` method returns `Result<T>` (architecture §2.3).
 */
@Singleton
class NookFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    /**
     * Live tab — page of approved Korners ordered by `viewsCount`
     * descending. Mirrors iOS `communitiesRepo.fetchCommunities(after:
     * communityLastSnapshot, orderby: .viewsCount)` (see
     * `CommunitiesRepository.swift:167`).
     */
    suspend fun fetchLiveKornerzPage(
        after: DocumentSnapshot?,
        limit: Long = LIVE_PAGE_LIMIT,
    ): Result<KornerPage> = runCatching {
        var query: Query = firestore.collection(COLLECTION_COMMUNITIES)
            .whereEqualTo(FIELD_COMMUNITY_APPROVED, true)
            .orderBy(FIELD_VIEWS_COUNT, Query.Direction.DESCENDING)
            .limit(limit)
        if (after != null) query = query.startAfter(after)
        val snap = query.get().await()
        val items = snap.documents.mapNotNull { it.data?.toKorner(it.id) }
        KornerPage(
            items = items,
            lastSnapshot = snap.documents.lastOrNull(),
            hasMore = snap.documents.size.toLong() == limit,
        )
    }

    /**
     * Upcoming tab — page of open Events for the next [days] days,
     * ordered by `eventDateFinish` ascending. Mirrors iOS
     * `eventRepository.fetchEventsForNextFewDays(after: eventLastSnapshot,
     * days: 30, limit: 20)` (see `EventsRepository.swift:76`).
     *
     * The returned page contains the raw `Nook` items plus the
     * cursor. The caller is expected to call [fetchUsersByUserCode] +
     * [fetchKornerzByCommunityId] in parallel to build an
     * `UpcomingNookItem` list — that two-call pattern mirrors iOS
     * `NooksTabViewModel.fetchUpcommingNooks` exactly.
     */
    suspend fun fetchUpcomingEventsPage(
        after: DocumentSnapshot?,
        days: Int = 30,
        limit: Long = UPCOMING_PAGE_LIMIT,
    ): Result<NookPage> = runCatching {
        val now = Timestamp.now()
        val end = Timestamp(java.util.Date(System.currentTimeMillis() + days * MS_PER_DAY))
        var query: Query = firestore.collection(COLLECTION_EVENTS)
            .whereGreaterThanOrEqualTo(FIELD_EVENT_DATE_FINISH, now)
            .whereLessThanOrEqualTo(FIELD_EVENT_DATE_FINISH, end)
            .whereEqualTo(FIELD_EVENT_TYPE, "open")
            .orderBy(FIELD_EVENT_DATE_FINISH, Query.Direction.ASCENDING)
            .limit(limit)
        if (after != null) query = query.startAfter(after)
        val snap = query.get().await()
        val items = snap.documents.mapNotNull { it.data?.toNook(it.id) }
        NookPage(
            items = items,
            lastSnapshot = snap.documents.lastOrNull(),
            hasMore = snap.documents.size.toLong() == limit,
        )
    }

    /**
     * Resolves a list of `publisherId` values (from `Nook.publisherId`)
     * to `User` records. Mirrors iOS
     * `usersRepository.fetchUsers(in: userIds)` — iOS matches on the
     * `codeUser` field of the top-level `Users` collection (see
     * `UsersRepository.swift:37`).
     *
     * An empty input short-circuits to an empty list (iOS does the
     * same — `UsersRepository.swift:39`).
     */
    suspend fun fetchUsersByUserCode(userCodes: List<String>): Result<List<User>> = runCatching {
        val cleaned = userCodes.filter { it.isNotBlank() }.distinct().take(MAX_WHERE_IN)
        if (cleaned.isEmpty()) return@runCatching emptyList()
        val snap = firestore.collection(COLLECTION_USERS)
            .whereIn(FIELD_USER_CODE, cleaned)
            .get()
            .await()
        snap.documents.mapNotNull { it.data?.toUser(it.id) }
    }

    /**
     * Resolves a list of `communityId` values (from
     * `Nook.eventCommunityId`) to `Korner` records. Mirrors iOS
     * `communitiesRepo.fetchCommunities(in: communityIds)` — iOS
     * matches on the `communityId` field of the top-level
     * `Communities` collection (see
     * `CommunitiesRepository.swift:56`).
     *
     * An empty input short-circuits to an empty list (iOS does the
     * same).
     */
    suspend fun fetchKornerzByCommunityId(communityIds: List<String>): Result<List<Korner>> = runCatching {
        val cleaned = communityIds.filter { it.isNotBlank() }.distinct().take(MAX_WHERE_IN)
        if (cleaned.isEmpty()) return@runCatching emptyList()
        val snap = firestore.collection(COLLECTION_COMMUNITIES)
            .whereIn(FIELD_COMMUNITY_ID, cleaned)
            .get()
            .await()
        snap.documents.mapNotNull { it.data?.toKorner(it.id) }
    }

    /**
     * Two-step read for the Nook detail screen. Mirrors iOS detail VM:
     *  1. `Events/{nookId}.get()` for the nook (uses the
     *     top-level `Events` collection just like the upcoming tab).
     *  2. `Communities.whereField("communityId", ==,
     *     eventCommunityId).get()` for the parent korner.
     */
    suspend fun fetchNookDetail(nookId: String): Result<NookDetail> = runCatching {
        if (nookId.isBlank()) error("nookId must not be blank")
        val nookSnap = firestore.collection(COLLECTION_EVENTS)
            .document(nookId)
            .get()
            .await()
        if (!nookSnap.exists()) error("Nook $nookId not found")
        val nook = nookSnap.data?.toNook(nookSnap.id)
            ?: error("Nook $nookId could not be mapped")
        val communityId = nook.communityId
            ?: error("Nook $nookId has no eventCommunityId")
        val kornerList = fetchKornerzByCommunityId(listOf(communityId)).getOrThrow()
        val korner = kornerList.firstOrNull()
            ?: error("Parent Korner $communityId not found")
        NookDetail(nook = nook, korner = korner.copy(isLive = nook.isLive))
    }

    /** Joins an `UpcomingNookItem` aggregate from raw Nooks, Users and
     *  Korners. Mirrors iOS `NooksTabViewModel.fetchUpcommingNooks`
     *  step 4 (the `NookCommunityWrapper` build loop). Items missing
     *  either a publisher or a parent Korner are dropped — matches
     *  iOS `guard let publisherId … continue` behavior. */
    suspend fun joinUpcoming(
        nooks: List<Nook>,
    ): Result<List<UpcomingNookItem>> = runCatching {
        val publisherCodes = nooks.mapNotNull { it.publisherId }.distinct()
        val communityIds = nooks.mapNotNull { it.communityId }.distinct()
        coroutineScope {
            val usersDeferred = async { fetchUsersByUserCode(publisherCodes).getOrThrow() }
            val kornersDeferred = async { fetchKornerzByCommunityId(communityIds).getOrThrow() }
            val users = usersDeferred.await()
            val korners = kornersDeferred.await()
            val usersByCode: Map<String, User> =
                users.mapNotNull { u -> u.userCode?.let { it to u } }.toMap()
            // The fetched korners all have a `communityId` field
            // matching one of the input ids. We re-key on that field
            // value (the Firestore `id` is the document id, which
            // may differ from the `communityId` value).
            val kornersByCommunityId: Map<String, Korner> = korners.associateBy { it.id }
            nooks.mapNotNull { nook ->
                val publisher = nook.publisherId?.let { usersByCode[it] }
                val korner = nook.communityId?.let { kornersByCommunityId[it] }
                if (korner == null) {
                    Log.w(
                        TAG,
                        "Upcoming nook ${nook.id} has no parent Korner (communityId=${nook.communityId}), dropping per iOS contract",
                    )
                    return@mapNotNull null
                }
                UpcomingNookItem(nook = nook, publisher = publisher, parentKorner = korner)
            }
        }
    }

    private fun Map<String, Any?>.toNook(id: String): Nook? {
        // iOS Event.CodingKeys — keep field names aligned so the same
        // Firestore document is consumable by both apps.
        val title = (get(FIELD_EVENT_TITLE) as? String) ?: return null
        val communityId = (get(FIELD_EVENT_COMMUNITY_ID) as? String).orEmpty()
        val startDate = (get(FIELD_EVENT_DATE_INIT) as? Timestamp)
            ?.toInstant()?.toKotlinInstant()
            ?: Instant.DISTANT_PAST
        val endDate = (get(FIELD_EVENT_DATE_FINISH) as? Timestamp)
            ?.toInstant()?.toKotlinInstant()
        val type = (get(FIELD_EVENT_TYPE) as? String)
        // iOS Event.isLive is derived from type (closed/live) and
        // session state; the Android approximation uses
        // `type == "live"` so the P3 Nook.isLive keeps its
        // boolean shape without changing the model schema.
        val isLive = type?.lowercase() == "live"
        return Nook(
            id = id,
            // P3 fields (kept populated so HomeFirestoreApi and
            // KornerFirestoreApi consumers keep working).
            communityId = communityId,
            title = title,
            coverUrl = (get(FIELD_FIREBASE_GUEST_IMAGE_URL) as? String)
                ?: (get(FIELD_GUEST_IMAGE) as? String),
            startDate = startDate,
            isLive = isLive,
            // iOS Event parity fields.
            eventCommunityId = communityId,
            eventTitle = title,
            eventDateInit = startDate,
            eventDateFinish = endDate,
            type = type,
            sessionId = (get(FIELD_SESSION_ID) as? String),
            publisherId = (get(FIELD_PUBLISHER_ID) as? String),
        )
    }

    private fun Map<String, Any?>.toKorner(id: String): Korner? {
        val name = (get(FIELD_COMMUNITY_NAME) as? String)
            ?: (get(FIELD_COMMUNITY_NAME_EN) as? String)
            ?: return null
        return Korner(
            id = id,
            name = name,
            themeColor = (get(FIELD_COMMUNITY_THEME) as? String).orEmpty(),
            coverUrl = (get(FIELD_FIREBASE_IMAGE_URL) as? String)
                ?: (get(FIELD_COMMUNITY_IMAGE) as? String),
            description = (get(FIELD_COMMUNITY_DESCRIPTION) as? String)
                ?: (get(FIELD_COMMUNITY_DESCRIPTION_EN) as? String),
            membersCount = parseMembersCount(get(FIELD_COMMUNITY_MEMBERS_COUNT)),
            isLive = false,
            isJoined = false,
        )
    }

    private fun Map<String, Any?>.toUser(id: String): User? {
        val email = (get(FIELD_USER_EMAIL) as? String) ?: return null
        val displayName = (get(FIELD_USER_DISPLAY_NAME) as? String)
            ?: (get(FIELD_USER_NICKNAME) as? String)
            ?: return null
        return User(
            id = (get(FIELD_USER_ID) as? String) ?: id,
            email = email,
            displayName = displayName,
            nickname = (get(FIELD_USER_NICKNAME) as? String).orEmpty(),
            userCode = (get(FIELD_USER_CODE) as? String),
            photoUrl = (get(FIELD_FIREBASE_IMAGE_URL) as? String)
                ?: (get(FIELD_USER_PHOTO_URL) as? String),
        )
    }

    private fun parseMembersCount(raw: Any?): Int = when (raw) {
        is Long -> raw.toInt()
        is Int -> raw
        is String -> raw.toIntOrNull() ?: 0
        else -> 0
    }

    /** A single page of Korners for the live tab. */
    data class KornerPage(
        val items: List<Korner>,
        val lastSnapshot: DocumentSnapshot?,
        val hasMore: Boolean,
    )

    /** A single page of Nooks for the upcoming tab. */
    data class NookPage(
        val items: List<Nook>,
        val lastSnapshot: DocumentSnapshot?,
        val hasMore: Boolean,
    )

    private companion object {
        const val TAG = "NookFirestoreApi"

        const val COLLECTION_COMMUNITIES = "Communities"
        const val COLLECTION_EVENTS = "Events"
        const val COLLECTION_USERS = "Users"

        // Communities fields (iOS Community.CodingKeys).
        const val FIELD_COMMUNITY_ID = "communityId"
        const val FIELD_COMMUNITY_NAME = "communityName"
        const val FIELD_COMMUNITY_NAME_EN = "communityNameEn"
        const val FIELD_COMMUNITY_APPROVED = "communityApproved"
        const val FIELD_COMMUNITY_THEME = "communityTheme"
        const val FIELD_COMMUNITY_DESCRIPTION = "communityDescription"
        const val FIELD_COMMUNITY_DESCRIPTION_EN = "communityDescriptionEn"
        const val FIELD_COMMUNITY_MEMBERS_COUNT = "communityNumberMembers"
        const val FIELD_COMMUNITY_IMAGE = "communityImage"
        const val FIELD_FIREBASE_IMAGE_URL = "firebaseImageUrl"
        const val FIELD_VIEWS_COUNT = "viewsCount"

        // Events fields (iOS Event.CodingKeys).
        const val FIELD_EVENT_TITLE = "eventTitle"
        const val FIELD_EVENT_DESCRIPTION = "eventDescription"
        const val FIELD_EVENT_COMMUNITY_ID = "eventCommunityId"
        const val FIELD_EVENT_DATE_INIT = "eventDateInit"
        const val FIELD_EVENT_DATE_FINISH = "eventDateFinish"
        const val FIELD_EVENT_TYPE = "eventType"
        const val FIELD_SESSION_ID = "sessionId"
        const val FIELD_PUBLISHER_ID = "publisherId"
        const val FIELD_FIREBASE_GUEST_IMAGE_URL = "firebaseGuestImageUrl"
        const val FIELD_GUEST_IMAGE = "guestImage"

        // Users fields (iOS User.CodingKeys).
        const val FIELD_USER_EMAIL = "email"
        const val FIELD_USER_DISPLAY_NAME = "name"
        const val FIELD_USER_NICKNAME = "nickname"
        const val FIELD_USER_PHOTO_URL = "photoUrl"
        const val FIELD_USER_ID = "id"
        const val FIELD_USER_CODE = "codeUser"

        // Page sizes — mirror iOS default limits.
        const val LIVE_PAGE_LIMIT = 30L
        const val UPCOMING_PAGE_LIMIT = 20L
        const val MAX_WHERE_IN = 30
        const val MS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
