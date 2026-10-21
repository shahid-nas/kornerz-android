package com.kornerz.feature.community.data.network

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kornerz.core.model.Community
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore API for the `Communities/{idCommunity}` collection.
 * Mirrors iOS `CommunitiesRepository.swift` (P7, task 3.1).
 *
 * The document key in the iOS app is the `idCommunity` field value
 * (6-char alphanumeric). The Android port uses the same key.
 */
@Singleton
class CommunitiesFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private val collection = firestore.collection(COLLECTION)

    private val communityAdmCollection = firestore.collection(COLLECTION_COMMUNITY_ADM)

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

    private fun com.google.firebase.firestore.DocumentSnapshot.toCommunityOrNull(): Community? {
        val data = data ?: return null
        val code = (data[FIELD_ID_COMMUNITY] as? String) ?: id
        val name = (data[FIELD_NAME] as? String) ?: return null
        return Community(
            id = code,
            code = code,
            name = name,
            description = data[FIELD_DESCRIPTION] as? String,
            themeIdRaw = (data[FIELD_THEME_ID] as? Long)?.toInt() ?: 0,
            imageURL = data[FIELD_IMAGE] as? String,
            thumbnailURL = data[FIELD_THUMBNAIL] as? String,
            firebaseImageURL = data[FIELD_FIREBASE_IMAGE_URL] as? String,
            firebaseThumbnailURL = data[FIELD_FIREBASE_THUMBNAIL_URL] as? String,
            isCreator = data[FIELD_IS_CREATOR] as? Boolean ?: false,
            createdByUserId = (data[FIELD_USER_ID] as? String) ?: "",
            createdAt = data[FIELD_CREATED_DATE] as? String,
        )
    }

    private companion object {
        const val COLLECTION = "Communities"
        const val COLLECTION_COMMUNITY_ADM = "CommunityAdm"
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
    }
}
