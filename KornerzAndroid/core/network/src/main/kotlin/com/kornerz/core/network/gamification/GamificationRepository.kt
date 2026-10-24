package com.kornerz.core.network.gamification

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.kornerz.core.model.Rank
import com.kornerz.core.model.UserRanking
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gamification read path. Backs the shared tab header's points chip
 * and the Klouders leaderboard. Mirrors iOS `GamificationService.swift`.
 *
 * - `getUserPoints` sums the `points` field on every
 *   `Gamefication/{id}` document with
 *   `gameficationUserId == userCode`,
 *   `gameficationDateHora ∈ [startOfMonth, endOfMonth)`.
 * - `getRanking` aggregates the same collection per user, sorts by
 *   points descending, resolves each user's nickname with one batched
 *   `Users` query, and assigns the medal tier by final position.
 *
 * **Typo note:** the Firestore collection is misspelled as
 * `Gamefication` (missing the `m`) — iOS has the same typo
 * (`db.collection("Gamefication")`, line 27) so the Android port
 * preserves the existing collection name to avoid a migration.
 *
 * **Field-name note:** the field names below are the iOS `CodingKeys`
 * of `Models/Gamification.swift` (lines 19–26) and are the same
 * strings the [com.kornerz.core.model.Gamification] model declares.
 * They are duplicated as constants because the read path works on raw
 * Firestore documents — a Firestore `Timestamp` is not the
 * `kotlinx-datetime` `Instant` the model carries.
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
     *
     * Returns `Result.success(0)` **without querying Firestore** when
     * the session is anonymous or the user has no `codeUser`.
     */
    suspend fun getUserPoints(): Result<Int> = runCatching {
        val user = (sessionRepository.data.first() as? SessionState.Authenticated)?.user
            ?: return@runCatching 0
        val userCode = user.userCode?.takeIf { it.isNotBlank() }
            ?: return@runCatching 0

        val (startTs, endTs) = monthRange()
        val snapshot = firestore.collection(GAMIFICATION_COLLECTION)
            .whereEqualTo(FIELD_USER_ID, userCode)
            .whereGreaterThanOrEqualTo(FIELD_DATE, startTs)
            .whereLessThan(FIELD_DATE, endTs)
            .get()
            .await()
        snapshot.documents.sumOf { it.readPoints() }
    }

    /**
     * Awards Klouders points by writing one `Gamefication` document.
     * Mirrors iOS `GamificationService.addPoints(action:points:referenceId:)`
     * (`kornerz/Kornerz/FirestoreService/GamificationService.swift`).
     *
     * The document carries the iOS field names verbatim:
     *
     * | Kotlin parameter | Firestore field |
     * |---|---|
     * | `action` | `gameficationAction` |
     * | `points` | `gameficationQtdPts` |
     * | now | `gameficationDateHora` |
     * | the signed-in user's `codeUser` | `gameficationUserId` |
     * | `referenceId` | `gameficationReferenceId` |
     *
     * The write is a **no-op** for an anonymous session (or a user
     * without a `codeUser`): it returns `Result.success(Unit)` and
     * creates no document, so an anonymous session never writes a
     * `Gamefication` document with an empty user id.
     *
     * The document it writes is readable by the existing read path —
     * [getUserPoints] and [getRanking] both read `gameficationQtdPts`
     * and aggregate per `gameficationUserId`.
     *
     * Returns `Result.failure` rather than throwing when the write
     * fails.
     */
    suspend fun addPoints(
        action: String,
        points: Int,
        referenceId: String,
    ): Result<Unit> = runCatching {
        val user = (sessionRepository.data.first() as? SessionState.Authenticated)?.user
        val userCode = user?.userCode?.takeIf { it.isNotBlank() }
            ?: return@runCatching Unit

        val payload = hashMapOf<String, Any?>(
            FIELD_ACTION to action,
            FIELD_POINTS to points,
            FIELD_DATE to com.google.firebase.Timestamp.now(),
            FIELD_USER_ID to userCode,
            FIELD_REFERENCE_ID to referenceId,
        )
        firestore.collection(GAMIFICATION_COLLECTION)
            .document()
            .set(payload)
            .await()
        Unit
    }

    /**
     * Returns the current month's leaderboard, ordered by points
     * descending, capped at [count] rows. Mirrors iOS
     * `GamificationService.getRanking(_:)` (lines 106–140) plus its
     * `fillUserInformation` (`:142-152`) and `fillMadalInformation`
     * (`:154-166`) steps, which the Android port had skipped.
     *
     * The read is two queries, exactly as iOS's is:
     * 1. the `Gamefication` range query, paginated with
     *    `startAfter(lastDocument)` until a page comes back empty,
     *    aggregating the points per user;
     * 2. one batched `Users.whereIn("codeUser", …)` query that resolves
     *    each row's nickname.
     *
     * **Tie order.** Rows are ordered with `sortedByDescending`, which
     * is a **stable** sort, so rows with equal points keep their
     * first-seen order (the Firestore document iteration order). iOS
     * sorts with `sorted(by: { $0.points > $1.points })`, which is not
     * guaranteed stable, so its tie order is arbitrary and can differ
     * from Android's on the same data. Android's deterministic
     * tie-break is a deliberate, documented divergence — not a parity
     * break — and is recorded in the `ranking-leaderboard` spec so a
     * later pass does not "fix" it back.
     *
     * Returns `Result.failure` when the underlying query fails; the
     * exception is never swallowed and never escapes as a throw.
     */
    suspend fun getRanking(count: Int): Result<List<UserRanking>> = runCatching {
        val (startTs, endTs) = monthRange()
        val totals = LinkedHashMap<String, Int>()
        var lastSnapshot: DocumentSnapshot? = null
        var pageSize: Int
        do {
            var query = firestore.collection(GAMIFICATION_COLLECTION)
                .whereGreaterThanOrEqualTo(FIELD_DATE, startTs)
                .whereLessThan(FIELD_DATE, endTs)
            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }
            val snapshot = query.get().await()
            pageSize = snapshot.documents.size
            for (doc in snapshot.documents) {
                val userCode = doc.getString(FIELD_USER_ID) ?: continue
                totals[userCode] = (totals[userCode] ?: 0) + doc.readPoints()
            }
            lastSnapshot = snapshot.documents.lastOrNull()
        } while (pageSize > 0 && lastSnapshot != null)

        // Stable sort — `sortedByDescending` keeps equal-point rows in
        // their first-seen order. Truncation happens *after* the sort,
        // so the top `count` rows are the highest-scoring ones.
        val ordered = totals.entries
            .sortedByDescending { it.value }
            .take(count)

        val nicknames = fetchNicknames(ordered.map { it.key })

        ordered.mapIndexed { index, entry ->
            UserRanking(
                userId = entry.key,
                userName = nicknames[entry.key] ?: FALLBACK_USER_NAME,
                points = entry.value,
                medal = rankForPosition(index),
            )
        }
    }

    /**
     * Resolves each user's display name with one batched query —
     * `Users.whereIn("codeUser", userIds)` — assigning `User.nickname`
     * to the matching row. Mirrors iOS
     * `GamificationService.fillUserInformation` (`:142-152`).
     *
     * A user id with no matching `Users` document is simply absent
     * from the result, and the caller falls back to the literal
     * `"User"` — the same value iOS uses when it creates the row
     * (`GamificationService.swift:124`).
     */
    private suspend fun fetchNicknames(userIds: List<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()
        val nicknames = mutableMapOf<String, String>()
        // Firestore caps an `in` query at a bounded number of values;
        // the leaderboard's own contract is the top 10, so the normal
        // path is a single query.
        for (chunk in userIds.chunked(MAX_IN_QUERY_VALUES)) {
            val snapshot = firestore.collection(USERS_COLLECTION)
                .whereIn(FIELD_USER_CODE, chunk)
                .get()
                .await()
            for (doc in snapshot.documents) {
                val code = doc.getString(FIELD_USER_CODE) ?: continue
                val nickname = doc.getString(FIELD_USER_NICKNAME) ?: continue
                nicknames[code] = nickname
            }
        }
        return nicknames
    }

    /**
     * The medal tier for a row's **final position** in the sorted
     * leaderboard: gold, silver, bronze for the first three rows, none
     * after. Mirrors iOS `GamificationService.fillMadalInformation`
     * (`:154-166`).
     */
    private fun rankForPosition(index: Int): Rank = when (index) {
        0 -> Rank.GOLD
        1 -> Rank.SILVER
        2 -> Rank.BRONZE
        else -> Rank.NONE
    }

    /**
     * Reads a document's points.
     *
     * `gameficationQtdPts` is the authoritative field — iOS maps
     * `points` to it (`Models/Gamification.swift:23`) and the backend
     * writes it. `gameficationPoints` is read **only** as a fallback
     * for a document written by a different backend version, and only
     * when the authoritative field is absent, so the authoritative
     * spelling always wins when both are present.
     *
     * A missing or unreadable value reads as `0` rather than throwing.
     */
    private fun DocumentSnapshot.readPoints(): Int {
        val value = get(FIELD_POINTS) ?: get(FIELD_POINTS_FALLBACK) ?: return 0
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
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
        const val USERS_COLLECTION = "Users"
        const val FIELD_USER_ID = "gameficationUserId"
        const val FIELD_DATE = "gameficationDateHora"

        /**
         * The authoritative points field — iOS `CodingKeys.points`
         * (`Models/Gamification.swift:23`).
         *
         * Before P10 this constant read `"gameficationPoints"`, a field
         * no server document carries, so every score resolved to `0`
         * and the tab header's points chip has read `0` on every tab
         * since P2.
         */
        const val FIELD_POINTS = "gameficationQtdPts"

        /** Fallback spelling, read only when [FIELD_POINTS] is absent. */
        const val FIELD_POINTS_FALLBACK = "gameficationPoints"

        const val FIELD_USER_CODE = "codeUser"
        const val FIELD_USER_NICKNAME = "nickname"

        /** iOS `CodingKeys.action` (`Models/Gamification.swift`). */
        const val FIELD_ACTION = "gameficationAction"

        /** iOS `CodingKeys.referenceId` (`Models/Gamification.swift`). */
        const val FIELD_REFERENCE_ID = "gameficationReferenceId"

        /** The name a row keeps when its user id does not resolve. */
        const val FALLBACK_USER_NAME = "User"

        /** Firestore's cap on the number of values in an `in` query. */
        const val MAX_IN_QUERY_VALUES = 10
    }
}
