package com.kornerz.core.network.loops

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kornerz.core.model.Loop
import com.kornerz.core.model.LoopsFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.toKotlinInstant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore reads and writes for the loop collection. Mirrors the iOS
 * `LoopRepository` (`kornerz/Kornerz/Backend/Repositories/LoopRepository.swift`)
 * so the same documents surface in both apps.
 *
 * Key iOS-equivalence rules (verified against the iOS source on
 * 2026-10-12):
 *   - Collection name is **`"Loop"`** (capitalized, singular) — not
 *     `"loops"`.
 *   - The default feed is `collection.orderBy("creationDate",
 *     descending).limit(20)` — no `whereEqualTo("active", true)` filter.
 *   - The user-posts feed is
 *     `collection.whereField("publisherId", ==, userId).orderBy("creationDate",
 *     descending).limit(20)`.
 *   - Search uses `whereField("title", >=, query).whereField("title",
 *     <=, query + high-codepoint).orderBy("title")` — range scan on
 *     the raw `title` field (NOT lowercased).
 */
@Singleton
class LoopFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {

    val collection get() = firestore.collection(COLLECTION_LOOPS)

    /**
     * Streams the first page of loops for [filter]. Mirrors the iOS
     * `fetchAllLoops` / `fetchUserLoops` calls. Cursor pagination
     * matches iOS `start(afterDocument:)`.
     *
     * For `YOUR_LOOPS` the query is gated on a signed-in user — when
     * no user is signed in the method emits a `Result.failure` to
     * mirror the iOS `error = .tryAgain` behavior in
     * `LoopsTabViewModel.fetchUserLoops()`.
     */
    fun observeLoops(
        filter: LoopsFilter,
        searchQuery: String,
        cursor: LoopCursor?,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Flow<Result<PagingResult<Loop>>> = flow {
        // Lazy anonymous sign-in: the iOS app assumes a logged-in
        // user (`AppHelper.shared.loggedInUser?.id`) for the "Your
        // Loops" filter. Android's auth flow signs in lazily on
        // certain actions; trigger the same anonymous sign-in here
        // so the user id is populated by the time the user taps the
        // filter. Matches `FirestoreAuthApi.signInFirebaseAnonymously`.
        val currentUserId = ensureSignedIn()?.uid
        if (filter == LoopsFilter.YOUR_LOOPS && currentUserId.isNullOrBlank()) {
            emit(Result.failure(IllegalStateException("Sign in to view your loops")))
            return@flow
        }
        val pageResult = runCatching {
            val trimmed = searchQuery.trim()
            val (q, usedSearch) = if (trimmed.isNotEmpty() && filter == LoopsFilter.ALL) {
                // iOS `searchLoops` range scan on the `title` field.
                collection
                    .whereGreaterThanOrEqualTo(FIELD_TITLE, trimmed)
                    .whereLessThanOrEqualTo(FIELD_TITLE, trimmed + "\uf8ff")
                    .orderBy(FIELD_TITLE) to true
            } else {
                when (filter) {
                    LoopsFilter.ALL -> collection
                        .orderBy(FIELD_CREATION_DATE, Query.Direction.DESCENDING)
                    LoopsFilter.YOUR_LOOPS -> collection
                        .whereEqualTo(FIELD_PUBLISHER_ID, currentUserId)
                        .orderBy(FIELD_CREATION_DATE, Query.Direction.DESCENDING)
                } to false
            }
            var query = q.limit(pageSize.toLong())
            if (cursor?.snapshot != null) query = query.startAfter(cursor.snapshot!!)
            val snap = query.get().await()
            val items = snap.documents.mapNotNull { it.data?.toLoop(it.id) }
            val last = snap.documents.lastOrNull()
            PagingResult(
                items = items,
                nextCursor = if (last != null && items.size >= pageSize) LoopCursor(last) else null,
                hasMore = items.size >= pageSize && !usedSearch,
            )
        }.onFailure { Log.w(TAG, "observeLoops: ${it.message}") }
        emit(pageResult)
    }

    /** One-shot search pagination — mirrors iOS `searchLoops`. */
    suspend fun searchLoops(
        query: String,
        cursor: LoopCursor?,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Result<PagingResult<Loop>> = runCatching {
        val trimmed = query.trim()
        var q: Query = collection
            .whereGreaterThanOrEqualTo(FIELD_TITLE, trimmed)
            .whereLessThanOrEqualTo(FIELD_TITLE, trimmed + "\uf8ff")
            .orderBy(FIELD_TITLE)
            .limit(pageSize.toLong())
        if (cursor?.snapshot != null) q = q.startAfter(cursor.snapshot!!)
        val snap = q.get().await()
        val items = snap.documents.mapNotNull { it.data?.toLoop(it.id) }
        val last = snap.documents.lastOrNull()
        PagingResult(
            items = items,
            nextCursor = if (last != null && items.size >= pageSize) LoopCursor(last) else null,
            hasMore = items.size >= pageSize,
        )
    }.onFailure { Log.w(TAG, "searchLoops: ${it.message}") }

    /** Single-loop read — mirrors iOS `fetchLoopById`. */
    fun fetchLoopDetail(loopId: String): Flow<Result<Loop>> = flow {
        if (loopId.isBlank()) {
            emit(Result.failure(IllegalArgumentException("loopId must not be blank")))
            return@flow
        }
        val result = runCatching {
            val snap = collection.document(loopId).get().await()
            snap.data?.toLoop(loopId) ?: error("Loop $loopId not found")
        }.onFailure { Log.w(TAG, "fetchLoopDetail: ${it.message}") }
        emit(result)
    }

    /** Increments the loop's `viewCounts` — mirrors iOS `incrementViewCount`. */
    suspend fun incrementViewCount(loopId: String): Result<Unit> = runCatching {
        if (loopId.isBlank()) error("loopId must not be blank")
        collection.document(loopId)
            .update(FIELD_VIEW_COUNTS, FieldValue.increment(1))
            .await()
        Unit
    }.onFailure { Log.w(TAG, "incrementViewCount: ${it.message}") }

    /** Fetches the most recent N loops (iOS `fetchAllLoops` with smaller limit). */
    suspend fun fetchRecentLoops(limit: Long = 10L): Result<List<Loop>> = runCatching {
        val snap = collection
            .orderBy(FIELD_CREATION_DATE, Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
        snap.documents.mapNotNull { it.data?.toLoop(it.id) }
    }.recover {
        Log.w(TAG, "fetchRecentLoops fallback: ${it.message}")
        emptyList()
    }

    /** Fetches the loops authored by [userId], newest first — iOS `fetchUserLoops`. */
    fun fetchLoopsByPublisher(
        userId: String,
        cursor: LoopCursor?,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Flow<Result<PagingResult<Loop>>> = flow {
        if (userId.isBlank()) {
            emit(Result.failure(IllegalArgumentException("userId must not be blank")))
            return@flow
        }
        val result = runCatching {
            var q: Query = collection
                .whereEqualTo(FIELD_PUBLISHER_ID, userId)
                .orderBy(FIELD_CREATION_DATE, Query.Direction.DESCENDING)
                .limit(pageSize.toLong())
            if (cursor?.snapshot != null) q = q.startAfter(cursor.snapshot!!)
            val snap = q.get().await()
            val items = snap.documents.mapNotNull { it.data?.toLoop(it.id) }
            val last = snap.documents.lastOrNull()
            PagingResult(
                items = items,
                nextCursor = if (last != null && items.size >= pageSize) LoopCursor(last) else null,
                hasMore = items.size >= pageSize,
            )
        }.onFailure { Log.w(TAG, "fetchLoopsByPublisher: ${it.message}") }
        emit(result)
    }

    private fun currentUserIdOrNull(): String? = auth.currentUser?.uid

    /**
     * Returns the currently signed-in user, signing in anonymously if
     * no user is set yet. Mirrors `FirestoreAuthApi`'s
     * `signInFirebaseAnonymously` lazy sign-in pattern. Returns
     * `null` if sign-in fails (e.g., no network).
     */
    private suspend fun ensureSignedIn(): com.google.firebase.auth.FirebaseUser? {
        auth.currentUser?.let { return it }
        return runCatching {
            val result = auth.signInAnonymously().await()
            result.user
        }.onFailure { Log.w(TAG, "ensureSignedIn: ${it.message}") }
            .getOrNull()
    }

    private fun Map<String, Any?>.toLoop(id: String): Loop? = Loop(
        id = id,
        communityId = this[FIELD_COMMUNITY_ID] as? String,
        publisherId = this[FIELD_PUBLISHER_ID] as? String,
        active = this[FIELD_ACTIVE] as? Boolean,
        title = this[FIELD_TITLE] as? String,
        description = this[FIELD_DESCRIPTION] as? String,
        descriptionHtml = this[FIELD_DESCRIPTION_HTML] as? String,
        imageHash = this[FIELD_IMAGE_HASH] as? String,
        thumbnailHash = this[FIELD_THUMBNAIL_HASH] as? String,
        videoHash = this[FIELD_VIDEO_HASH] as? String,
        firebaseImageUrl = this[FIELD_FIREBASE_IMAGE_URL] as? String,
        firebaseThumbnailUrl = this[FIELD_FIREBASE_THUMBNAIL_URL] as? String,
        firebaseVideoUrl = this[FIELD_FIREBASE_VIDEO_URL] as? String,
        viewCounts = (this[FIELD_VIEW_COUNTS] as? Long)?.toInt(),
        commentsCount = (this[FIELD_COMMENTS_COUNT] as? Long)?.toInt(),
        likesCount = (this[FIELD_LIKES_COUNT] as? Long)?.toInt(),
        theme = this[FIELD_THEME] as? String,
        creationDate = (this[FIELD_CREATION_DATE] as? Timestamp)
            ?.toInstant()?.toKotlinInstant(),
        // P2 backwards-compat aliases.
        thumbnailUrl = (this[FIELD_FIREBASE_THUMBNAIL_URL] as? String)
            ?: (this[FIELD_FIREBASE_IMAGE_URL] as? String),
        color = this[FIELD_THEME] as? String,
        durationSeconds = null,
        createdAt = (this[FIELD_CREATION_DATE] as? Timestamp)
            ?.toInstant()?.toKotlinInstant(),
        publisherName = null,
        publisherAvatarUrl = null,
        communityName = null,
    )

    companion object {
        private const val TAG = "LoopFirestoreApi"

        // iOS uses `db.collection("Loop")` — capitalized, singular.
        const val COLLECTION_LOOPS = "Loop"

        const val FIELD_ACTIVE = "active"
        const val FIELD_COMMUNITY_ID = "communityId"
        const val FIELD_PUBLISHER_ID = "publisherId"
        const val FIELD_TITLE = "title"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_DESCRIPTION_HTML = "descriptionHtml"
        const val FIELD_IMAGE_HASH = "imageHash"
        const val FIELD_THUMBNAIL_HASH = "thumbnailHash"
        const val FIELD_VIDEO_HASH = "videoHash"
        const val FIELD_FIREBASE_IMAGE_URL = "firebaseImageUrl"
        const val FIELD_FIREBASE_THUMBNAIL_URL = "firebaseThumbnailUrl"
        const val FIELD_FIREBASE_VIDEO_URL = "firebaseVideoUrl"
        const val FIELD_VIEW_COUNTS = "viewCounts"
        const val FIELD_COMMENTS_COUNT = "commentsCount"
        const val FIELD_LIKES_COUNT = "likesCount"
        const val FIELD_THEME = "theme"
        const val FIELD_CREATION_DATE = "creationDate"

        const val DEFAULT_PAGE_SIZE = 20
    }
}
