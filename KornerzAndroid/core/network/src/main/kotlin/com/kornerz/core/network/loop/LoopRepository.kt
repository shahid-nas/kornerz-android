package com.kornerz.core.network.loop

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kornerz.core.model.Loop
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
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

    private fun Map<String, Any?>.toLoop(id: String): Loop {
        val thumbnail = (this[FIELD_FIREBASE_THUMBNAIL_URL] as? String)
            ?: (this[FIELD_FIREBASE_IMAGE_URL] as? String)
        val createdAtMs = (this[FIELD_CREATION_DATE] as? Timestamp)
            ?.seconds?.times(1000L) ?: 0L
        return Loop(
            id = id,
            title = (this[FIELD_TITLE] as? String).orEmpty(),
            thumbnailUrl = thumbnail,
            color = this[FIELD_THEME] as? String,
            durationSeconds = null,
            createdAt = Instant.fromEpochMilliseconds(createdAtMs),
            publisherId = this[FIELD_PUBLISHER_ID] as? String,
            publisherName = null,
            publisherAvatarUrl = null,
            description = this[FIELD_DESCRIPTION] as? String,
            communityId = this[FIELD_COMMUNITY_ID] as? String,
            communityName = null,
            theme = this[FIELD_THEME] as? String,
            viewCounts = (this[FIELD_VIEW_COUNTS] as? Long)?.toInt(),
            commentsCount = (this[FIELD_COMMENTS_COUNT] as? Long)?.toInt(),
            likesCount = (this[FIELD_LIKES_COUNT] as? Long)?.toInt(),
        )
    }

    companion object {
        private const val TAG = "LoopRepository"
        private const val COLLECTION_NAME = "Loop"
        private const val FIELD_TITLE = "title"
        private const val FIELD_DESCRIPTION = "description"
        private const val FIELD_CREATION_DATE = "creationDate"
        private const val FIELD_PUBLISHER_ID = "publisherId"
        private const val FIELD_COMMUNITY_ID = "communityId"
        private const val FIELD_THEME = "theme"
        private const val FIELD_VIEW_COUNTS = "viewCounts"
        private const val FIELD_COMMENTS_COUNT = "commentsCount"
        private const val FIELD_LIKES_COUNT = "likesCount"
        private const val FIELD_FIREBASE_IMAGE_URL = "firebaseImageUrl"
        private const val FIELD_FIREBASE_THUMBNAIL_URL = "firebaseThumbnailUrl"
    }
}
