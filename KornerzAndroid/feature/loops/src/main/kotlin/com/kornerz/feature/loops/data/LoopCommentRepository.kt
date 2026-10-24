package com.kornerz.feature.loops.data

import com.kornerz.core.model.LoopComment
import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.model.User
import com.kornerz.core.network.loops.LoopCommentFirestoreApi
import com.kornerz.core.network.user.UsersRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoopCommentRepository @Inject constructor(
    private val api: LoopCommentFirestoreApi,
    private val usersRepository: UsersRepository,
) {
    fun observeComments(loopId: String): Flow<Result<List<LoopComment>>> =
        api.observeComments(loopId)

    fun observeReplies(loopId: String, parentCommentId: String): Flow<Result<List<LoopComment>>> =
        api.observeReplies(loopId, parentCommentId)

    /**
     * A single comment, live — the reply thread's pinned parent
     * comment, whose `repliesCount` follows the replies posted under
     * it.
     */
    fun observeComment(commentId: String): Flow<Result<LoopComment?>> =
        api.observeComment(commentId)

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

    /**
     * Fetches a comment **together with its author** — the projection a
     * comment row renders.
     *
     * The join mirrors the one iOS performs while it assembles a
     * `LoopCommentInfo`: the comment document, then the author's
     * `Users` document.
     */
    suspend fun fetchCommentWithAuthor(commentId: String): Result<LoopCommentInfo?> {
        val comment = api.fetchCommentById(commentId)
            .getOrElse { return Result.failure(it) }
            ?: return Result.success(null)
        val author = comment.userId?.let { code ->
            usersRepository.fetchUserSnippetsByCodes(listOf(code))
                .values.firstOrNull()?.let { snippet ->
                    User(
                        id = snippet.code,
                        email = "",
                        displayName = snippet.name,
                        nickname = snippet.name,
                        photoUrl = snippet.thumbnailUrl,
                    )
                }
        }
        return Result.success(
            LoopCommentInfo(
                comment = comment,
                user = author,
                likesCount = comment.likesCount ?: 0,
                repliesCount = comment.repliesCount ?: 0,
            ),
        )
    }
}
