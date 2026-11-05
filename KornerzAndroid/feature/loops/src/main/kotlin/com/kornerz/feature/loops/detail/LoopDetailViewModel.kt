package com.kornerz.feature.loops.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.LoopAccentPalette
import com.kornerz.core.model.LoopComment
import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.network.gamification.GamificationRepository
import com.kornerz.core.network.loops.CommentLikeRepository
import com.kornerz.core.network.loops.LoopDetailRepository
import com.kornerz.core.network.user.UsersRepository
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.loops.LoopsRoute
import com.kornerz.feature.loops.data.LoopCommentRepository
import com.kornerz.feature.loops.data.LoopLikeRepository
import com.kornerz.feature.loops.data.LoopRepository
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

/**
 * Hilt-managed ViewModel for the loop detail screen.
 *
 * Mirrors the iOS `LoopDetailViewModel`
 * (`kornerz/Kornerz/SwiftUI/Dashboard/Loops/LoopDetail/LoopDetailViewModel.swift`):
 *  - the loop, its owner and its community are resolved **together**
 *    through [LoopDetailRepository];
 *  - the comment list is composed with the authors' users, the **live**
 *    like counts and the liked-by-me flags;
 *  - `incrementViewCount` fires exactly once per opening;
 *  - the like toggle is optimistic and reverts on failure;
 *  - every write is gated on the **real** signed-in user's `codeUser`,
 *    resolved from [SessionRepository] — the pre-P5 placeholder was the
 *    empty string, which made every write a silent no-op;
 *  - the Klouders points iOS awards are written on the same triggers.
 */
@HiltViewModel
class LoopDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loopDetailRepository: LoopDetailRepository,
    private val loopRepository: LoopRepository,
    private val loopCommentRepository: LoopCommentRepository,
    private val loopLikeRepository: LoopLikeRepository,
    private val commentLikeRepository: CommentLikeRepository,
    private val gamificationRepository: GamificationRepository,
    private val sessionRepository: SessionRepository,
    private val usersRepository: UsersRepository,
) : ViewModel() {

    val loopId: String = savedStateHandle.get<String>(LoopsRoute.Detail.ARG_LOOP_ID).orEmpty()

    /**
     * The accent colour handed over by the screen that opened the loop
     * — the feed row's colour.
     *
     * iOS passes the very same colour from the feed to the detail
     * screen, so the detail paints itself in the colour the user
     * tapped (`LoopsTabView.swift:114-118`). `null` when the
     * destination was opened without one — a deep link, a
     * notification — and the screen falls back to the loop-id-derived
     * palette entry.
     */
    private val handoverAccentColorHex: String? =
        savedStateHandle.get<String>(LoopsRoute.Detail.ARG_ACCENT_COLOR_HEX)
            ?.takeIf { it.isNotBlank() }
            ?.let { LoopAccentPalette.canonical(it) }
            ?.takeIf { LoopAccentPalette.isHexColor(it) }

    private val _state = MutableStateFlow(LoopDetailState(accentColorHex = handoverAccentColorHex))
    val state: StateFlow<LoopDetailState> = _state.asStateFlow()

    private val _navEvents = Channel<LoopDetailNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<LoopDetailNavEvent> = _navEvents.receiveAsFlow()

    private var fetchJob: Job? = null

    /**
     * The comment subscription. It is restarted — and the previous
     * subscription cancelled — on every fetch, so a pull-to-refresh
     * cannot leave two collectors reading the same collection.
     */
    private var commentsJob: Job? = null

    /** Guards the one-shot `ACCESS_LOOP` award against a re-fetch. */
    private var hasAwardedAccessLoop: Boolean = false

    init {
        observeSession()
        fetchData()
    }

    fun onEvent(event: LoopDetailEvent) {
        when (event) {
            LoopDetailEvent.Retry -> fetchData()
            LoopDetailEvent.Refresh -> fetchData(forcedRefresh = true)
            LoopDetailEvent.BackTapped -> emit(LoopDetailNavEvent.Pop)
            is LoopDetailEvent.MediaTapped -> openVideoPlayer()
            is LoopDetailEvent.KornerTapped -> navigateToKornerDetail(event.communityId)
            LoopDetailEvent.LikeToggled -> toggleLike()
            is LoopDetailEvent.AddCommentTapped -> {
                // The comment sheet posts against the resolved loop, so
                // a tap that arrives while the screen is still loading
                // is swallowed — the bar is disabled in the UI, and the
                // event is guarded here too.
                if (_state.value.isLoading) return
                emit(
                    LoopDetailNavEvent.OpenCreateComment(
                        loopId = event.loopId,
                        accentColorHex = _state.value.resolvedAccentColorHex,
                    ),
                )
            }
            is LoopDetailEvent.CommentAvatarTapped -> { /* future */ }
            is LoopDetailEvent.CommentLikeToggled -> toggleCommentLike(event.commentInfo)
            is LoopDetailEvent.CommentDeleteConfirmed -> deleteComment(event.commentId)
            is LoopDetailEvent.CommentReplyTapped ->
                emit(
                    LoopDetailNavEvent.OpenReply(
                        loopId = loopId,
                        commentId = event.commentId,
                        accentColorHex = _state.value.resolvedAccentColorHex,
                    ),
                )
            LoopDetailEvent.VideoDismissed -> _state.update { it.copy(isVideoPlayerOpen = false) }
            is LoopDetailEvent.CommentBodyToggled -> {
                // The shared `KornerzExpandableText` owns its expansion
                // state, so nothing has to be mirrored here.
            }
        }
    }

    /**
     * Increments the loop's view count — **once per opening**.
     *
     * The increment targets the resolved loop's **document id**, which
     * is not necessarily the id the route carries: the route may hold
     * the loop's `id` *field*, which is a different value. The write
     * therefore waits for the detail to resolve.
     */
    fun incrementViewCountOnce() {
        if (_state.value.hasIncrementedViewCount) return
        _state.update { it.copy(hasIncrementedViewCount = true) }
        viewModelScope.launch {
            val documentId = awaitResolvedDocumentId() ?: loopId
            loopRepository.incrementViewCount(documentId)
        }
    }

    /**
     * Waits for the detail to resolve and returns the loop's document
     * id, or `null` when the detail failed to load.
     */
    private suspend fun awaitResolvedDocumentId(): String? {
        val snapshot = state.first { it.loop != null || it.detail is AsyncValue.Error }
        return snapshot.loop?.id?.takeIf { it.isNotBlank() }
    }

    /**
     * Loads the screen: the loop bundle first, then the comments, then
     * the loop's own like state.
     */
    fun fetchData(forcedRefresh: Boolean = false) {
        if (loopId.isBlank()) return
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            if (forcedRefresh) {
                // A refresh keeps the loaded content on screen so the
                // scroll position survives.
                _state.update { it.copy(isRefreshing = true) }
            } else {
                _state.update {
                    it.copy(detail = AsyncValue.Loading, comments = AsyncValue.Loading)
                }
            }
            val result = loopDetailRepository.fetchLoopDetail(loopId)
            val failure = result.exceptionOrNull()
            if (failure != null) {
                _state.update { it.copy(detail = AsyncValue.Error(failure), isRefreshing = false) }
            } else {
                val resolved = result.getOrThrow()
                _state.update {
                    it.copy(
                        detail = AsyncValue.Success(resolved),
                        likesCount = resolved.loop.likesCount ?: 0,
                        commentsCount = resolved.loop.commentsCount ?: 0,
                        isRefreshing = false,
                    )
                }
                awardAccessLoopOnce()
                fetchLikeState()
            }
            // The comments load even when the loop itself failed — a
            // broken bundle must not hide them.
            fetchComments()
        }
    }

    /**
     * Streams the comments and composes each row with its author, its
     * **live** like count and its liked-by-me flag.
     *
     * The stream is **live** — it is backed by a Firestore snapshot
     * listener — so a comment posted from the create-comment sheet
     * lands in the list on its own: the screen is never told that
     * something was written, and never has to re-read anything. This
     * is the Android mirror of iOS's `.refreshLoopDetail` notification,
     * which iOS posts after a successful create and which
     * `LoopDetailViewModel` observes to re-fetch its comments.
     */
    private fun fetchComments() {
        commentsJob?.cancel()
        commentsJob = viewModelScope.launch {
            // The source of the last composed list. A snapshot that
            // carries the same comments as the previous one — the
            // local write, then the server's acknowledgement of it —
            // is skipped: the rows would be identical, and composing
            // them costs three batched reads.
            var composedSource: List<LoopComment>? = null
            loopCommentRepository.observeComments(loopId).collect { result ->
                result.fold(
                    onSuccess = { comments ->
                        if (comments != composedSource) {
                            composedSource = comments
                            val composed = composeComments(comments)
                            _state.update { it.copy(comments = AsyncValue.Success(composed)) }
                        }
                    },
                    onFailure = { error ->
                        _state.update { it.copy(comments = AsyncValue.Error(error)) }
                    },
                )
            }
        }
    }

    /**
     * Composes the comment rows.
     *
     * The authors are resolved with **one** batched `Users` read and
     * the engagement with **two** batched `CommentLikes` reads, so a
     * 40-comment loop costs three reads instead of the 80 iOS's
     * per-comment fan-out costs.
     */
    private suspend fun composeComments(comments: List<LoopComment>): List<LoopCommentInfo> {
        val commentIds = comments.mapNotNull { it.id }
        val authorIds = comments.mapNotNull { it.userId }.distinct()

        val users = if (authorIds.isEmpty()) {
            emptyMap()
        } else {
            usersRepository.fetchUsersByCodes(authorIds).getOrDefault(emptyMap())
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
            LoopCommentInfo(
                comment = comment,
                user = comment.userId?.let { users[it] },
                isLikedByUser = comment.id != null && comment.id in likedIds,
                likesCount = when {
                    liveLikeCounts == null || comment.id == null -> comment.likesCount ?: 0
                    else -> liveLikeCounts[comment.id] ?: 0
                },
                repliesCount = comment.repliesCount ?: 0,
            )
        }
    }

    /** Reads whether the signed-in user has liked the loop. */
    private suspend fun fetchLikeState() {
        val code = currentUserCode() ?: return
        val liked = loopLikeRepository.checkIfUserLiked(code, loopId).getOrNull() ?: return
        _state.update { it.copy(isLikedByUser = liked) }
    }

    /**
     * The like toggle — optimistic, reverting on failure, and a no-op
     * when no signed-in user with a `codeUser` resolves.
     */
    private fun toggleLike() {
        viewModelScope.launch {
            val code = currentUserCode() ?: return@launch
            val wasLiked = _state.value.isLikedByUser
            _state.update {
                it.copy(
                    isLikedByUser = !wasLiked,
                    isLikeInProgress = true,
                    likesCount = (it.likesCount + if (wasLiked) -1 else 1).coerceAtLeast(0),
                )
            }
            val result = if (wasLiked) {
                loopLikeRepository.unlikeLoop(code, loopId)
            } else {
                loopLikeRepository.likeLoop(code, loopId)
            }
            if (result.isFailure) {
                _state.update {
                    it.copy(
                        isLikedByUser = wasLiked,
                        likesCount = (it.likesCount + if (wasLiked) 1 else -1).coerceAtLeast(0),
                    )
                }
            }
            _state.update { it.copy(isLikeInProgress = false) }
        }
    }

    /**
     * The comment-like toggle — optimistic, reverting on failure, and
     * gated on the signed-in user's `codeUser`.
     */
    private fun toggleCommentLike(info: LoopCommentInfo) {
        viewModelScope.launch {
            val code = currentUserCode() ?: return@launch
            val commentId = info.comment.id ?: return@launch
            val current = _state.value.comments as? AsyncValue.Success ?: return@launch
            val index = current.data.indexOfFirst { it.comment.id == commentId }
            if (index < 0) return@launch
            val wasLiked = current.data[index].isLikedByUser
            val updated = current.data.toMutableList().also {
                it[index] = it[index].copy(
                    isLikedByUser = !wasLiked,
                    likesCount = (it[index].likesCount + if (wasLiked) -1 else 1).coerceAtLeast(0),
                )
            }
            _state.update { it.copy(comments = AsyncValue.Success(updated)) }
            val result = if (wasLiked) {
                commentLikeRepository.unlikeComment(code, commentId)
            } else {
                commentLikeRepository.likeComment(code, commentId)
            }
            if (result.isFailure) {
                _state.update { it.copy(comments = AsyncValue.Success(current.data)) }
            }
        }
    }

    /**
     * Deletes a comment — gated on the signed-in user **owning** it,
     * mirroring iOS `LoopDetailViewModel.isCommentOwnedByUser`.
     */
    private fun deleteComment(commentId: String) {
        viewModelScope.launch {
            val code = currentUserCode() ?: return@launch
            val comments = (_state.value.comments as? AsyncValue.Success)?.data ?: return@launch
            val target = comments.firstOrNull { it.comment.id == commentId } ?: return@launch
            if (target.comment.userId != code) return@launch
            val result = loopCommentRepository.deleteComment(commentId)
            if (result.isSuccess) {
                val remaining = comments.filterNot { it.comment.id == commentId }
                _state.update {
                    it.copy(
                        comments = AsyncValue.Success(remaining),
                        commentsCount = (it.commentsCount - 1).coerceAtLeast(0),
                    )
                }
            }
        }
    }

    /**
     * Opens the modal video player for a video loop and awards the
     * `ACCESS_REPLAYS` point. A still-image loop — or a loop with no
     * video url — is a no-op.
     */
    private fun openVideoPlayer() {
        val loop = _state.value.loop ?: return
        if (!loop.isVideo) return
        val videoUrl = loop.firebaseVideoUrl?.takeIf { it.isNotBlank() } ?: return
        _state.update { it.copy(isVideoPlayerOpen = true) }
        viewModelScope.launch {
            gamificationRepository.addPoints(
                action = ACTION_ACCESS_REPLAYS,
                points = 1,
                referenceId = videoUrl,
            )
        }
    }

    /**
     * Navigates to the loop's Korner detail, using the **resolved**
     * community's identifier — the event carries a bare id the shell
     * used to drop.
     */
    private fun navigateToKornerDetail(fallbackId: String) {
        val target = _state.value.community?.id?.takeIf { it.isNotBlank() } ?: fallbackId
        emit(LoopDetailNavEvent.NavigateToKornerDetail(target))
    }

    /**
     * Awards the one-shot `ACCESS_LOOP` point. The write never blocks
     * the screen and never fires twice for the same opening.
     */
    private fun awardAccessLoopOnce() {
        if (hasAwardedAccessLoop) return
        hasAwardedAccessLoop = true
        viewModelScope.launch {
            gamificationRepository.addPoints(
                action = ACTION_ACCESS_LOOP,
                points = 1,
                referenceId = loopId,
            )
        }
    }

    /**
     * Mirrors the session into the state so the UI can tell whether the
     * signed-in user owns a comment.
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

    private fun emit(event: LoopDetailNavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }

    companion object {
        /** iOS `GamificationAction.accessLoop`. */
        const val ACTION_ACCESS_LOOP: String = "ACCESS_LOOP"

        /** iOS `GamificationAction.accessReplays`. */
        const val ACTION_ACCESS_REPLAYS: String = "ACCESS_REPLAYS"

        /** iOS `GamificationAction.accessComment`. */
        const val ACTION_ACCESS_COMMENT: String = "ACCESS_COMMENT"
    }
}
