package com.kornerz.core.network.loops

import com.kornerz.core.model.CommentLike
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the comment-like collection — the Android counterpart
 * of iOS `CommentLikeRepository`
 * (`kornerz/Kornerz/Backend/Repositories/CommentLikeRepository.swift`).
 *
 * Besides the single-comment writes, it exposes the **batched**
 * engagement reads the loop detail screen needs: one query for the live
 * like counts of every comment on the screen and one for the ids the
 * signed-in user has liked. A 40-comment loop costs two reads instead
 * of the 80 iOS's per-comment fan-out costs.
 */
@Singleton
class CommentLikeRepository @Inject constructor(
    private val api: CommentLikeFirestoreApi,
) {
    suspend fun likeComment(userId: String, commentId: String): Result<Unit> =
        api.likeComment(userId, commentId)

    suspend fun unlikeComment(userId: String, commentId: String): Result<Unit> =
        api.unlikeComment(userId, commentId)

    suspend fun checkIfUserLiked(userId: String, commentId: String): Result<Boolean> =
        api.checkIfUserLiked(userId, commentId)

    suspend fun fetchLikesForComment(commentId: String): Result<List<CommentLike>> =
        api.fetchLikesForComment(commentId)

    /** The live like count of every comment in [commentIds]. */
    suspend fun fetchLikeCounts(commentIds: List<String>): Result<Map<String, Int>> =
        api.fetchLikeCounts(commentIds)

    /** The ids of the comments in [commentIds] that [userId] has liked. */
    suspend fun fetchLikedCommentIds(
        userId: String,
        commentIds: List<String>,
    ): Result<Set<String>> = api.fetchLikedCommentIds(userId, commentIds)
}
