package com.kornerz.core.network.kornerz

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kornerz.core.model.Korner
import com.kornerz.core.model.KornerDetail
import com.kornerz.core.model.Nook
import com.kornerz.core.model.User
import com.kornerz.core.network.fellow.FellowRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore reads for the Kornerz tab and Korner detail screens.
 *
 * Mirrors the iOS `CommunitiesRepository` (`kornerz/Kornerz/Backend/
 * Repositories/CommunitiesRepository.swift`) and
 * `KornerDetailViewModel.fetchData()` (`.swiftui.Dashboard.Korner.
 * KornerDetail`) — same collection, same field names, same `where` /
 * `order` semantics:
 *  - [fetchAllKornerz] mirrors
 *    `CommunitiesRepository.fetchCommunities(after:orderby:)` called
 *    from the "Recommended" carousel in `KornerzCarouselViewModel` —
 *    `Communities` collection filtered by `communityApproved == true`,
 *    ordered by `communityName` **descending**, limited to 30.
 *  - [fetchKornerDetail] mirrors `KornerDetailViewModel.fetchData()` —
 *    three parallel reads merged: the `Communities` document (the
 *    korner), the top-level `Events` collection (the nooks), and the
 *    `Fellow` + `Users` collections (the members).
 *
 * `isLive` is **derived** from the nooks list (true if any nook has
 * `isLive = true`); iOS has no `isLive` field on `Community`. The
 * same applies to `isJoined` — the iOS app derives it from the
 * signed-in user's fellow records, not from the community document;
 * for now the list defaults to `false` (joining the Kornerz community
 * is not in iOS live flow per `proposal.md` non-goals). The detail
 * screen overrides `isLive` from the nooks aggregate.
 *
 * Each suspend method returns a `Result<T>` so the calling repository
 * decides how to surface errors (architecture §2.3 — no exceptions
 * thrown past the data layer).
 */
@Singleton
class KornerFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val fellowRepository: FellowRepository,
) {

    /**
     * Discovers every approved Korner. Mirrors iOS
     * `CommunitiesRepository.fetchCommunities(after:orderby:.name)` —
     * `Communities` collection filtered by `communityApproved == true`,
     * ordered by `communityName` descending, limited to 30.
     *
     * If the composite index for `Communities.whereEqualTo(communityApproved)
     * .orderBy(communityName)` is missing, the query fails and we fall
     * back to a client-side sort to keep the tab populated.
     */
    suspend fun fetchAllKornerz(): Result<List<Korner>> = runCatching {
        try {
            firestore.collection(COLLECTION_COMMUNITIES)
                .whereEqualTo(FIELD_COMMUNITY_APPROVED, true)
                .orderBy(FIELD_COMMUNITY_NAME, Query.Direction.DESCENDING)
                .limit(LIST_QUERY_LIMIT)
                .get()
                .await()
                .documents
                .mapNotNull { it.data?.toKorner(it.id) }
        } catch (t: Throwable) {
            Log.w(TAG, "fetchAllKornerz index missing, falling back to client-side sort: ${t.message}")
            firestore.collection(COLLECTION_COMMUNITIES)
                .whereEqualTo(FIELD_COMMUNITY_APPROVED, true)
                .get()
                .await()
                .documents
                .mapNotNull { it.data?.toKorner(it.id) }
                .sortedByDescending { it.name.lowercase() }
        }
    }

    /**
     * Discovers the "New this week" carousel — same collection + filter
     * as [fetchAllKornerz] but ordered by `viewsCount` descending.
     * Mirrors iOS `KornerzCarouselViewModel.fetchRecentCommunities()`
     * which calls `CommunitiesRepository.fetchCommunities(after:)` —
     * the default overload orders by `viewsCount` descending.
     */
    suspend fun fetchRecentKornerz(): Result<List<Korner>> = runCatching {
        firestore.collection(COLLECTION_COMMUNITIES)
            .whereEqualTo(FIELD_COMMUNITY_APPROVED, true)
            .orderBy(FIELD_VIEWS_COUNT, Query.Direction.DESCENDING)
            .limit(LIST_QUERY_LIMIT)
            .get()
            .await()
            .documents
            .mapNotNull { it.data?.toKorner(it.id) }
    }

    /**
     * Resolves a list of followed community names (from the signed-in
     * user's `Fellow` documents) into the matching approved `Korner`
     * records. Mirrors iOS `KornerzCarouselViewModel.fetchFollowingCommunities()`
     * which delegates to `CommunitiesRepository.fetchFollowingCommunities(for:)`.
     *
     * Empty / blank names short-circuit to an empty list. The 30-item
     * `whereIn` cap matches the iOS `queryLimit` — caller must batch
     * larger lists.
     */
    suspend fun fetchFollowingKornerzByNames(communityNames: List<String>): Result<List<Korner>> = runCatching {
        val cleaned = communityNames.filter { it.isNotBlank() }.distinct().take(30)
        if (cleaned.isEmpty()) return@runCatching emptyList()
        firestore.collection(COLLECTION_COMMUNITIES)
            .whereIn(FIELD_COMMUNITY_NAME, cleaned)
            .whereEqualTo(FIELD_COMMUNITY_APPROVED, true)
            .get()
            .await()
            .documents
            .mapNotNull { it.data?.toKorner(it.id) }
    }

    /**
     * Three parallel Firestore reads for the Korner detail screen:
     * the community document, the community's nooks, and the
     * community's members.
     *
     * **Identifier resolution.** [kornerId] may be either the Firestore
     * **document id** or the community's public **`communityId` field**
     * — they are different values on the same document (iOS
     * `APICommunity.getCommunityDetailsFor` resolves by the field and
     * then overwrites the model id with the document id). The Home
     * tab's "Your Kornerz" carousel carries the `communityId` field
     * (see `CommunitiesRepository.fetchFollowingCommunities`), while
     * the Kornerz tab carries the document id, so both must resolve.
     *
     * **Nooks** come from the top-level `Events` collection, matched by
     * `eventCommunityId in [communityName, communityId]` — iOS
     * `EventsRepository.fetchCommunityEvents` reads the same collection
     * (there is no `Communities/{id}/events` subcollection).
     *
     * **Members** come from the top-level `Fellow` collection, matched
     * by `fellowCommunity in [communityId, communityName, communityNameEn]`
     * — iOS `FellowRepository.fetchCommunityFellows` — and are then
     * joined against the `Users` collection by `codeUser`
     * (iOS `UsersRepository.fetchUsers(in:)`). There is no
     * `Communities/{id}/members` subcollection.
     *
     * A missing community document is a `Result.failure`; a failed
     * nook/member read degrades to an empty list so the header still
     * renders (iOS prints the error and keeps the screen alive).
     */
    suspend fun fetchKornerDetail(
        kornerId: String,
        currentUserCode: String? = null,
    ): Result<KornerDetail> = runCatching {
        if (kornerId.isBlank()) error("kornerId must not be blank")
        val doc = resolveCommunityDocument(kornerId)
        val baseKorner = doc.data?.toKorner(doc.id)
            ?: error("Korner document $kornerId not found")
        val lookupKeys = doc.data?.communityLookupKeys().orEmpty()
        coroutineScope {
            val nooksDeferred = async { fetchCommunityNooks(lookupKeys) }
            val membersDeferred = async { fetchCommunityMembers(lookupKeys) }
            // iOS `KornerDetailViewModel.fetchData` runs
            // `checkIfFellowExists` as its own query alongside the
            // follower fetch — the follow state is NOT derived from the
            // member list, which iOS caps at 50 entries and which
            // therefore may not contain the signed-in user's own
            // record.
            val followingDeferred = async {
                currentUserCode?.takeIf { it.isNotBlank() }?.let { code ->
                    fellowRepository.fellowExists(code, lookupKeys).getOrDefault(false)
                } ?: false
            }
            val nooks = nooksDeferred.await()
            val members = membersDeferred.await()
            val isJoined = followingDeferred.await()
            val live = nooks.any { it.isLive }
            val korner = baseKorner.copy(
                isLive = live,
                // iOS `KornerDetailViewModel.checkIfFellowExists` derives
                // the joined flag from the signed-in user's `Fellow`
                // record, not from the community document.
                isJoined = isJoined,
            )
            KornerDetail(korner = korner, nooks = nooks, members = members)
        }
    }

    /**
     * Follows the Korner addressed by [kornerId] on behalf of
     * [userCode].
     *
     * Mirrors iOS `KornerDetailViewModel.followKorner()`
     * (`kornerz/Kornerz/SwiftUI/Dashboard/Korner/KornerDetail/KornerDetailViewModel.swift:340`),
     * which calls `FellowRepository.followCommunity(userId:community:)`
     * — the `Fellow` document write is reproduced verbatim in
     * [FellowRepository.followCommunity].
     *
     * The value written to `fellowCommunity` is the community's
     * `communityName` — the key both platforms resolve a followed
     * community by (iOS `CommunitiesRepository.fetchFollowingCommunities`
     * queries `Communities.whereField("communityName", in: fellows'
     * fellowCommunity)`).
     */
    suspend fun followCommunity(kornerId: String, userCode: String): Result<Unit> = runCatching {
        if (kornerId.isBlank()) error("kornerId must not be blank")
        if (userCode.isBlank()) error("userCode must not be blank")
        val doc = resolveCommunityDocument(kornerId)
        val communityName = doc.data?.get(FIELD_COMMUNITY_NAME) as? String
            ?: doc.data?.get(FIELD_COMMUNITY_NAME_EN) as? String
            ?: error("Korner document $kornerId has no community name")
        fellowRepository.followCommunity(userCode, communityName)
    }

    /**
     * Unfollows the Korner addressed by [kornerId] on behalf of
     * [userCode].
     *
     * Mirrors iOS `KornerDetailViewModel.unfollowKorner()`
     * (`KornerDetailViewModel.swift:352`), which calls
     * `FellowRepository.unfollowCommunity(userId:community:)`.
     *
     * The community is matched by **every** key it is known by
     * (`communityId`, `communityName`, `communityNameEn`) so a follow
     * written by either iOS path — the SwiftUI Korner detail screen
     * writes `communityNameEn`, the legacy community screen writes
     * `communityName` — is removed.
     */
    suspend fun unfollowCommunity(kornerId: String, userCode: String): Result<Unit> = runCatching {
        if (kornerId.isBlank()) error("kornerId must not be blank")
        if (userCode.isBlank()) error("userCode must not be blank")
        val doc = resolveCommunityDocument(kornerId)
        val lookupKeys = doc.data?.communityLookupKeys().orEmpty()
        fellowRepository.unfollowCommunity(userCode, lookupKeys)
    }

    /**
     * Resolves the `Communities` document addressed by [id], which may
     * be the document id or the `communityId` field value.
     */
    private suspend fun resolveCommunityDocument(id: String): com.google.firebase.firestore.DocumentSnapshot {
        val byDocumentId = firestore.collection(COLLECTION_COMMUNITIES)
            .document(id)
            .get()
            .await()
        if (byDocumentId.exists()) return byDocumentId
        val byCommunityId = firestore.collection(COLLECTION_COMMUNITIES)
            .whereEqualTo(FIELD_COMMUNITY_ID, id)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
        return byCommunityId ?: error("Korner document $id not found")
    }

    /**
     * The community's admins — the "Kornerstones" tab of the Korner
     * detail screen. Mirrors iOS `KornerDetailViewModel.fetchLeaders`,
     * which reads the `CommunityAdm` collection
     * (`AdminCommunitiesRepository.fetchCommunityAdmins`:
     * `whereField("communityId", isEqualTo: communityId)`) and joins the
     * `userId` of every admin record against the `Users` collection.
     *
     * [kornerId] is resolved the same way as in [fetchKornerDetail] — a
     * Firestore document id **or** the community's public `communityId`
     * field — and the admin records are matched against every lookup
     * key the community is known by (`communityId`, `communityName`,
     * `communityNameEn`).
     *
     * A failed read degrades to an empty list so the screen still
     * renders (iOS prints the error and keeps the screen alive).
     */
    suspend fun fetchCommunityAdmins(kornerId: String): Result<List<User>> = runCatching {
        if (kornerId.isBlank()) error("kornerId must not be blank")
        val doc = resolveCommunityDocument(kornerId)
        val lookupKeys = doc.data?.communityLookupKeys().orEmpty()
        fetchCommunityAdminsByKeys(lookupKeys)
    }

    private suspend fun fetchCommunityAdminsByKeys(lookupKeys: List<String>): List<User> {
        if (lookupKeys.isEmpty()) return emptyList()
        return try {
            val admins = firestore.collection(COLLECTION_COMMUNITY_ADM)
                .whereIn(FIELD_ADMIN_COMMUNITY_ID, lookupKeys.take(MAX_WHERE_IN))
                .get()
                .await()
                .documents
            // iOS `CommunityAdmin.userId` carries the user's **code** —
            // the same value `Users.codeUser` is keyed by.
            val userCodes = admins.mapNotNull { it.data?.get(FIELD_ADMIN_USER_ID) as? String }
                .filter { it.isNotBlank() }
                .distinct()
            if (userCodes.isEmpty()) return emptyList()
            firestore.collection(COLLECTION_USERS)
                .whereIn(FIELD_USER_CODE, userCodes.take(MAX_WHERE_IN))
                .limit(MEMBERS_LIMIT)
                .get()
                .await()
                .documents
                .mapNotNull { it.data?.toUser(it.id) }
        } catch (t: Throwable) {
            Log.w(TAG, "fetchCommunityAdmins failed for $lookupKeys: ${t.message}")
            emptyList()
        }
    }

    /**
     * The community's upcoming nooks — the top-level `Events`
     * collection filtered by `eventCommunityId`. Mirrors iOS
     * `EventsRepository.fetchCommunityEvents(communityName:communityId:)`.
     *
     * The iOS query pairs the `whereField("eventCommunityId", in:)`
     * filter with an `eventDateFinish >= now` range filter, which
     * requires a composite index. The date filter is applied
     * client-side here so the read works without one; events with a
     * missing end date are kept.
     */
    private suspend fun fetchCommunityNooks(lookupKeys: List<String>): List<Nook> {
        if (lookupKeys.isEmpty()) return emptyList()
        return try {
            val now = Timestamp.now()
            firestore.collection(COLLECTION_EVENTS)
                .whereIn(FIELD_EVENT_COMMUNITY_ID, lookupKeys.take(MAX_WHERE_IN))
                .get()
                .await()
                .documents
                .mapNotNull { it.data?.toEventNook(it.id) }
                .filter { nook ->
                    val finish = nook.eventDateFinish
                    finish == null || finish >= now.toInstant().toKotlinInstant()
                }
                .sortedBy { it.startDate }
        } catch (t: Throwable) {
            Log.w(TAG, "fetchCommunityNooks failed for $lookupKeys: ${t.message}")
            emptyList()
        }
    }

    /**
     * The community's members — the `Fellow` records that follow this
     * community, joined against the `Users` collection. Mirrors iOS
     * `FellowRepository.fetchCommunityFellows` + `UsersRepository.fetchUsers`.
     */
    private suspend fun fetchCommunityMembers(
        lookupKeys: List<String>,
    ): List<User> {
        if (lookupKeys.isEmpty()) return emptyList()
        return try {
            val fellowUserNames = fellowRepository
                .fetchCommunityFollowerCodes(lookupKeys, MEMBERS_LIMIT.toLong())
                .getOrDefault(emptyList())
            val userCodes = fellowUserNames.distinct().take(MAX_WHERE_IN)
            val members = if (userCodes.isEmpty()) {
                emptyList()
            } else {
                firestore.collection(COLLECTION_USERS)
                    .whereIn(FIELD_USER_CODE, userCodes)
                    .limit(MEMBERS_LIMIT)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { it.data?.toUser(it.id) }
            }
            members
        } catch (t: Throwable) {
            Log.w(TAG, "fetchCommunityMembers failed for $lookupKeys: ${t.message}")
            emptyList()
        }
    }

    private fun Map<String, Any?>.toKorner(id: String): Korner? {
        val name = (get(FIELD_COMMUNITY_NAME) as? String)
            ?: (get(FIELD_COMMUNITY_NAME_EN) as? String)
            ?: return null
        return Korner(
            // iOS sets `community.id = snapShot.documentID` — match that
            // (don't prefer the `communityId` field which is a separate
            // public identifier).
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
            communityId = (get(FIELD_COMMUNITY_ID) as? String),
            // Mirrors iOS Community.swift CodingKeys: `sessionID =
            // "communitySessionId"`.
            sessionId = (get(FIELD_COMMUNITY_SESSION_ID) as? String)
                ?: (get(FIELD_SESSION_ID) as? String),
        )
    }

    private fun parseMembersCount(raw: Any?): Int = when (raw) {
        is Long -> raw.toInt()
        is Int -> raw
        is String -> raw.toIntOrNull() ?: 0
        else -> 0
    }

    /**
     * The community's lookup keys — the values iOS matches the
     * community's events and fellow records against:
     * `communityId`, `communityName` and `communityNameEn`.
     */
    private fun Map<String, Any?>.communityLookupKeys(): List<String> = listOfNotNull(
        (get(FIELD_COMMUNITY_ID) as? String),
        (get(FIELD_COMMUNITY_NAME) as? String),
        (get(FIELD_COMMUNITY_NAME_EN) as? String),
    ).filter { it.isNotBlank() }.distinct()

    /**
     * Maps a top-level `Events` document onto the [Nook] model.
     * Mirrors iOS `Event.CodingKeys` (`eventTitle`, `eventDateInit`,
     * `eventDateFinish`, `eventCommunityId`, `eventType`, …).
     */
    private fun Map<String, Any?>.toEventNook(id: String): Nook? {
        val title = (get(FIELD_EVENT_TITLE) as? String) ?: return null
        val communityId = (get(FIELD_EVENT_COMMUNITY_ID) as? String)
            ?: (get(FIELD_COMMUNITY_ID) as? String)
            ?: ""
        val startDate = (get(FIELD_EVENT_DATE_INIT) as? Timestamp)
            ?.toInstant()?.toKotlinInstant()
            ?: Instant.DISTANT_PAST
        val endDate = (get(FIELD_EVENT_DATE_FINISH) as? Timestamp)
            ?.toInstant()?.toKotlinInstant()
        val type = (get(FIELD_EVENT_TYPE) as? String)
        return Nook(
            id = id,
            communityId = communityId,
            title = title,
            coverUrl = (get(FIELD_FIREBASE_GUEST_IMAGE_URL) as? String)
                ?: (get(FIELD_GUEST_IMAGE) as? String),
            startDate = startDate,
            // iOS derives the live flag from the event type.
            isLive = type?.lowercase() == FIELD_EVENT_TYPE_LIVE,
            eventCommunityId = communityId,
            eventTitle = title,
            eventDateInit = startDate,
            eventDateFinish = endDate,
            type = type,
            sessionId = (get(FIELD_SESSION_ID) as? String)
                ?: (get(FIELD_SESSION_ID_UPPER) as? String),
            publisherId = (get(FIELD_PUBLISHER_ID) as? String),
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
            photoUrl = (get(FIELD_USER_PHOTO_URL) as? String),
            firebaseImageUrl = (get(FIELD_FIREBASE_IMAGE_URL) as? String),
        )
    }

    private companion object {
        const val TAG = "KornerFirestoreApi"

        // Collection / field names mirror iOS `Community.CodingKeys`
        // (`kornerz/Kornerz/Models/Community.swift`). Firestore IDs
        // are case-sensitive — using lowercase here would silently
        // return an empty result.
        const val COLLECTION_COMMUNITIES = "Communities"
        // Top-level `Events` collection (iOS `EventsRepository`). The
        // old `Communities/{id}/events` subcollection does not exist.
        const val COLLECTION_EVENTS = "Events"
        const val COLLECTION_USERS = "Users"
        // iOS `CommunityAdm` collection (iOS `AdminCommunitiesRepository`).
        const val COLLECTION_COMMUNITY_ADM = "CommunityAdm"

        const val FIELD_COMMUNITY_ID = "communityId"
        const val FIELD_SESSION_ID = "sessionId"
        // Mirrors iOS Community.swift CodingKeys: `sessionID =
        // "communitySessionId"`.
        const val FIELD_COMMUNITY_SESSION_ID = "communitySessionId"
        const val FIELD_COMMUNITY_NAME = "communityName"
        const val FIELD_COMMUNITY_NAME_EN = "communityNameEn"
        const val FIELD_COMMUNITY_APPROVED = "communityApproved"
        const val FIELD_COMMUNITY_THEME = "communityTheme"
        const val FIELD_COMMUNITY_DESCRIPTION = "communityDescription"
        const val FIELD_COMMUNITY_DESCRIPTION_EN = "communityDescriptionEn"
        const val FIELD_COMMUNITY_MEMBERS_COUNT = "communityNumberMembers"
        const val FIELD_COMMUNITY_IMAGE = "communityImage"
        const val FIELD_FIREBASE_IMAGE_URL = "firebaseImageUrl"
        const val FIELD_NOOK_TITLE = "title"
        const val FIELD_NOOK_START_DATE = "startDate"
        const val FIELD_IS_LIVE = "isLive"
        const val FIELD_USER_EMAIL = "email"
        const val FIELD_USER_DISPLAY_NAME = "name"
        const val FIELD_USER_NICKNAME = "nickname"
        const val FIELD_USER_PHOTO_URL = "photoUrl"
        const val FIELD_USER_ID = "userId"
        const val FIELD_USER_CODE = "codeUser"
        const val FIELD_VIEWS_COUNT = "viewsCount"

        // Events fields (iOS `Event.CodingKeys`).
        const val FIELD_EVENT_TITLE = "eventTitle"
        const val FIELD_EVENT_COMMUNITY_ID = "eventCommunityId"
        const val FIELD_EVENT_DATE_INIT = "eventDateInit"
        const val FIELD_EVENT_DATE_FINISH = "eventDateFinish"
        const val FIELD_EVENT_TYPE = "eventType"
        const val FIELD_EVENT_TYPE_LIVE = "live"
        const val FIELD_SESSION_ID_UPPER = "sessionID"
        const val FIELD_PUBLISHER_ID = "publisherId"
        const val FIELD_FIREBASE_GUEST_IMAGE_URL = "firebaseGuestImageUrl"
        const val FIELD_GUEST_IMAGE = "guestImage"

        // CommunityAdm fields (iOS `CommunityAdmin`).
        const val FIELD_ADMIN_COMMUNITY_ID = "communityId"
        const val FIELD_ADMIN_USER_ID = "userId"

        const val LIST_QUERY_LIMIT = 30L
        const val MEMBERS_LIMIT = 50L
        const val MAX_WHERE_IN = 30
    }
}
