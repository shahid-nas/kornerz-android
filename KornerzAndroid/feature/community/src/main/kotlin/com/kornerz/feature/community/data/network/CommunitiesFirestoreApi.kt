package com.kornerz.feature.community.data.network

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kornerz.core.model.Community
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore API for the `Communities/{idCommunity}` collection. Mirrors
 * the read surface of iOS
 * `Backend/Repositories/AdminCommunitiesRepository.swift` (P7 + parity
 * fix that mirrors iOS `AdminCommunityListController.viewDidLoad` +
 * `AdminCommunityListViewModel.fetchData()`).
 *
 * The document key in both apps is the 6-char `communityId` /
 * `idCommunity` value (iOS writes `communityId`, Android writes
 * `idCommunity`; the manual mapper below accepts either).
 *
 * The admin-owned-communities list reads via a two-step join that
 * mirrors iOS `AdminCommunityListViewModel` lines 76–118:
 *
 *  1. `CommunityAdm.whereField("userId", == userCode).limit(100)` →
 *     `[CommunityAdm]`, each carrying a `communityId`.
 *  2. `Communities.whereField("communityId", in: ids).
 *        whereField("communityApproved", == true)` →
 *     the actual community docs, looked up in chunks of 30 because
 *     Firestore caps `whereIn` at 30 values per call (iOS line 53
 *     batches in groups of 30).
 */
@Singleton
class CommunitiesFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private val collection = firestore.collection(COLLECTION)

    private val communityAdmCollection = firestore.collection(COLLECTION_COMMUNITY_ADM)

    /**
     * Step 1 of the admin-list fetch. Returns the list of 6-char
     * `communityId`s in the `CommunityAdm` collection whose `userId`
     * matches [userCode], up to 100 rows (iOS limit, line 81 of
     * `AdminCommunitiesRepository.fetchCommunityAdminList`).
     *
     * Mirrors iOS
     * `AdminCommunitiesRepository.fetchCommunityAdminList(userId:after:)`.
     */
    suspend fun fetchCommunityAdmIdsByUser(userCode: String, limit: Long = 100L): Result<List<String>> =
        runCatching {
            if (userCode.isBlank()) return@runCatching emptyList()
            val snapshot = communityAdmCollection
                .whereEqualTo(FIELD_USER_ID, userCode)
                .limit(limit)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.get(FIELD_COMMUNITY_ID) as? String
            }
        }

    /**
     * Step 2 of the admin-list fetch. Returns the approved communities
     * whose 6-char `communityId` is in [communityIds]. The list is
     * automatically chunked in batches of 30 because Firestore caps
     * `whereIn` at 30 values per call (mirrors iOS line 53 chunks).
     *
     * iOS counterpart:
     * `AdminCommunitiesRepository.fetchCommunities(withIds:)`.
     */
    suspend fun fetchByCommunityIds(communityIds: List<String>): Result<List<Community>> =
        runCatching {
            communityIds.filter { it.isNotBlank() }
                .distinct()
                .chunked(WHERE_IN_BATCH_SIZE)
                .flatMap { batch ->
                    val snapshot = collection
                        .whereIn(FIELD_COMMUNITY_ID_IOS, batch)
                        .whereEqualTo(FIELD_COMMUNITY_APPROVED, true)
                        .get()
                        .await()
                    snapshot.documents.mapNotNull { it.toCommunityOrNull() }
                }
        }

    /**
     * Returns the list of communities created by [userCode], sorted by
     * `createdDate` descending and capped at [limit] (default 50).
     * Mirrors iOS `AdminCommunityListController.viewDidLoad` line 41.
     */
    suspend fun fetchOwnedByUser(userCode: String, limit: Long = 50L): Result<List<Community>> =
        runCatching {
            if (userCode.isBlank()) return@runCatching emptyList()
            val snapshot = collection
                .whereEqualTo(FIELD_USER_ID, userCode)
                .orderBy(FIELD_CREATED_DATE, Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toCommunityOrNull()
            }
        }

    /**
     * Returns a single community by its 6-char [code] (the document
     * key), or `null` if missing.
     */
    suspend fun fetchByCode(code: String): Community? {
        if (code.isBlank()) return null
        val doc = collection.document(code).get().await()
        if (!doc.exists()) return null
        return doc.toCommunityOrNull()
    }

    /**
     * Writes [community] to `Communities/{code}` via `setData(...)`
     * (overwrite, not merge) — matches iOS
     * `CommunitiesRepository.createCommunity` line 102.
     */
    suspend fun create(code: String, community: Community): Result<Unit> = runCatching {
        collection.document(code).set(community).await()
    }

    /**
     * Server-side count of `CommunityAdm` rows where `userId ==
     * userCode`. Mirrors iOS
     * `AdminCommunitiesRepository.fetchUserCommunitiesCount`
     * (lines 140–159) — uses Firestore's `count().getAggregation(
     * source: .server)` so only the integer is transferred, no
     * documents are downloaded.
     *
     * Returns 0 when [userCode] is blank.
     */
    suspend fun fetchCommunityAdmCountByUser(userCode: String): Result<Int> = runCatching {
        if (userCode.isBlank()) return@runCatching 0
        val snapshot = communityAdmCollection
            .whereEqualTo(FIELD_USER_ID, userCode)
            .count()
            .get(AggregateSource.SERVER)
            .await()
        snapshot.count.toInt()
    }

    /**
     * Generates a 6-character alphanumeric community code, mirroring
     * iOS `communityRepo.generateCommunityCode` lines 88–95. The
     * collision probability is bounded by the per-user community
     * count (iOS uses the same scheme — see design §"Risks").
     */
    fun generateCode(): String {
        val raw = collection.document().id
        val clean = raw.replace("-", "").take(6).uppercase()
        return clean.ifEmpty { "ABCDEF" }
    }

    /**
     * Maps a Firestore `Communities/{id}` document into a [Community].
     *
     * Tolerant of BOTH schemas so docs written by either platform
     * parse:
     *  - **iOS-written** (`Models/Community.swift` schema): uses the
     *    `community*`-prefixed field names and stores the 6-char
     *    `communityId` in a named field (NOT the documentID).
     *  - **Android-written** (P7 schema): uses the bare field names
     *    (`idCommunity`, `name`, `userId`, `createdDate`, ...) and
     *    uses the 6-char code as both the documentID and the
     *    `idCommunity` field.
     *
     * Each value below falls back from new-schema → old-schema →
     * documentID for the 6-char code, and from new-schema → old-schema
     * for every other field.
     */
    private fun com.google.firebase.firestore.DocumentSnapshot.toCommunityOrNull(): Community? {
        val data = data ?: return null
        val code = (data[FIELD_ID_COMMUNITY] as? String)
            ?: (data[FIELD_COMMUNITY_ID_IOS] as? String)
            ?: id
        val name = (data[FIELD_NAME] as? String)
            ?: (data[FIELD_COMMUNITY_NAME] as? String)
            ?: return null
        val description = (data[FIELD_DESCRIPTION] as? String)
            ?: (data[FIELD_COMMUNITY_DESCRIPTION] as? String)
        val themeIdInt = (data[FIELD_THEME_ID] as? Long)?.toInt()
            ?: (data[FIELD_COMMUNITY_THEME_ID] as? Long)?.toInt()
            ?: 0
        val image = (data[FIELD_IMAGE] as? String)
            ?: (data[FIELD_COMMUNITY_IMAGE] as? String)
        val thumbnail = (data[FIELD_THUMBNAIL] as? String)
            ?: (data[FIELD_COMMUNITY_THUMBNAIL] as? String)
        val firebaseImage = (data[FIELD_FIREBASE_IMAGE_URL] as? String)
            ?: (data[FIELD_COMMUNITY_FIREBASE_IMAGE_URL] as? String)
        val firebaseThumbnail = (data[FIELD_FIREBASE_THUMBNAIL_URL] as? String)
            ?: (data[FIELD_COMMUNITY_FIREBASE_THUMBNAIL_URL] as? String)
        val createdBy = (data[FIELD_USER_ID] as? String)
            ?: (data[FIELD_COMMUNITY_OWNER] as? String)
            ?: ""
        val createdAt = (data[FIELD_CREATED_DATE] as? String)
        return Community(
            id = code,
            code = code,
            name = name,
            description = description,
            themeIdRaw = themeIdInt,
            imageURL = image,
            thumbnailURL = thumbnail,
            firebaseImageURL = firebaseImage,
            firebaseThumbnailURL = firebaseThumbnail,
            isCreator = data[FIELD_IS_CREATOR] as? Boolean ?: false,
            createdByUserId = createdBy,
            createdAt = createdAt,
        )
    }

    private companion object {
        const val COLLECTION = "Communities"
        const val COLLECTION_COMMUNITY_ADM = "CommunityAdm"

        const val WHERE_IN_BATCH_SIZE = 30

        // New (P7 Android-written) schema
        const val FIELD_ID_COMMUNITY = "idCommunity"
        const val FIELD_NAME = "name"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_THEME_ID = "themeId"
        const val FIELD_IMAGE = "image"
        const val FIELD_THUMBNAIL = "thumbnail"
        const val FIELD_FIREBASE_IMAGE_URL = "firebaseImageUrl"
        const val FIELD_FIREBASE_THUMBNAIL_URL = "firebaseThumbnailUrl"
        const val FIELD_IS_CREATOR = "isCreator"
        const val FIELD_USER_ID = "userId"
        const val FIELD_CREATED_DATE = "createdDate"

        // Old (iOS-written) schema — fallbacks so cross-platform docs parse
        const val FIELD_COMMUNITY_ID_IOS = "communityId"
        const val FIELD_COMMUNITY_NAME = "communityName"
        const val FIELD_COMMUNITY_DESCRIPTION = "communityDescription"
        const val FIELD_COMMUNITY_THEME_ID = "communityThemeId"
        const val FIELD_COMMUNITY_IMAGE = "communityImage"
        const val FIELD_COMMUNITY_THUMBNAIL = "communityThumbnail"
        const val FIELD_COMMUNITY_FIREBASE_IMAGE_URL = "firebaseImageUrl"
        const val FIELD_COMMUNITY_FIREBASE_THUMBNAIL_URL = "firebaseThumbnailUrl"
        const val FIELD_COMMUNITY_OWNER = "communityOwner"
        const val FIELD_COMMUNITY_APPROVED = "communityApproved"

        // CommunityAdm row fields (consistent across both platforms)
        const val FIELD_COMMUNITY_ID = "communityId"
    }
}
