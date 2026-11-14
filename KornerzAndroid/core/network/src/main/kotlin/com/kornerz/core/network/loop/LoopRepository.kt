package com.kornerz.core.network.loop

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kornerz.core.model.Loop
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.toKotlinInstant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoopRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    val collection: CollectionReference
        get() = firestore.collection(COLLECTION_NAME)

    suspend fun fetchLatestLoop(): Result<Loop> = runCatching {
        val snapshot = collection
            .orderBy(FIELD_CREATION_DATE, Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        val document = snapshot.documents.firstOrNull()
            ?: throw NoSuchElementException("No loop available")
        (document.data ?: emptyMap<String, Any?>()).toLoop(document.id)
    }.onFailure { Log.w(TAG, "fetchLatestLoop: ${it.message}") }

    suspend fun fetchRecentLoops(limit: Long = 10): Result<List<Loop>> = runCatching {
        collection
            .orderBy(FIELD_CREATION_DATE, Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
            .documents
            .map { (it.data ?: emptyMap<String, Any?>()).toLoop(it.id) }
    }.recover {
        Log.w(TAG, "fetchRecentLoops fallback: ${it.message}")
        emptyList()
    }

    /**
     * Decodes a `Loop` document.
     *
     * Mirrors iOS `LoopRepository.fetchLatestLoop`
     * (`kornerz/Kornerz/Backend/Repositories/LoopRepository.swift:88`),
     * which decodes the **whole** `Loop` struct via
     * `document.data(as: Loop.self)` — so every media field is
     * populated. This decoder previously populated only a legacy
     * subset (notably not `firebaseThumbnailUrl`), which left the
     * Home tab's "Loop of the day" / "Recent at Kornerz" rows
     * without their media while the Loops tab — decoded by
     * `core/network/loops/LoopFirestoreApi` — showed it.
     *
     * The field set now matches `LoopFirestoreApi.toLoop` exactly, so
     * both surfaces read the same document into the same shape.
     */
    private fun Map<String, Any?>.toLoop(id: String): Loop {
        val createdAt = (this[FIELD_CREATION_DATE] as? Timestamp)
            ?.toInstant()?.toKotlinInstant()
        return Loop(
            id = id,
            communityId = this[FIELD_COMMUNITY_ID] as? String,
            publisherId = this[FIELD_PUBLISHER_ID] as? String,
            active = this[FIELD_ACTIVE] as? Boolean,
            title = (this[FIELD_TITLE] as? String).orEmpty(),
            description = this[FIELD_DESCRIPTION] as? String,
            descriptionHtml = this[FIELD_DESCRIPTION_HTML] as? String,
            firebaseImageUrl = this[FIELD_FIREBASE_IMAGE_URL] as? String,
            firebaseThumbnailUrl = this[FIELD_FIREBASE_THUMBNAIL_URL] as? String,
            firebaseVideoUrl = this[FIELD_FIREBASE_VIDEO_URL] as? String,
            viewCounts = (this[FIELD_VIEW_COUNTS] as? Long)?.toInt(),
            commentsCount = (this[FIELD_COMMENTS_COUNT] as? Long)?.toInt(),
            likesCount = (this[FIELD_LIKES_COUNT] as? Long)?.toInt(),
            theme = this[FIELD_THEME] as? String,
            creationDate = createdAt,
            // P2 backwards-compat aliases.
            thumbnailUrl = (this[FIELD_FIREBASE_THUMBNAIL_URL] as? String)
                ?: (this[FIELD_FIREBASE_IMAGE_URL] as? String),
            color = this[FIELD_THEME] as? String,
            durationSeconds = null,
            createdAt = createdAt,
            publisherName = null,
            publisherAvatarUrl = null,
            communityName = null,
        )
    }

    companion object {
        private const val TAG = "LoopRepository"
        private const val COLLECTION_NAME = "Loop"
        private const val FIELD_TITLE = "title"
        private const val FIELD_DESCRIPTION = "description"
        private const val FIELD_DESCRIPTION_HTML = "descriptionHtml"
        private const val FIELD_CREATION_DATE = "creationDate"
        private const val FIELD_PUBLISHER_ID = "publisherId"
        private const val FIELD_COMMUNITY_ID = "communityId"
        private const val FIELD_ACTIVE = "active"
        private const val FIELD_THEME = "theme"
        private const val FIELD_VIEW_COUNTS = "viewCounts"
        private const val FIELD_COMMENTS_COUNT = "commentsCount"
        private const val FIELD_LIKES_COUNT = "likesCount"
        private const val FIELD_FIREBASE_IMAGE_URL = "firebaseImageUrl"
        private const val FIELD_FIREBASE_THUMBNAIL_URL = "firebaseThumbnailUrl"
        private const val FIELD_FIREBASE_VIDEO_URL = "firebaseVideoUrl"
    }
}
