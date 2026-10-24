package com.kornerz.feature.loops.reply

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.LoopAccentPalette
import com.kornerz.core.model.LoopComment
import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.model.User
import com.kornerz.core.network.loops.CommentLikeRepository
import com.kornerz.core.network.user.UserSnippet
import com.kornerz.core.network.user.UsersRepository
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.loops.LoopsRoute
import com.kornerz.feature.loops.data.LoopCommentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReplyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loopCommentRepository: LoopCommentRepository,
    private val commentLikeRepository: CommentLikeRepository,
    private val usersRepository: UsersRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val loopId: String = savedStateHandle.get<String>(LoopsRoute.Reply.ARG_LOOP_ID).orEmpty()
    val commentId: String = savedStateHandle.get<String>(LoopsRoute.Reply.ARG_COMMENT_ID).orEmpty()

    /**
     * The accent colour the thread paints with — the colour the detail
     * screen it was opened from is painted with, so the two agree.
     * Falls back to the loop-id-derived palette entry when the thread
     * was opened without one.
     */
    private val accentColorHex: String =
        savedStateHandle.get<String>(LoopsRoute.Reply.ARG_ACCENT_COLOR_HEX)
            ?.takeIf { it.isNotBlank() }
            ?.let { LoopAccentPalette.canonical(it) }
            ?.takeIf { LoopAccentPalette.isHexColor(it) }
            ?: LoopAccentPalette.hexForLoopId(loopId)

    private val _state = MutableStateFlow(ReplyState(accentColorHex = accentColorHex))
    val state: StateFlow<ReplyState> = _state.asStateFlow()

    private val _navEvents = Channel<ReplyNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<ReplyNavEvent> = _navEvents.receiveAsFlow()

    /**
     * The parent comment's load. It is cancelled before it is
     * restarted, so a refresh cannot leave two loads racing for the
     * state.
     */
    private var parentJob: Job? = null

    /**
     * The replies' subscription — cancelled before it is restarted for
     * the same reason.
     */
    private var repliesJob: Job? = null

    init {
        observeSession()
        observeParent()
        observeReplies()
    }

    fun onEvent(event: ReplyEvent) {
        when (event) {
            ReplyEvent.BackTapped -> emit(ReplyNavEvent.Pop)
            ReplyEvent.AddCommentTapped ->
                emit(
                    ReplyNavEvent.OpenCreateComment(
                        loopId = loopId,
                        parentCommentId = commentId,
                        accentColorHex = accentColorHex,
                    ),
                )
            is ReplyEvent.CommentLikeToggled -> toggleLike(event.info)
            is ReplyEvent.CommentDeleteConfirmed -> delete(event.commentId)
        }
    }

    /**
     * Loads the **parent comment** — the comment the thread replies to
     * — and composes it with its author and its engagement state, so
     * its heart reflects the signed-in user's like.
     *
     * The load is **live**: the parent is observed, not read once, so
     * its `repliesCount` follows the replies posted under it.
     */
    private fun observeParent() {
        parentJob?.cancel()
        parentJob = viewModelScope.launch {
            loopCommentRepository.observeComment(commentId).collect { result ->
                val parent = result.getOrNull()
                if (parent == null) {
                    val error = result.exceptionOrNull() ?: IllegalStateException("Not found")
                    _state.update { it.copy(parentComment = AsyncValue.Error(error)) }
                } else {
                    val info = composeComments(listOf(parent)).firstOrNull()
                    if (info != null) {
                        _state.update { it.copy(parentComment = AsyncValue.Success(info)) }
                    }
                }
            }
        }
    }

    /**
     * Streams the replies and composes each row with its author and
     * its engagement state.
     *
     * The stream is **live** — it is backed by a Firestore snapshot
     * listener — so a reply posted from the create-comment sheet lands
     * in the list on its own: the thread is never told that something
     * was written, and never has to re-read anything. This is the
     * Android mirror of iOS's `.refreshLoopDetail` notification, which
     * iOS posts after a successful create and which
     * `LoopReplyViewModel` observes to re-fetch its replies.
     */
    private fun observeReplies() {
        repliesJob?.cancel()
        repliesJob = viewModelScope.launch {
            // The source of the last composed list. A snapshot that
            // carries the same comments as the previous one — the
            // local write, then the server's acknowledgement of it —
            // is skipped: the rows would be identical, and composing
            // them costs three batched reads.
            var composedSource: List<LoopComment>? = null
            loopCommentRepository.observeReplies(loopId, commentId).collect { result ->
                result.fold(
                    onSuccess = { comments ->
                        if (comments != composedSource) {
                            composedSource = comments
                            val infos = composeComments(comments)
                            _state.update { it.copy(replies = AsyncValue.Success(infos)) }
                        }
                    },
                    onFailure = { t -> _state.update { it.copy(replies = AsyncValue.Error(t)) } },
                )
            }
        }
    }

    /**
     * Composes the comment rows — the author, the **live** like count
     * and the liked-by-me flag.
     *
     * The engagement reads are **batched**, exactly like the loop
     * detail screen's: one `CommentLikes` read for the counts and one
     * for the ids the signed-in user has liked, so a 40-reply thread
     * costs two reads instead of the 80 iOS's per-comment fan-out
     * costs. The live count replaces the denormalized
     * `LoopComments.likesCount`, mirroring iOS's
     * `LoopCommentInfo.setLikeCount(_:)`.
     */
    private suspend fun composeComments(comments: List<LoopComment>): List<LoopCommentInfo> {
        if (comments.isEmpty()) return emptyList()

        val commentIds = comments.mapNotNull { it.id }
        val authorIds = comments.mapNotNull { it.userId }.distinct()

        val snippets: Map<String, UserSnippet> = if (authorIds.isEmpty()) {
            emptyMap()
        } else {
            runCatching { usersRepository.fetchUserSnippetsByCodes(authorIds) }
                .getOrDefault(emptyMap())
        }

        val liveLikeCounts = if (commentIds.isEmpty()) {
            null
        } else {
            commentLikeRepository.fetchLikeCounts(commentIds).getOrNull()
        }

        val likedIds = currentUserCode()?.let { code ->
            if (commentIds.isEmpty()) {
                emptySet()
            } else {
                commentLikeRepository.fetchLikedCommentIds(code, commentIds).getOrDefault(emptySet())
            }
        } ?: emptySet()

        return comments.map { comment ->
            val id = comment.id
            LoopCommentInfo(
                comment = comment,
                user = comment.userId?.let { code -> snippets[code]?.toUser() },
                isLikedByUser = id != null && id in likedIds,
                likesCount = when {
                    liveLikeCounts == null || id == null -> comment.likesCount ?: 0
                    else -> liveLikeCounts[id] ?: 0
                },
                repliesCount = comment.repliesCount ?: 0,
            )
        }
    }

    /**
     * The comment-like toggle — optimistic, reverting on failure, and
     * gated on the signed-in user's `codeUser`.
     *
     * The row that was tapped is the one that flips, whether it is the
     * pinned parent comment or one of the replies: the like state is
     * read from the state, not from the event, so a second tap always
     * unlikes what the first tap liked.
     */
    private fun toggleLike(info: LoopCommentInfo) {
        viewModelScope.launch {
            val code = currentUserCode() ?: return@launch
            val targetId = info.comment.id ?: return@launch
            val wasLiked = findComment(targetId)?.isLikedByUser ?: info.isLikedByUser

            updateComment(targetId) { current ->
                current.copy(
                    isLikedByUser = !wasLiked,
                    likesCount = (current.likesCount + if (wasLiked) -1 else 1).coerceAtLeast(0),
                )
            }

            val result = if (wasLiked) {
                commentLikeRepository.unlikeComment(code, targetId)
            } else {
                commentLikeRepository.likeComment(code, targetId)
            }
            if (result.isFailure) {
                updateComment(targetId) { current ->
                    current.copy(
                        isLikedByUser = wasLiked,
                        likesCount = (current.likesCount + if (wasLiked) 1 else -1).coerceAtLeast(0),
                    )
                }
                return@launch
            }
            // The write landed — confirm the row against the server's
            // live count so the two agree.
            refreshEngagement(targetId)
        }
    }

    /**
     * Re-reads the **live** engagement of a single comment — its like
     * count and the signed-in user's liked flag — and writes it back
     * into the state.
     */
    private suspend fun refreshEngagement(id: String) {
        val code = currentUserCode() ?: return
        val counts = commentLikeRepository.fetchLikeCounts(listOf(id)).getOrNull() ?: return
        val likedIds = commentLikeRepository.fetchLikedCommentIds(code, listOf(id))
            .getOrDefault(emptySet())
        updateComment(id) { current ->
            current.copy(
                isLikedByUser = id in likedIds,
                likesCount = counts[id] ?: 0,
            )
        }
    }

    /**
     * Patches the comment row with the given id — the pinned parent
     * comment or one of the replies — with [transform].
     */
    private fun updateComment(id: String, transform: (LoopCommentInfo) -> LoopCommentInfo) {
        _state.update { state ->
            val parent = when (val value = state.parentComment) {
                is AsyncValue.Success ->
                    if (value.data.comment.id == id) {
                        AsyncValue.Success(transform(value.data))
                    } else {
                        value
                    }
                else -> value
            }
            val replies = when (val value = state.replies) {
                is AsyncValue.Success -> {
                    val index = value.data.indexOfFirst { it.comment.id == id }
                    if (index < 0) {
                        value
                    } else {
                        val updated = value.data.toMutableList()
                        updated[index] = transform(updated[index])
                        AsyncValue.Success(updated.toList())
                    }
                }
                else -> value
            }
            state.copy(parentComment = parent, replies = replies)
        }
    }

    /**
     * The current row for the comment with the given id — the pinned
     * parent comment or one of the replies.
     */
    private fun findComment(id: String): LoopCommentInfo? {
        val state = _state.value
        val parent = when (val value = state.parentComment) {
            is AsyncValue.Success -> value.data
            else -> null
        }
        if (parent?.comment?.id == id) return parent
        val replies = when (val value = state.replies) {
            is AsyncValue.Success -> value.data
            else -> return null
        }
        return replies.firstOrNull { it.comment.id == id }
    }

    /**
     * Mirrors the session into the state so the add-comment bar can
     * show the signed-in user's avatar and the like / delete actions
     * can be gated on the real identity.
     */
    private fun observeSession() {
        viewModelScope.launch {
            sessionRepository.data.collect { session ->
                val user = (session as? SessionState.Authenticated)?.user
                _state.update {
                    it.copy(
                        currentUserCode = user?.userCode,
                        currentUserAvatarUrl = user?.thumbnailURL ?: user?.photoUrl,
                    )
                }
            }
        }
    }

    /**
     * The signed-in user's `codeUser`, or `null` when the session is
     * anonymous or carries no code.
     *
     * The cached state value is used when it is available; otherwise the
     * session is awaited once, so a write that happens before the first
     * session emission still resolves the real identity.
     */
    private suspend fun currentUserCode(): String? {
        _state.value.currentUserCode?.let { return it }
        return sessionRepository.data
            .filterNot { it is SessionState.Loading }
            .map { (it as? SessionState.Authenticated)?.user?.userCode }
            .first()
            ?.takeIf { it.isNotBlank() }
    }

    private fun delete(commentId: String) {
        viewModelScope.launch {
            loopCommentRepository.deleteComment(commentId)
            observeReplies()
        }
    }

    private fun emit(event: ReplyNavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }
}

/**
 * The snippet projection the comment row renders — the display name
 * and the avatar URL, the same projection the feeds use.
 */
private fun UserSnippet.toUser(): User = User(
    id = code,
    email = "",
    displayName = name,
    nickname = name,
    photoUrl = thumbnailUrl,
)
