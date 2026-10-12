package com.kornerz.feature.loops.data

import com.kornerz.core.model.LoopComment
import com.kornerz.core.network.loops.LoopCommentFirestoreApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoopCommentRepository @Inject constructor(
    private val api: LoopCommentFirestoreApi,
) {
    fun observeComments(loopId: String): Flow<Result<List<LoopComment>>> =
        api.observeComments(loopId)

    fun observeReplies(loopId: String, parentCommentId: String): Flow<Result<List<LoopComment>>> =
        api.observeReplies(loopId, parentCommentId)

    suspend fun createComment(
        loopId: String,
        parentId: String?,
        text: String,
        userId: String,
    ): Result<LoopComment> = api.createComment(loopId, parentId, text, userId)

    suspend fun deleteComment(commentId: String): Result<Unit> =
        api.deleteComment(commentId)

    suspend fun fetchCommentById(commentId: String): Result<LoopComment?> =
        api.fetchCommentById(commentId)
}
