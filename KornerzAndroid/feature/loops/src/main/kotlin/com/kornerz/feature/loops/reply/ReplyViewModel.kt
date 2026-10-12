package com.kornerz.feature.loops.reply

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.model.User
import com.kornerz.core.network.user.UsersRepository
import com.kornerz.feature.loops.LoopsRoute
import com.kornerz.feature.loops.data.CommentLikeRepository
import com.kornerz.feature.loops.data.LoopCommentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    val loopId: String = savedStateHandle.get<String>(LoopsRoute.Reply.ARG_LOOP_ID).orEmpty()
    val commentId: String = savedStateHandle.get<String>(LoopsRoute.Reply.ARG_COMMENT_ID).orEmpty()

    private val _state = MutableStateFlow(ReplyState())
    val state: StateFlow<ReplyState> = _state.asStateFlow()

    private val _navEvents = Channel<ReplyNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<ReplyNavEvent> = _navEvents.receiveAsFlow()

    private val currentUserId: String = ""

    init {
        observeParent()
        observeReplies()
    }

    fun onEvent(event: ReplyEvent) {
        when (event) {
            is ReplyEvent.TextChanged -> _state.update { it.copy(text = event.text) }
            ReplyEvent.SendTapped -> submit()
            ReplyEvent.BackTapped -> emit(ReplyNavEvent.Pop)
            is ReplyEvent.CommentLikeToggled -> toggleLike(event.info)
            is ReplyEvent.CommentDeleteConfirmed -> delete(event.commentId)
        }
    }

    private fun observeParent() {
        viewModelScope.launch {
            val parent = loopCommentRepository.fetchCommentById(commentId).getOrNull()
            if (parent == null) {
                _state.update { it.copy(parentComment = AsyncValue.Error(IllegalStateException("Not found"))) }
                return@launch
            }
            val user = parent.userId?.let { uid ->
                usersRepository.fetchUsersByCodes(listOf(uid))
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
            _state.update {
                it.copy(
                    parentComment = AsyncValue.Success(
                        LoopCommentInfo(comment = parent, user = user, likesCount = parent.likesCount ?: 0),
                    ),
                )
            }
        }
    }

    private fun observeReplies() {
        viewModelScope.launch {
            loopCommentRepository.observeReplies(loopId, commentId).collect { result ->
                result.fold(
                    onSuccess = { comments ->
                        val infos = comments.map { c ->
                            val user = c.userId?.let { uid ->
                                usersRepository.fetchUsersByCodes(listOf(uid))
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
                            LoopCommentInfo(comment = c, user = user, likesCount = c.likesCount ?: 0)
                        }
                        _state.update { it.copy(replies = AsyncValue.Success(infos)) }
                    },
                    onFailure = { t -> _state.update { it.copy(replies = AsyncValue.Error(t)) } },
                )
            }
        }
    }

    private fun submit() {
        val text = _state.value.text
        if (text.isBlank() || loopId.isBlank() || commentId.isBlank() || currentUserId.isBlank()) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = loopCommentRepository.createComment(
                loopId = loopId,
                parentId = commentId,
                text = text,
                userId = currentUserId,
            )
            _state.update {
                if (result.isSuccess) it.copy(isSubmitting = false, text = "")
                else it.copy(isSubmitting = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    private fun toggleLike(info: LoopCommentInfo) {
        if (currentUserId.isBlank()) return
        val cid = info.comment.id ?: return
        viewModelScope.launch {
            val isLiked = info.isLikedByUser
            if (isLiked) commentLikeRepository.unlikeComment(currentUserId, cid)
            else commentLikeRepository.likeComment(currentUserId, cid)
            observeReplies()
        }
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
