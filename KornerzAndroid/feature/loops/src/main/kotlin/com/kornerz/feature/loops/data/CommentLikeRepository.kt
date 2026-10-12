package com.kornerz.feature.loops.data

import com.kornerz.core.model.CommentLike
import com.kornerz.core.network.loops.CommentLikeFirestoreApi
import javax.inject.Inject
import javax.inject.Singleton

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
}
