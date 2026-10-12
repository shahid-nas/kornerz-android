package com.kornerz.feature.loops.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Loop
import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.model.User
import com.kornerz.core.network.user.UsersRepository
import com.kornerz.feature.loops.LoopsRoute
import com.kornerz.feature.loops.data.CommentLikeRepository
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hilt-managed ViewModel for the loop detail screen. Mirrors the iOS
 * `LoopDetailViewModel`:
 *  - single-fire `incrementViewCount` (gate on `hasIncrementedViewCount`)
 *  - optimistic like + revert-on-failure
 *  - parallel `checkIfUserLiked` + `fetchAllComments` fan-out
 */
@HiltViewModel
class LoopDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loopRepository: LoopRepository,
    private val loopCommentRepository: LoopCommentRepository,
    private val loopLikeRepository: LoopLikeRepository,
    private val commentLikeRepository: CommentLikeRepository,
    private val usersRepository: UsersRepository,
) : ViewModel() {

    val loopId: String = savedStateHandle.get<String>(LoopsRoute.Detail.ARG_LOOP_ID).orEmpty()

    private val _state = MutableStateFlow(LoopDetailState())
    val state: StateFlow<LoopDetailState> = _state.asStateFlow()

    private val _navEvents = Channel<LoopDetailNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<LoopDetailNavEvent> = _navEvents.receiveAsFlow()

    private var fetchJob: Job? = null
    // Placeholder signed-in user id; P7 wires this to FirebaseAuth.
    private val currentUserId: String = ""

    init {
        fetchData()
    }

    fun onEvent(event: LoopDetailEvent) {
        when (event) {
            LoopDetailEvent.Retry -> fetchData()
            LoopDetailEvent.Refresh -> fetchData(forcedRefresh = true)
            is LoopDetailEvent.MediaTapped -> { /* handled in UI; player.play() */ }
            is LoopDetailEvent.KornerTapped ->
                emit(LoopDetailNavEvent.NavigateToKornerDetail(event.communityId))
            LoopDetailEvent.LikeToggled -> toggleLike()
            is LoopDetailEvent.AddCommentTapped ->
                emit(LoopDetailNavEvent.OpenCreateComment(event.loopId))
            is LoopDetailEvent.CommentAvatarTapped -> { /* future */ }
            is LoopDetailEvent.CommentLikeToggled -> toggleCommentLike(event.commentInfo)
            is LoopDetailEvent.CommentDeleteConfirmed -> deleteComment(event.commentId)
            is LoopDetailEvent.CommentReplyTapped ->
                emit(LoopDetailNavEvent.OpenReply(loopId, event.commentId))
        }
    }

    fun incrementViewCountOnce() {
        if (_state.value.hasIncrementedViewCount) return
        _state.update { it.copy(hasIncrementedViewCount = true) }
        viewModelScope.launch { loopRepository.incrementViewCount(loopId) }
    }

    fun fetchData(forcedRefresh: Boolean = false) {
        if (loopId.isBlank()) return
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.update { it.copy(detail = AsyncValue.Loading, comments = AsyncValue.Loading) }
            loopRepository.fetchLoopDetail(loopId).collect { result ->
                result.fold(
                    onSuccess = { loop ->
                        _state.update {
                            it.copy(
                                detail = AsyncValue.Success(loop),
                                likesCount = loop.likesCount ?: 0,
                                commentsCount = loop.commentsCount ?: 0,
                            )
                        }
                    },
                    onFailure = { t ->
                        _state.update { it.copy(detail = AsyncValue.Error(t)) }
                    },
                )
            }
            fetchComments()
            fetchLikeState()
        }
    }

    private suspend fun fetchLikeState() {
        if (currentUserId.isBlank()) return
        val liked = loopLikeRepository.checkIfUserLiked(currentUserId, loopId).getOrNull() ?: return
        _state.update { it.copy(isLikedByUser = liked) }
    }

    private fun fetchComments() {
        viewModelScope.launch {
            loopCommentRepository.observeComments(loopId).collect { result ->
                result.fold(
                    onSuccess = { comments ->
                        val infos = comments.map { c ->
                            val user = c.userId?.let { uid ->
                                usersRepository.fetchUsersByCodes(listOf(uid))
                                    .values
                                    .firstOrNull()
                                    ?.let { snippet ->
                                        User(
                                            id = snippet.code,
                                            email = "",
                                            displayName = snippet.name,
                                            nickname = snippet.name,
                                            photoUrl = snippet.thumbnailUrl,
                                        )
                                    }
                            }
                            LoopCommentInfo(
                                comment = c,
                                user = user,
                                isLikedByUser = false,
                                likesCount = c.likesCount ?: 0,
                            )
                        }
                        _state.update { it.copy(comments = AsyncValue.Success(infos)) }
                    },
                    onFailure = { t ->
                        _state.update { it.copy(comments = AsyncValue.Error(t)) }
                    },
                )
            }
        }
    }

    private fun toggleLike() {
        if (currentUserId.isBlank()) return
        val wasLiked = _state.value.isLikedByUser
        _state.update {
            it.copy(
                isLikedByUser = !wasLiked,
                isLikeInProgress = true,
                likesCount = (it.likesCount + if (wasLiked) -1 else 1).coerceAtLeast(0),
            )
        }
        viewModelScope.launch {
            val result = if (wasLiked) {
                loopLikeRepository.unlikeLoop(currentUserId, loopId)
            } else {
                loopLikeRepository.likeLoop(currentUserId, loopId)
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

    private fun toggleCommentLike(info: LoopCommentInfo) {
        val current = _state.value.comments as? AsyncValue.Success ?: return
        val idx = current.data.indexOfFirst { it.comment.id == info.comment.id }
        if (idx < 0) return
        val wasLiked = current.data[idx].isLikedByUser
        val updated = current.data.toMutableList().also {
            it[idx] = it[idx].copy(
                isLikedByUser = !wasLiked,
                likesCount = (it[idx].likesCount + if (wasLiked) -1 else 1).coerceAtLeast(0),
            )
        }
        _state.update { it.copy(comments = AsyncValue.Success(updated)) }
        val commentId = info.comment.id ?: return
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            val result = if (wasLiked) {
                commentLikeRepository.unlikeComment(currentUserId, commentId)
            } else {
                commentLikeRepository.likeComment(currentUserId, commentId)
            }
            if (result.isFailure) {
                _state.update {
                    it.copy(comments = AsyncValue.Success(current.data))
                }
            }
        }
    }

    private fun deleteComment(commentId: String) {
        viewModelScope.launch {
            loopCommentRepository.deleteComment(commentId)
            fetchComments()
        }
    }

    private fun emit(event: LoopDetailNavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }
}
