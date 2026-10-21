package com.kornerz.feature.community.data.network

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore API for the `Nooks/{id}` collection. Used by the
 * `community/admin/list` screen to count nooks per community
 * (mirrors iOS `CommunitiesRepository.fetchNookCount`).
 */
@Singleton
class NooksFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private val collection = firestore.collection(COLLECTION)

    /**
     * Returns the live count of `Nooks` documents where
     * `communityId == [communityCode]`. On any failure returns 0
     * (mirrors iOS row-failure handling).
     */
    suspend fun fetchCountForCommunity(communityCode: String): Int {
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
        const val COLLECTION = "Nooks"
        const val FIELD_COMMUNITY_ID = "communityId"
    }
}
