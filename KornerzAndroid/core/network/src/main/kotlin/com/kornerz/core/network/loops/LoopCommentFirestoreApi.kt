package com.kornerz.core.network.loops

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kornerz.core.model.LoopComment
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.toKotlinInstant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore reads and writes for loop comments. Mirrors the iOS
 * `LoopCommentRepository`
 * (`kornerz/Kornerz/Backend/Repositories/LoopCommentRepository.swift`).
 *
 * Key iOS-equivalence rules:
 *   - Collection name is **`"LoopComments"`** (PascalCase, plural).
 *   - Top-level comments: `whereField("loopId", ==, loopId)
 *     .whereField("parentId", ==, null).whereField("active", ==, true)
 *     .order(by: "creationDate", descending: true)`.
 *   - Replies: `whereField("loopId", ==, loopId)
 *     .whereField("parentId", ==, commentId).whereField("active", ==, true)
 *     .order(by: "creationDate", descending: true)`.
 *   - `createdDate` (no `ion`) is the iOS field name for some
 *     queries; `creationDate` is used for the parentId / replies
 *     branches.
 */
@Singleton
class LoopCommentFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private val collection get() = firestore.collection(COLLECTION_LOOP_COMMENTS)

    /**
     * Top-level comments for a loop, `creationDate` desc — iOS
     * `fetchComments(for: loopId)` (parentId == null branch).
     *
     * The stream is **live**: it is backed by a Firestore snapshot
     * listener, so a comment written anywhere — the create-comment
     * sheet, another device — lands in the list without a manual
     * refresh. This is the Android mirror of iOS's
     * `.refreshLoopDetail` notification, which iOS posts after a
     * successful create and which `LoopDetailViewModel` observes to
     * re-fetch its list.
     */
    fun observeComments(loopId: String): Flow<Result<List<LoopComment>>> = flow {
        if (loopId.isBlank()) {
            emit(Result.failure(IllegalArgumentException("loopId must not be blank")))
            return@flow
        }
        emitAll(
            observeQuery(
                collection
                    .whereEqualTo(FIELD_LOOP_ID, loopId)
                    .whereEqualTo(FIELD_PARENT_ID, null)
                    .whereEqualTo(FIELD_ACTIVE, true)
                    .orderBy(FIELD_CREATION_DATE, Query.Direction.DESCENDING),
            ),
        )
    }

    /**
     * Replies for a top-level comment, `creationDate` desc — iOS
     * `fetchReplies(for: loopId, commentId:)`.
     *
     * Live, like [observeComments] — a reply posted from the
     * create-comment sheet appears in the thread as soon as the write
     * lands, without the thread having to re-read anything.
     */
    fun observeReplies(
        loopId: String,
        parentCommentId: String,
    ): Flow<Result<List<LoopComment>>> = flow {
        if (loopId.isBlank() || parentCommentId.isBlank()) {
            emit(Result.failure(IllegalArgumentException("loopId and parentCommentId must not be blank")))
            return@flow
        }
        emitAll(
            observeQuery(
                collection
                    .whereEqualTo(FIELD_LOOP_ID, loopId)
                    .whereEqualTo(FIELD_PARENT_ID, parentCommentId)
                    .whereEqualTo(FIELD_ACTIVE, true)
                    .orderBy(FIELD_CREATION_DATE, Query.Direction.DESCENDING),
            ),
        )
    }

    /**
     * A single comment, live — the parent comment the reply thread
     * pins above its list.
     *
     * The stream re-emits whenever the document changes, so the
     * parent's `repliesCount` follows the replies posted under it
     * (the backend's Cloud Function maintains that counter).
     */
    fun observeComment(commentId: String): Flow<Result<LoopComment?>> = flow {
        if (commentId.isBlank()) {
            emit(Result.failure(IllegalArgumentException("commentId must not be blank")))
            return@flow
        }
        emitAll(observeDocument(collection.document(commentId)))
    }

    /**
     * Backs a comment query with a **Firestore snapshot listener**.
     *
     * Every snapshot is emitted as a `Result.success`; the listener's
     * own failure is emitted as a `Result.failure` and ends the
     * stream, because a Firestore listener does not recover from an
     * error. The listener is detached when the collector goes away
     * ([awaitClose]), so it lives exactly as long as the screen that
     * collects it.
     */
    private fun observeQuery(query: Query): Flow<Result<List<LoopComment>>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "observeQuery: ${error.message}")
                trySend(Result.failure(error))
                close()
                return@addSnapshotListener
            }
            val comments = snapshot?.documents?.mapNotNull { it.data?.toComment(it.id) }.orEmpty()
            trySend(Result.success(comments))
        }
        awaitClose { registration.remove() }
    }

    /** The document-level twin of [observeQuery] — one comment. */
    private fun observeDocument(document: DocumentReference): Flow<Result<LoopComment?>> = callbackFlow {
        val registration = document.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "observeDocument: ${error.message}")
                trySend(Result.failure(error))
                close()
                return@addSnapshotListener
            }
            val comment = snapshot?.let { it.data?.toComment(it.id) }
            trySend(Result.success(comment))
        }
        awaitClose { registration.remove() }
    }

    /** Creates a new top-level comment or a reply. Mirrors iOS
     *  `createComment` (top-level + reply branches). The iOS code
     *  does NOT bump a `commentsCount` counter — `LoopRepository.fetchLoopById`
     *  re-reads the `commentsCount` field from the `Loop` document
     *  which iOS updates via Cloud Functions (out of scope for the
     *  Android port — the count field is read-only here). */
    suspend fun createComment(
        loopId: String,
        parentId: String?,
        text: String,
        userId: String,
    ): Result<LoopComment> = runCatching {
        if (loopId.isBlank()) error("loopId must not be blank")
        if (text.isBlank()) error("text must not be blank")
        val newDoc = collection.document()
        val now = Timestamp.now()
        val payload = hashMapOf<String, Any?>(
            FIELD_LOOP_ID to loopId,
            FIELD_PARENT_ID to parentId,
            FIELD_USER_ID to userId,
            FIELD_ACTIVE to true,
            FIELD_COMMENT to text,
            FIELD_CREATION_DATE to now,
            FIELD_REPLIES_COUNT to 0,
            FIELD_LIKES_COUNT to 0,
        )
        newDoc.set(payload).await()
        LoopComment(
            id = newDoc.id,
            loopId = loopId,
            parentId = parentId,
            userId = userId,
            active = true,
            comment = text,
            creationDate = now.toInstant().toKotlinInstant(),
            repliesCount = 0,
            likesCount = 0,
        )
    }.onFailure { Log.w(TAG, "createComment: ${it.message}") }

    /** Deletes a comment — mirrors iOS `deleteComment`. */
    suspend fun deleteComment(commentId: String): Result<Unit> = runCatching {
        if (commentId.isBlank()) error("commentId must not be blank")
        collection.document(commentId).delete().await()
        Unit
    }.onFailure { Log.w(TAG, "deleteComment: ${it.message}") }

    /** Fetches a single comment by id — used by `ReplyViewModel`. */
    suspend fun fetchCommentById(commentId: String): Result<LoopComment?> = runCatching {
        if (commentId.isBlank()) return@runCatching null
        val snap = collection.document(commentId).get().await()
        snap.data?.toComment(commentId)
    }.onFailure { Log.w(TAG, "fetchCommentById: ${it.message}") }

    private fun Map<String, Any?>.toComment(id: String): LoopComment? = LoopComment(
        id = id,
        loopId = this[FIELD_LOOP_ID] as? String,
        parentId = this[FIELD_PARENT_ID] as? String,
        userId = this[FIELD_USER_ID] as? String,
        active = this[FIELD_ACTIVE] as? Boolean,
        comment = this[FIELD_COMMENT] as? String,
        creationDate = (this[FIELD_CREATION_DATE] as? Timestamp)
            ?.toInstant()?.toKotlinInstant(),
        repliesCount = (this[FIELD_REPLIES_COUNT] as? Long)?.toInt(),
        likesCount = (this[FIELD_LIKES_COUNT] as? Long)?.toInt(),
    )

    companion object {
        private const val TAG = "LoopCommentFirestoreApi"

        // iOS uses `db.collection("LoopComments")`.
        const val COLLECTION_LOOP_COMMENTS = "LoopComments"

        const val FIELD_LOOP_ID = "loopId"
        const val FIELD_PARENT_ID = "parentId"
        const val FIELD_USER_ID = "userId"
        const val FIELD_ACTIVE = "active"
        const val FIELD_COMMENT = "comment"
        const val FIELD_CREATION_DATE = "creationDate"
        const val FIELD_REPLIES_COUNT = "repliesCount"
        const val FIELD_LIKES_COUNT = "likesCount"
    }
}
