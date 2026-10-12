package com.kornerz.core.network.gamification

import com.google.firebase.firestore.FirebaseFirestore
import com.kornerz.core.model.UserRanking
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gamification read path for the shared tab header. Mirrors iOS
 * `GamificationService.swift`:
 * - `getUserPoints` sums the `points` field on every
 *   `Gamefication/{id}` document with
 *   `gameficationUserId == userCode`,
 *   `gameficationDateHora ∈ [startOfMonth, endOfMonth)`.
 *
 * **Typo note:** the Firestore collection is misspelled as
 * `Gamefication` (missing the `m`) — iOS has the same typo
 * (`db.collection("Gamefication")`, line 27) so the Android port
 * preserves the existing collection name to avoid a migration.
 *
 * Returns `Result.success(0)` when the user is signed out so the
 * header always renders a value.
 */
@Singleton
class GamificationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionRepository: SessionRepository,
) {

    /**
     * Sums the signed-in user's monthly points. Mirrors iOS
     * `GamificationService.getUserPoints` (lines 38–68).
     */
    suspend fun getUserPoints(): Result<Int> = runCatching {
        val user = (sessionRepository.data.first() as? SessionState.Authenticated)?.user
            ?: return@runCatching 0
        val userCode = user.userCode
            ?: return@runCatching 0

        val (startTs, endTs) = monthRange()
        val snapshot = firestore.collection(GAMIFICATION_COLLECTION)
            .whereEqualTo(FIELD_USER_ID, userCode)
            .whereGreaterThan(FIELD_DATE, startTs)
            .whereLessThan(FIELD_DATE, endTs)
            .get()
            .await()
        var total = 0
        for (doc in snapshot.documents) {
            val pts = (doc.getLong(FIELD_POINTS) ?: 0L).toInt()
            total += pts
        }
        total
    }

    /**
     * Returns the current month's leaderboard, ordered by points
     * descending, capped at [count] rows. Mirrors iOS
     * `GamificationService.getRanking(_:)` (lines 106–140). The
     * paginated loop is the same: `start(afterDocument:)` until the
     * page is empty.
     */
    suspend fun getRanking(count: Int): Result<List<UserRanking>> = runCatching {
        val (startTs, endTs) = monthRange()
        val ranking = mutableMapOf<String, UserRanking>()
        var lastSnapshot = null as com.google.firebase.firestore.DocumentSnapshot?
        var pageSize = 0
        do {
            var query = firestore.collection(GAMIFICATION_COLLECTION)
                .whereGreaterThan(FIELD_DATE, startTs)
                .whereLessThan(FIELD_DATE, endTs)
            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }
            val snapshot = query.get().await()
            pageSize = snapshot.documents.size
            for (doc in snapshot.documents) {
                val userCode = doc.getString(FIELD_USER_ID) ?: continue
                val pts = (doc.getLong(FIELD_POINTS) ?: 0L).toInt()
                val existing = ranking[userCode]
                if (existing != null) {
                    ranking[userCode] = existing.copy(points = existing.points + pts)
                } else {
                    ranking[userCode] = UserRanking(
                        userId = userCode,
                        userName = "User",
                        points = pts,
                        medal = UserRanking.Medal.NONE,
                    )
                }
            }
            lastSnapshot = snapshot.documents.lastOrNull()
        } while (pageSize > 0 && lastSnapshot != null)
        val ordered = ranking.values
            .sortedByDescending { it.points }
            .take(count)
        // Fill medals — iOS `fillMadalInformation` (lines 154–166).
        ordered.mapIndexed { index, ranking ->
            val medal = when (index) {
                0 -> UserRanking.Medal.GOLD
                1 -> UserRanking.Medal.SILVER
                2 -> UserRanking.Medal.BRONZE
                else -> UserRanking.Medal.NONE
            }
            ranking.copy(medal = medal)
        }
    }

    /**
     * Returns the inclusive lower / exclusive upper timestamps for
     * the current calendar month in the device timezone. Mirrors
     * iOS `Date.now.startOfMonth()` / `Date.now.endOfMonth()`.
     */
    private fun monthRange(): Pair<com.google.firebase.Timestamp, com.google.firebase.Timestamp> {
        val now = Calendar.getInstance()
        val start = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.MONTH, now.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val end = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.MONTH, now.get(Calendar.MONTH) + 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return com.google.firebase.Timestamp(start.time) to
            com.google.firebase.Timestamp(end.time)
    }

    private companion object {
        const val GAMIFICATION_COLLECTION = "Gamefication"
        const val FIELD_USER_ID = "gameficationUserId"
        const val FIELD_DATE = "gameficationDateHora"
        const val FIELD_POINTS = "gameficationPoints"
    }
}
