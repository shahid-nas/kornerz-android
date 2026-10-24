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

    /**
     * The **live** like count of every comment in [commentIds], keyed by
     * comment id.
     *
     * The read is batched: one `CommentLikes where commentId in […]`
     * query per chunk of at most [MAX_IN_QUERY_VALUES] ids, counting
     * the documents per comment id. iOS issues **two** queries per
     * comment inside its `DispatchGroup` fan-out, so a 40-comment loop
     * costs 80 reads on iOS and two reads here.
     *
     * The count replaces the denormalized `LoopComments.likesCount` —
     * matching iOS, where `LoopCommentInfo.setLikeCount(_:)` overwrites
     * the stored count with the fetched one.
     */
    suspend fun fetchLikeCounts(commentIds: List<String>): Result<Map<String, Int>> = runCatching {
        if (commentIds.isEmpty()) return@runCatching emptyMap()
        val counts = LinkedHashMap<String, Int>()
        for (chunk in commentIds.distinct().chunked(MAX_IN_QUERY_VALUES)) {
            val snap = collection
                .whereIn(FIELD_COMMENT_ID, chunk)
                .get()
                .await()
            for (doc in snap.documents) {
                val commentId = doc.getString(FIELD_COMMENT_ID) ?: continue
                counts[commentId] = (counts[commentId] ?: 0) + 1
            }
        }
        counts
    }.onFailure { Log.w(TAG, "fetchLikeCounts: ${it.message}") }

    /**
     * The ids of the comments in [commentIds] that [userId] has liked.
     *
     * The read is batched: one
     * `CommentLikes where userId == userId AND commentId in […]` query
     * per chunk of at most [MAX_IN_QUERY_VALUES] ids.
     */
    suspend fun fetchLikedCommentIds(
        userId: String,
        commentIds: List<String>,
    ): Result<Set<String>> = runCatching {
        if (userId.isBlank() || commentIds.isEmpty()) return@runCatching emptySet()
        val liked = LinkedHashSet<String>()
        for (chunk in commentIds.distinct().chunked(MAX_IN_QUERY_VALUES)) {
            val snap = collection
                .whereEqualTo(FIELD_USER_ID, userId)
                .whereIn(FIELD_COMMENT_ID, chunk)
                .get()
                .await()
            for (doc in snap.documents) {
                doc.getString(FIELD_COMMENT_ID)?.let { liked.add(it) }
            }
        }
        liked
    }.onFailure { Log.w(TAG, "fetchLikedCommentIds: ${it.message}") }

    companion object {
        private const val TAG = "CommentLikeFirestoreApi"

        // iOS uses `db.collection("CommentLikes")` (top-level).
        const val COLLECTION_COMMENT_LIKES = "CommentLikes"
        const val COLLECTION_LOOP_COMMENTS = "LoopComments"
        const val FIELD_USER_ID = "userId"
        const val FIELD_COMMENT_ID = "commentId"
        const val FIELD_CREATION_DATE = "creationDate"
        const val FIELD_LIKES_COUNT = "likesCount"

        /**
         * Firestore's cap on the number of values in an `in` clause.
         * The comment ids are chunked into groups of this size.
         */
        const val MAX_IN_QUERY_VALUES = 30
    }
}
