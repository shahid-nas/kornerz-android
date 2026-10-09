package com.kornerz.core.network.community

import com.google.firebase.firestore.FirebaseFirestore
import com.kornerz.core.model.Community
import com.kornerz.core.model.FollowMember
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
            val id = (data[FIELD_COMMUNITY_ID] as? String) ?: doc.id
            val name = (data[FIELD_COMMUNITY_NAME] as? String) ?: return@mapNotNull null
            Community(
                id = id,
                name = name,
                coverUrl = data[FIELD_FIREBASE_IMAGE_URL] as? String,
                themeColor = null,
                isMember = true,
            )
        }
    }

    private companion object {
        const val FIELD_COMMUNITY_COLLECTION = "Communities"
        const val FIELD_COMMUNITY_ID = "communityId"
        const val FIELD_COMMUNITY_NAME = "communityName"
        const val FIELD_COMMUNITY_NAME_EN = "communityNameEn"
        const val FIELD_COMMUNITY_APPROVED = "communityApproved"
        const val FIELD_FIREBASE_IMAGE_URL = "firebaseImageUrl"
    }
}
