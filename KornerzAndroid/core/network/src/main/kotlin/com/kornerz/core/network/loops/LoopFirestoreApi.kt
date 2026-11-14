package com.kornerz.core.network.loops

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.kornerz.core.model.Loop
import com.kornerz.core.model.LoopsFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.toJavaInstant
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
) {

    val collection get() = firestore.collection(COLLECTION_LOOPS)

    /**
     * Streams the first page of loops for [filter]. Mirrors the iOS
     * `fetchAllLoops` / `fetchUserLoops` calls. Cursor pagination
     * matches iOS `start(afterDocument:)`.
     *
     * For `YOUR_LOOPS` the query is gated on [currentUserCode] — the
     * signed-in user's **`codeUser`** value. iOS queries with
     * `AppHelper.shared.loggedInUser?.id`, and `User.id` is
     * `codeUser ?? userId` (`kornerz/Kornerz/Models/User.swift:57-59`);
     * every loop document stores its author as the `codeUser` string
     * (`AddForumViewModel.swift:220-227`), so `publisherId` holds a
     * **user code**, never a Firebase Auth uid and never the `Users`
     * document id. When no code is available the method emits a
     * `Result.failure` so the ViewModel surfaces the error state
     * instead of a misleading empty list.
     */
    fun observeLoops(
        filter: LoopsFilter,
        searchQuery: String,
        cursor: LoopCursor?,
        currentUserCode: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Flow<Result<PagingResult<Loop>>> = flow {
        // "Your Loops" is gated on a real signed-in user — matches
        // iOS `AppHelper.shared.loggedInUser?.id` in
        // `LoopsTabViewModel.fetchUserLoops()`. The Firebase Auth uid
        // is deliberately NOT used here: the app signs in anonymously,
        // so that uid is a throwaway identity that never matches the
        // `publisherId` of any loop document.
        val publisherId = currentUserCode?.takeIf { it.isNotBlank() }
        if (filter == LoopsFilter.YOUR_LOOPS && publisherId == null) {
            emit(Result.failure(LoopsRequireSignInException()))
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
                        .whereEqualTo(FIELD_PUBLISHER_ID, publisherId)
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

    /**
     * Single-loop read — mirrors iOS `fetchLoopById`.
     *
     * The document is resolved by its **`id` field** first
     * (`whereEqualTo("id", loopId)`), falling back to the Firestore
     * document id when no document carries that field value. iOS's
     * deep-link handler resolves a loop the same way
     * (`APICommunity.swift:606` — `Loop.whereField("id", isEqualTo:
     * loopId)`), and the field and the document key are **different**
     * values on the same backend document, so a payload carrying the
     * `id` field resolves on iOS and 404s on Android when only the
     * document id is read.
     *
     * The document-id fallback keeps every pre-existing caller working:
     * a document that omits the `id` field is still reachable by its
     * key.
     */
    fun fetchLoopDetail(loopId: String): Flow<Result<Loop>> = flow {
        if (loopId.isBlank()) {
            emit(Result.failure(IllegalArgumentException("loopId must not be blank")))
            return@flow
        }
        val result = runCatching {
            val byField = collection
                .whereEqualTo(FIELD_ID, loopId)
                .limit(1)
                .get()
                .await()
            val doc = byField.documents.firstOrNull()
                ?: collection.document(loopId).get().await()
            doc.data?.toLoop(doc.id) ?: error("Loop $loopId not found")
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

    /** Fetches the loops authored by [publisherId], newest first — iOS `fetchUserLoops`. */
    fun fetchLoopsByPublisher(
        publisherId: String,
        cursor: LoopCursor?,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Flow<Result<PagingResult<Loop>>> = flow {
        if (publisherId.isBlank()) {
            emit(Result.failure(IllegalArgumentException("publisherId must not be blank")))
            return@flow
        }
        val result = runCatching {
            var q: Query = collection
                .whereEqualTo(FIELD_PUBLISHER_ID, publisherId)
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

    /**
     * The loops published in a community, newest first. Mirrors iOS
     * `LoopRepository.fetchCommunityLoops(communityId:)` —
     * `whereField("communityId", isEqualTo: communityId)` ordered by
     * `creationDate` descending.
     *
     * The Korner detail screen's "Recently in &lt;community&gt;" section
     * is built from this read (iOS `KornerDetailViewModel.fetchLoops`).
     *
     * The equality filter combined with the `creationDate` ordering
     * needs a Firestore composite index — the same one iOS relies on.
     * A missing index (or any other read failure) degrades to an empty
     * list so the screen still renders its empty state, which is what
     * iOS does when the query fails.
     */
    suspend fun fetchCommunityLoops(
        communityId: String,
        limit: Long = DEFAULT_PAGE_SIZE.toLong(),
    ): Result<List<Loop>> = runCatching {
        if (communityId.isBlank()) return@runCatching emptyList()
        collection
            .whereEqualTo(FIELD_COMMUNITY_ID, communityId)
            .orderBy(FIELD_CREATION_DATE, Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
            .documents
            .mapNotNull { it.data?.toLoop(it.id) }
    }.onFailure { Log.w(TAG, "fetchCommunityLoops: ${it.message}") }
        .recover { emptyList() }

    /**
     * Fetches one page of the loops published in a community, newest
     * first. The page resumes after [cursor] when one is supplied.
     */
    suspend fun fetchCommunityLoopsPage(
        communityId: String,
        cursor: LoopCursor?,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Result<PagingResult<Loop>> = runCatching {
        if (communityId.isBlank()) {
            return@runCatching PagingResult(emptyList(), null, false)
        }
        var query = collection
            .whereEqualTo(FIELD_COMMUNITY_ID, communityId)
            .orderBy(FIELD_CREATION_DATE, Query.Direction.DESCENDING)
            .limit(pageSize.toLong())
        if (cursor?.snapshot != null) query = query.startAfter(cursor.snapshot!!)
        val snapshot = query.get().await()
        val items = snapshot.documents.mapNotNull { it.data?.toLoop(it.id) }
        val last = snapshot.documents.lastOrNull()
        val hasMore = items.size >= pageSize
        PagingResult(
            items = items,
            nextCursor = if (hasMore) LoopCursor(last) else null,
            hasMore = hasMore,
        )
    }.onFailure { Log.w(TAG, "fetchCommunityLoopsPage: ${it.message}") }

    /**
     * Creates a new loop document in the `Loop` collection — mirrors iOS
     * `LoopRepository.createLoop(_:)` (`LoopRepository.swift:282-294`),
     * which writes `collection.document(loop.id).setData(from: loop)`.
     *
     * The document id is [Loop.id] (the 20-char forum/loop code generated
     * by the caller, mirroring iOS `AddForumViewModel.forumId`). All
     * fields (`title`, `description`, `firebaseImageUrl`,
     * `firebaseThumbnailUrl`, `firebaseVideoUrl`, `creationDate` as a
     * Firestore `Timestamp`, etc.) are written so both platforms decode
     * the same document shape.
     */
    suspend fun createLoop(loop: Loop): Result<Unit> = runCatching {
        val loopId = loop.id ?: error("loop id must not be blank")
        require(loopId.isNotBlank()) { "loop id must not be blank" }
        collection.document(loopId).set(loopToMap(loop)).await()
        Unit
    }.onFailure { Log.w(TAG, "createLoop: ${it.message}") }

    private fun Map<String, Any?>.toLoop(id: String): Loop? = Loop(
        id = id,
        communityId = this[FIELD_COMMUNITY_ID] as? String,
        publisherId = this[FIELD_PUBLISHER_ID] as? String,
        active = this[FIELD_ACTIVE] as? Boolean,
        title = this[FIELD_TITLE] as? String,
        description = this[FIELD_DESCRIPTION] as? String,
        descriptionHtml = this[FIELD_DESCRIPTION_HTML] as? String,
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

    /**
     * Converts a [Loop] into the Firestore field map used for writes.
     * Field names mirror the iOS `Loop` CodingKeys so both platforms
     * read/write the same document shape.
     */
    private fun loopToMap(loop: Loop): Map<String, Any?> = mapOf(
        FIELD_ID to loop.id,
        FIELD_COMMUNITY_ID to loop.communityId,
        FIELD_PUBLISHER_ID to loop.publisherId,
        FIELD_ACTIVE to loop.active,
        FIELD_TITLE to loop.title,
        FIELD_DESCRIPTION to loop.description,
        FIELD_DESCRIPTION_HTML to loop.descriptionHtml,
        FIELD_FIREBASE_IMAGE_URL to loop.firebaseImageUrl,
        FIELD_FIREBASE_THUMBNAIL_URL to loop.firebaseThumbnailUrl,
        FIELD_FIREBASE_VIDEO_URL to loop.firebaseVideoUrl,
        FIELD_VIEW_COUNTS to loop.viewCounts,
        FIELD_COMMENTS_COUNT to loop.commentsCount,
        FIELD_LIKES_COUNT to loop.likesCount,
        FIELD_THEME to loop.theme,
        FIELD_CREATION_DATE to loop.creationDate?.let {
            com.google.firebase.Timestamp(it.toJavaInstant())
        },
    )

    companion object {
        private const val TAG = "LoopFirestoreApi"

        // iOS uses `db.collection("Loop")` — capitalized, singular.
        const val COLLECTION_LOOPS = "Loop"

        const val FIELD_ACTIVE = "active"
        const val FIELD_ID = "id"
        const val FIELD_COMMUNITY_ID = "communityId"
        const val FIELD_PUBLISHER_ID = "publisherId"
        const val FIELD_TITLE = "title"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_DESCRIPTION_HTML = "descriptionHtml"
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
