package com.kornerz.core.network.loops

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.kornerz.core.model.CommentLike
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.toKotlinInstant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore reads and writes for comment likes. Mirrors the iOS
 * `CommentLikeRepository`
 * (`kornerz/Kornerz/Backend/Repositories/CommentLikeRepository.swift`).
 *
 * Key iOS-equivalence rule: the storage shape is a top-level
 * **`"CommentLikes"`** collection (NOT a
 * `loopComments/{commentId}/likes/{userId}` subcollection). Each
 * like is a single document with `userId`, `commentId`, and
 * `creationDate` fields. `checkIfUserLiked` / `unlikeComment` query
 * the collection by `(userId, commentId)`.
 */
@Singleton
class CommentLikeFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private val collection get() = firestore.collection(COLLECTION_COMMENT_LIKES)

    suspend fun likeComment(userId: String, commentId: String): Result<Unit> = runCatching {
        if (userId.isBlank() || commentId.isBlank()) error("userId and commentId must not be blank")
        val now = Timestamp.now()
        val docRef = collection.document()
        val payload = hashMapOf<String, Any?>(
            FIELD_USER_ID to userId,
            FIELD_COMMENT_ID to commentId,
            FIELD_CREATION_DATE to now,
        )
        docRef.set(payload).await()
        // Bump the parent LoopComment's `likesCount` atomically
        // (mirrors the iOS `LoopCommentRepository.setLikesCount`).
        firestore.collection(COLLECTION_LOOP_COMMENTS)
            .document(commentId)
            .update(FIELD_LIKES_COUNT, FieldValue.increment(1))
            .await()
        Unit
    }.onFailure { Log.w(TAG, "likeComment: ${it.message}") }

    suspend fun unlikeComment(userId: String, commentId: String): Result<Unit> = runCatching {
        if (userId.isBlank() || commentId.isBlank()) error("userId and commentId must not be blank")
        val snap = collection
            .whereEqualTo(FIELD_USER_ID, userId)
            .whereEqualTo(FIELD_COMMENT_ID, commentId)
            .get()
            .await()
        for (doc in snap.documents) doc.reference.delete().await()
        firestore.collection(COLLECTION_LOOP_COMMENTS)
            .document(commentId)
            .update(FIELD_LIKES_COUNT, FieldValue.increment(-1))
            .await()
        Unit
    }.onFailure { Log.w(TAG, "unlikeComment: ${it.message}") }

    suspend fun checkIfUserLiked(userId: String, commentId: String): Result<Boolean> = runCatching {
        if (userId.isBlank() || commentId.isBlank()) return@runCatching false
        val snap = collection
            .whereEqualTo(FIELD_USER_ID, userId)
            .whereEqualTo(FIELD_COMMENT_ID, commentId)
            .get()
            .await()
        !snap.documents.isEmpty()
    }.onFailure { Log.w(TAG, "checkIfUserLiked: ${it.message}") }

    suspend fun fetchLikesForComment(commentId: String): Result<List<CommentLike>> = runCatching {
        if (commentId.isBlank()) return@runCatching emptyList()
        val snap = collection
            .whereEqualTo(FIELD_COMMENT_ID, commentId)
            .get()
            .await()
        snap.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val userId = data[FIELD_USER_ID] as? String ?: return@mapNotNull null
            val commentIdValue = data[FIELD_COMMENT_ID] as? String ?: return@mapNotNull null
            CommentLike(
                id = doc.id,
                userId = userId,
                commentId = commentIdValue,
                creationDate = (data[FIELD_CREATION_DATE] as? Timestamp)
                    ?.toInstant()?.toKotlinInstant(),
            )
        }
    }.onFailure { Log.w(TAG, "fetchLikesForComment: ${it.message}") }

    companion object {
        private const val TAG = "CommentLikeFirestoreApi"

        // iOS uses `db.collection("CommentLikes")` (top-level).
        const val COLLECTION_COMMENT_LIKES = "CommentLikes"
        const val COLLECTION_LOOP_COMMENTS = "LoopComments"
        const val FIELD_USER_ID = "userId"
        const val FIELD_COMMENT_ID = "commentId"
        const val FIELD_CREATION_DATE = "creationDate"
        const val FIELD_LIKES_COUNT = "likesCount"
    }
}
