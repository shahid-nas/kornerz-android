package com.kornerz.core.network.loops

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.kornerz.core.model.LoopLike
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore reads and writes for loop likes. Mirrors the iOS
 * `LoopLikeRepository`
 * (`kornerz/Kornerz/Backend/Repositories/LoopLikeRepository.swift`).
 *
 * Key iOS-equivalence rule: the storage shape is a top-level
 * **`"LoopLikes"`** collection (NOT a `users/{userId}/likedLoops`
 * subcollection). Each like is a single document with `userId`,
 * `loopId`, and `creationDate` fields. `checkIfUserLiked` /
 * `unlikeLoop` query the collection by `(userId, loopId)`.
 */
@Singleton
class LoopLikeFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private val collection get() = firestore.collection(COLLECTION_LOOP_LIKES)

    suspend fun likeLoop(userId: String, loopId: String): Result<Unit> = runCatching {
        if (userId.isBlank() || loopId.isBlank()) error("userId and loopId must not be blank")
        val now = Timestamp.now()
        val docRef = collection.document()
        val payload = hashMapOf<String, Any?>(
            FIELD_USER_ID to userId,
            FIELD_LOOP_ID to loopId,
            FIELD_CREATION_DATE to now,
        )
        docRef.set(payload).await()
        // Bump the parent Loop's `likesCount` atomically (mirrors the
        // iOS `LoopRepository.setLikesCount(for:isLiked:)`).
        firestore.collection(COLLECTION_LOOPS)
            .document(loopId)
            .update(FIELD_LIKES_COUNT, FieldValue.increment(1))
            .await()
        Unit
    }.onFailure { Log.w(TAG, "likeLoop: ${it.message}") }

    suspend fun unlikeLoop(userId: String, loopId: String): Result<Unit> = runCatching {
        if (userId.isBlank() || loopId.isBlank()) error("userId and loopId must not be blank")
        val snap = collection
            .whereEqualTo(FIELD_USER_ID, userId)
            .whereEqualTo(FIELD_LOOP_ID, loopId)
            .get()
            .await()
        // Delete all matches (iOS deletes the first one; we delete all
        // for safety in case duplicates were ever written).
        for (doc in snap.documents) doc.reference.delete().await()
        firestore.collection(COLLECTION_LOOPS)
            .document(loopId)
            .update(FIELD_LIKES_COUNT, FieldValue.increment(-1))
            .await()
        Unit
    }.onFailure { Log.w(TAG, "unlikeLoop: ${it.message}") }

    suspend fun checkIfUserLiked(userId: String, loopId: String): Result<Boolean> = runCatching {
        if (userId.isBlank() || loopId.isBlank()) return@runCatching false
        val snap = collection
            .whereEqualTo(FIELD_USER_ID, userId)
            .whereEqualTo(FIELD_LOOP_ID, loopId)
            .get()
            .await()
        !snap.documents.isEmpty()
    }.onFailure { Log.w(TAG, "checkIfUserLiked: ${it.message}") }

    /** Fetches all likes for a loop (iOS `fetchLikesForLoop`). */
    suspend fun fetchLikesForLoop(loopId: String): Result<List<LoopLike>> = runCatching {
        if (loopId.isBlank()) return@runCatching emptyList()
        val snap = collection
            .whereEqualTo(FIELD_LOOP_ID, loopId)
            .get()
            .await()
        snap.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val userId = data[FIELD_USER_ID] as? String ?: return@mapNotNull null
            val loopIdValue = data[FIELD_LOOP_ID] as? String ?: return@mapNotNull null
            LoopLike(
                id = doc.id,
                userId = userId,
                loopId = loopIdValue,
                creationDate = (data[FIELD_CREATION_DATE] as? Timestamp)
                    ?.toDate()?.toInstant()?.toKotlinInstant(),
            )
        }
    }.onFailure { Log.w(TAG, "fetchLikesForLoop: ${it.message}") }

    companion object {
        private const val TAG = "LoopLikeFirestoreApi"

        // iOS uses `db.collection("LoopLikes")` (top-level).
        const val COLLECTION_LOOP_LIKES = "LoopLikes"
        const val COLLECTION_LOOPS = "Loop"
        const val FIELD_USER_ID = "userId"
        const val FIELD_LOOP_ID = "loopId"
        const val FIELD_CREATION_DATE = "creationDate"
        const val FIELD_LIKES_COUNT = "likesCount"
    }
}
