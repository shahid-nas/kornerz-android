package com.kornerz.core.network.home

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.datetime.toKotlinInstant
import com.google.firebase.firestore.Query
import com.kornerz.core.model.Nook
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore reads for the Home tab. 1:1 mirrors the iOS reads in
 * `kornerz/Kornerz/HomeVC/AppHomeVC/RegisterToAppCell/AppHomeViewModel.swift`
 * and `RecentArticlesViewModel.swift`. Each suspend method returns a
 * `Result<T>` so the caller (a repository) can decide how to surface
 * errors (architecture §2.3 — no exceptions thrown past the data layer).
 */
@Singleton
class HomeFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    /**
     * Fetches upcoming nooks within the next 7 days, filtered to those
     * hosted by [communityIds]. Returns an empty list on any failure
     * (missing index, empty collection, network error) so the section
     * hides silently per spec §9.6 instead of showing an error state.
     */
    suspend fun fetchUpcomingNooks(communityIds: List<String>): Result<List<Nook>> {
        if (communityIds.isEmpty()) return Result.success(emptyList())
        return runCatching {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val week = Instant.fromEpochMilliseconds(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)
            firestore.collection("events")
                .whereIn("communityId", communityIds.take(30))
                .whereGreaterThanOrEqualTo("startDate", Timestamp(now.toEpochMilliseconds() / 1000, 0))
                .whereLessThanOrEqualTo("startDate", Timestamp(week.toEpochMilliseconds() / 1000, 0))
                .orderBy("startDate", Query.Direction.ASCENDING)
                .limit(20)
                .get()
                .await()
                .documents
                .mapNotNull { it.data?.toNook(it.id) }
        }.recover {
            Log.w(TAG, "fetchUpcomingNooks fallback: ${it.message}")
            emptyList()
        }
    }

    private fun Map<String, Any?>.toNook(id: String): Nook = Nook(
        id = id,
        communityId = (get("communityId") as? String) ?: "",
        title = (get("title") as? String) ?: "",
        startDate = (get("startDate") as? Timestamp)?.toInstant()?.toKotlinInstant() ?: Instant.fromEpochMilliseconds(0L),
        isLive = (get("isLive") as? Boolean) ?: false,
    )

    companion object {
        private const val TAG = "HomeFirestoreApi"
    }
}
