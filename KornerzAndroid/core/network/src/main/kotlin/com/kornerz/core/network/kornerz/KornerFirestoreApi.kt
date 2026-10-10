package com.kornerz.core.network.kornerz

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kornerz.core.model.Korner
import com.kornerz.core.model.KornerDetail
import com.kornerz.core.model.Nook
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
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
 *    three parallel reads merged: `Communities/{id}` (the korner),
 *    `Communities/{id}/events` (the nooks), and
 *    `Communities/{id}/members` (capped at 50 per iOS UI).
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
     * `Communities/{id}` + `Communities/{id}/events` +
     * `Communities/{id}/members` (capped at 50). Returns
     * `Result.failure` if the parent `Communities/{id}` document is
     * missing; partial reads (zero events, zero members) are still a
     * `Result.success` with empty lists.
     *
     * `isLive` is derived from the nooks aggregate (matches iOS
     * `KornerDetailViewModel` semantics where the live badge reflects
     * the underlying events collection, not a denormalized field).
     */
    suspend fun fetchKornerDetail(kornerId: String): Result<KornerDetail> = runCatching {
        if (kornerId.isBlank()) error("kornerId must not be blank")
        coroutineScope {
            val kornerDeferred = async {
                firestore.collection(COLLECTION_COMMUNITIES)
                    .document(kornerId)
                    .get()
                    .await()
            }
            val nooksDeferred = async {
                firestore.collection(COLLECTION_COMMUNITIES)
                    .document(kornerId)
                    .collection(COLLECTION_EVENTS)
                    .get()
                    .await()
            }
            val membersDeferred = async {
                firestore.collection(COLLECTION_COMMUNITIES)
                    .document(kornerId)
                    .collection(COLLECTION_MEMBERS)
                    .limit(MEMBERS_LIMIT)
                    .get()
                    .await()
            }
            val kornerSnap = kornerDeferred.await()
            val nooksSnap = nooksDeferred.await()
            val membersSnap = membersDeferred.await()
            val baseKorner = kornerSnap.data?.toKorner(kornerId)
                ?: error("Korner document $kornerId not found")
            val nooks = nooksSnap.documents.mapNotNull { it.data?.toNook(it.id) }
            val members = membersSnap.documents.mapNotNull { it.data?.toUser(it.id) }
            val live = nooks.any { it.isLive }
            val korner = baseKorner.copy(
                isLive = live,
                isJoined = baseKorner.isJoined,
            )
            KornerDetail(korner = korner, nooks = nooks, members = members)
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
        )
    }

    private fun parseMembersCount(raw: Any?): Int = when (raw) {
        is Long -> raw.toInt()
        is Int -> raw
        is String -> raw.toIntOrNull() ?: 0
        else -> 0
    }

    private fun Map<String, Any?>.toNook(id: String): Nook? {
        val title = (get(FIELD_NOOK_TITLE) as? String) ?: return null
        val start = (get(FIELD_NOOK_START_DATE) as? Timestamp)
            ?: return null
        return Nook(
            id = id,
            communityId = (get(FIELD_COMMUNITY_ID) as? String).orEmpty(),
            title = title,
            coverUrl = (get(FIELD_FIREBASE_IMAGE_URL) as? String),
            startDate = start.toInstant().toKotlinInstant(),
            isLive = (get(FIELD_IS_LIVE) as? Boolean) ?: false,
        )
    }

    private fun Map<String, Any?>.toUser(id: String): com.kornerz.core.model.User? {
        val email = (get(FIELD_USER_EMAIL) as? String) ?: return null
        val displayName = (get(FIELD_USER_DISPLAY_NAME) as? String)
            ?: (get(FIELD_USER_NICKNAME) as? String)
            ?: return null
        return com.kornerz.core.model.User(
            id = (get(FIELD_USER_ID) as? String) ?: id,
            email = email,
            displayName = displayName,
            nickname = (get(FIELD_USER_NICKNAME) as? String).orEmpty(),
            photoUrl = (get(FIELD_USER_PHOTO_URL) as? String),
        )
    }

    private companion object {
        const val TAG = "KornerFirestoreApi"

        // Collection / field names mirror iOS `Community.CodingKeys`
        // (`kornerz/Kornerz/Models/Community.swift`). Firestore IDs
        // are case-sensitive — using lowercase here would silently
        // return an empty result.
        const val COLLECTION_COMMUNITIES = "Communities"
        const val COLLECTION_EVENTS = "events"
        const val COLLECTION_MEMBERS = "members"

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
        const val FIELD_NOOK_TITLE = "title"
        const val FIELD_NOOK_START_DATE = "startDate"
        const val FIELD_IS_LIVE = "isLive"
        const val FIELD_USER_EMAIL = "email"
        const val FIELD_USER_DISPLAY_NAME = "name"
        const val FIELD_USER_NICKNAME = "nickname"
        const val FIELD_USER_PHOTO_URL = "photoUrl"
        const val FIELD_USER_ID = "userId"
        const val FIELD_VIEWS_COUNT = "viewsCount"

        const val LIST_QUERY_LIMIT = 30L
        const val MEMBERS_LIMIT = 50L
    }
}
