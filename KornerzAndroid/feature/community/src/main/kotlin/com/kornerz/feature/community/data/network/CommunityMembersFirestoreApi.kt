package com.kornerz.feature.community.data.network

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore API for the `CommunityMembers/{id}` collection. Used by
 * the `community/admin/list` screen to count members per community
 * (mirrors iOS `CommunitiesRepository.fetchMemberCount`).
 */
@Singleton
class CommunityMembersFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private val collection = firestore.collection(COLLECTION)

    /**
     * Returns the live count of `CommunityMembers` documents where
     * `communityId == [communityCode]`. On any failure returns 0
     * (the iOS `AdminCommunityListScreen` line 60 behavior — single
     * row failures don't surface to the user).
     */
    suspend fun fetchCount(communityCode: String): Int {
        if (communityCode.isBlank()) return 0
        return runCatching {
            val snapshot = collection
                .whereEqualTo(FIELD_COMMUNITY_ID, communityCode)
                .get()
                .await()
            snapshot.size()
        }.getOrDefault(0)
    }

    private companion object {
        const val COLLECTION = "CommunityMembers"
        const val FIELD_COMMUNITY_ID = "communityId"
    }
}
