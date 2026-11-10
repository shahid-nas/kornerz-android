package com.kornerz.core.network.community

import android.util.Log
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.kornerz.core.model.Community
import com.kornerz.core.model.FollowMember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class CommunitySnippet(
    val id: String,
    val communityName: String,
)

@Singleton
class CommunitiesRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private val collection = firestore.collection(FIELD_COMMUNITY_COLLECTION)

    /**
     * The live count of communities the signed-in user administers.
     *
     * Lives here — in the data layer — rather than in the Community
     * feature, because the Profile tab renders the count and a feature
     * module may not depend on another feature module
     * (`docs/architecture.md` §2.5).
     */
    private val adminCount = MutableStateFlow(0)

    /**
     * A live count of the communities the user is an admin of.
     *
     * The flow re-emits every time [refreshAdminCount] is called (e.g.
     * after a community is created).
     */
    fun fetchUserCommunitiesCount(userCode: String): Flow<Int> = adminCount.asStateFlow()

    /**
     * Re-reads the admin count and emits the new value. Mirrors iOS
     * `AdminCommunitiesRepository.fetchUserCommunitiesCount` (line
     * 140) — queries `CommunityAdm.whereEqualTo("userId", userId)` via
     * Firestore's server-side aggregation
     * (`count().get(AggregateSource.SERVER)`), so only the integer is
     * transferred.
     *
     * **Never throws.** The count is read from a `viewModelScope`
     * coroutine (see `ProfileViewModel.observeAdminCommunitiesCount`),
     * and an unhandled `FirebaseFirestoreException` there — an offline
     * emulator, a dropped connection, a permission error — propagates
     * out of the scope and kills the process. The failure is therefore
     * captured: the last known count is kept and the error is returned
     * to the caller.
     */
    suspend fun refreshAdminCount(userCode: String): Result<Int> {
        if (userCode.isBlank()) {
            adminCount.value = 0
            return Result.success(0)
        }
        return try {
            val count = firestore.collection(FIELD_COMMUNITY_ADM_COLLECTION)
                .whereEqualTo(FIELD_ADMIN_USER_ID, userCode)
                .count()
                .get(AggregateSource.SERVER)
                .await()
                .count
                .toInt()
            adminCount.value = count
            Result.success(count)
        } catch (e: CancellationException) {
            // Never swallow cancellation — the caller may be tearing the
            // scope down while the request is in flight.
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "refreshAdminCount($userCode): ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchCommunityNamesByIds(communityIds: List<String>): Map<String, String> {
        if (communityIds.isEmpty()) return emptyMap()
        val snapshot = collection
            .whereIn(FIELD_COMMUNITY_ID, communityIds)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val id = data[FIELD_COMMUNITY_ID] as? String ?: return@mapNotNull null
            val name = (data[FIELD_COMMUNITY_NAME] as? String)
                ?: (data[FIELD_COMMUNITY_NAME_EN] as? String)
                ?: return@mapNotNull null
            id to name
        }.toMap()
    }

    /**
     * Fetches the communities the given fellows follow.
     *
     * [Community.id] carries the Firestore **document id** and
     * [Community.communityId] the public `communityId` field — iOS
     * `CommunitiesRepository.fetchFollowingCommunities` decodes the
     * document into `Community` (whose `id` is the `@DocumentID`) and
     * keeps the `communityId` field alongside it.
     *
     * The document id is the value the Korner detail route
     * (`kornerz/{kornerId}` → `Communities/{documentId}`) needs, and it
     * is URL-safe; the `communityId` field is not interchangeable with
     * it.
     */
    suspend fun fetchFollowingCommunities(fellows: List<FollowMember>): List<Community> {
        if (fellows.isEmpty()) return emptyList()
        val communityNames = fellows.mapNotNull { it.communityFollowed?.takeIf { n -> n.isNotBlank() } }
        if (communityNames.isEmpty()) return emptyList()
        val snapshot = collection
            .whereIn(FIELD_COMMUNITY_NAME, communityNames)
            .whereEqualTo(FIELD_COMMUNITY_APPROVED, true)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val communityId = data[FIELD_COMMUNITY_ID] as? String
            val name = (data[FIELD_COMMUNITY_NAME] as? String) ?: return@mapNotNull null
            Community(
                id = doc.id,
                code = doc.id,
                communityId = communityId,
                name = name,
                firebaseImageURL = data[FIELD_FIREBASE_IMAGE_URL] as? String,
            )
        }
    }

    /**
     * Fetches a single community by [name]. Returns `null` when the
     * community is not found. Used by `NotificationsViewModel` to
     * resolve the deeplink target (P6, task 6.7).
     */
    suspend fun fetchByName(name: String): Community? {
        if (name.isBlank()) return null
        val snapshot = collection
            .whereEqualTo(FIELD_COMMUNITY_NAME, name)
            .whereEqualTo(FIELD_COMMUNITY_APPROVED, true)
            .get()
            .await()
        val doc = snapshot.documents.firstOrNull() ?: return null
        val data = doc.data ?: return null
        val communityId = data[FIELD_COMMUNITY_ID] as? String
        val id = communityId ?: doc.id
        val resolvedName = (data[FIELD_COMMUNITY_NAME] as? String) ?: name
        return Community(
            id = id,
            code = id,
            communityId = communityId,
            name = resolvedName,
            firebaseImageURL = data[FIELD_FIREBASE_IMAGE_URL] as? String,
        )
    }

    /**
     * Fetches a single community by its **`communityId` field**.
     * Returns `null` when the community is not found.
     *
     * The lookup is by field, not by Firestore document id: iOS
     * resolves a community by the field
     * (`CommunitiesRepository.swift:241`, `APICommunity.swift:475`)
     * because the field and the document id are **different** values on
     * the same document. The Android port used to look the document up
     * by its id, which misses for every community whose `communityId`
     * field differs from its document id — the "Community not found"
     * alert the P6 notification flow showed.
     *
     * What [Community.id] and [Community.code] mean is **unchanged**:
     * both still carry the `communityId` field, falling back to the
     * document id when the field is absent.
     */
    suspend fun fetchById(id: String): Community? {
        if (id.isBlank()) return null
        val snapshot = collection
            .whereEqualTo(FIELD_COMMUNITY_ID, id)
            .get()
            .await()
        val doc = snapshot.documents.firstOrNull() ?: return null
        val data = doc.data ?: return null
        val communityId = data[FIELD_COMMUNITY_ID] as? String
        val resolvedId = communityId ?: doc.id
        val name = (data[FIELD_COMMUNITY_NAME] as? String) ?: return null
        return Community(
            id = resolvedId,
            code = resolvedId,
            communityId = communityId,
            name = name,
            firebaseImageURL = data[FIELD_FIREBASE_IMAGE_URL] as? String,
        )
    }

    /**
     * Fetches a single community by its **`communityId` field**, keeping
     * the Firestore **document id** in [Community.id].
     *
     * [fetchById] resolves the same document but overwrites `Community.id`
     * with the `communityId` field, which is convenient for the callers
     * that address a community by its public id. The loop detail screen
     * needs the opposite: it navigates to `kornerz/{kornerId}`, and that
     * route reads `Communities/{documentId}` — so it needs the document
     * id, not the field.
     *
     * Returns `null` when no community carries that `communityId`.
     */
    suspend fun fetchByCommunityId(id: String): Community? {
        if (id.isBlank()) return null
        val snapshot = collection
            .whereIn(FIELD_COMMUNITY_ID, listOf(id))
            .get()
            .await()
        val doc = snapshot.documents.firstOrNull() ?: return null
        val data = doc.data ?: return null
        val communityId = data[FIELD_COMMUNITY_ID] as? String
        val name = (data[FIELD_COMMUNITY_NAME] as? String) ?: return null
        return Community(
            id = doc.id,
            code = doc.id,
            communityId = communityId,
            name = name,
            firebaseImageURL = data[FIELD_FIREBASE_IMAGE_URL] as? String,
        )
    }

    /**
     * Translates a community's public `communityId` field into the
     * Firestore **document id** of the same community.
     *
     * The Korner detail route is addressed by document id
     * (`KornerFirestoreApi.fetchDetail` does
     * `collection("Communities").document(kornerId)`), while a deep
     * link carries the `communityId` field — so the deep-link resolver
     * needs this one lookup to bridge the two.
     *
     * Returns `null` when no community carries that `communityId`.
     */
    suspend fun fetchDocumentIdByCommunityId(communityId: String): String? {
        if (communityId.isBlank()) return null
        val snapshot = collection
            .whereEqualTo(FIELD_COMMUNITY_ID, communityId)
            .get()
            .await()
        val doc = snapshot.documents.firstOrNull() ?: return null
        return doc.id.takeIf { it.isNotBlank() }
    }

    /**
     * Finds the community that owns a forum.
     *
     * A forum (a post inside a community) lives in the `Loop` collection
     * under a 20-character code — iOS `LoopRepository.createLoop`
     * (`kornerz/Kornerz/Backend/Repositories/LoopRepository.swift:282-294`)
     * and the Android create-forum wizard both write it there. The
     * document's `communityId` field holds the community's **public**
     * `communityId`, not its Firestore document id.
     *
     * The lookup exists for the deep-link resolver: a comment
     * notification names the forum, so the forum is what has to be
     * resolved to a community before the community can be resolved to a
     * Korner detail.
     *
     * Returns `null` when no forum carries that id.
     */
    suspend fun fetchForumCommunityId(forumId: String): String? {
        if (forumId.isBlank()) return null
        return runCatching {
            val snapshot = firestore.collection(FIELD_LOOP_COLLECTION)
                .document(forumId)
                .get()
                .await()
            snapshot.getString(FIELD_COMMUNITY_ID)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "CommunitiesRepository"
        const val FIELD_COMMUNITY_COLLECTION = "Communities"
        const val FIELD_COMMUNITY_ADM_COLLECTION = "CommunityAdm"
        const val FIELD_LOOP_COLLECTION = "Loop"
        const val FIELD_COMMUNITY_ID = "communityId"
        const val FIELD_COMMUNITY_NAME = "communityName"
        const val FIELD_COMMUNITY_NAME_EN = "communityNameEn"
        const val FIELD_COMMUNITY_APPROVED = "communityApproved"
        const val FIELD_FIREBASE_IMAGE_URL = "firebaseImageUrl"
        const val FIELD_ADMIN_USER_ID = "userId"
    }
}
