package com.kornerz.feature.loops.comment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.LoopAccentPalette
import com.kornerz.core.network.gamification.GamificationRepository
import com.kornerz.core.network.loops.LoopDetailRepository
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.loops.LoopsRoute
import com.kornerz.feature.loops.data.LoopCommentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * ViewModel for the create-comment sheet.
 *
 * The comment is written under the **signed-in user's `codeUser`** —
 * the identity iOS uses and the one the `LoopComments` documents are
 * keyed by. The pre-P5 placeholder was the empty string, which made
 * every comment anonymous and therefore invisible to the reply thread
 * and the ownership gate.
 *
 * A successful creation also awards the `ACCESS_COMMENT` Klouders
 * point, mirroring iOS `CreateCommentViewModel`. The award never
 * blocks the dismiss.
 */
@HiltViewModel
class CreateCommentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loopCommentRepository: LoopCommentRepository,
    private val gamificationRepository: GamificationRepository,
    private val sessionRepository: SessionRepository,
    private val loopDetailRepository: LoopDetailRepository,
) : ViewModel() {

    val loopId: String = savedStateHandle.get<String>(LoopsRoute.Comment.ARG_LOOP_ID).orEmpty()

    /**
     * The comment being replied to, or the empty string for a
     * top-level comment. iOS carries the same distinction in
     * `CreateCommentType`.
     */
    val parentCommentId: String =
        savedStateHandle.get<String>(LoopsRoute.Comment.ARG_PARENT_COMMENT_ID).orEmpty()

    /**
     * The accent colour handed over by the detail screen the sheet was
     * opened from, so the sheet inherits the colour the user is
     * looking at. `null` when the sheet was opened without one.
     */
    private val handoverAccentColorHex: String? =
        savedStateHandle.get<String>(LoopsRoute.Comment.ARG_ACCENT_COLOR_HEX)
            ?.takeIf { it.isNotBlank() }
            ?.let { LoopAccentPalette.canonical(it) }
            ?.takeIf { LoopAccentPalette.isHexColor(it) }

    // The parent id is known from the route, so the sheet renders its
    // "Reply" title on the very first frame — the comment it hangs off
    // is resolved asynchronously.
    private val _state = MutableStateFlow(
        CreateCommentState(
            parentCommentId = parentCommentId,
            accentColorHex = handoverAccentColorHex,
        ),
    )
    val state: StateFlow<CreateCommentState> = _state.asStateFlow()

    private val _navEvents = Channel<CreateCommentNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<CreateCommentNavEvent> = _navEvents.receiveAsFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Load current user
            sessionRepository.data
                .filterNot { it is SessionState.Loading }
                .map { (it as? SessionState.Authenticated)?.user }
                .first()
                ?.let { user ->
                    _state.update { it.copy(currentUser = user) }
                }

            // Load loop detail
            if (loopId.isNotBlank()) {
                loopDetailRepository.fetchLoopDetail(loopId).onSuccess { detail ->
                    _state.update { it.copy(loopDetail = detail) }
                }
            }

            // Load the comment being replied to — the sheet's header in
            // reply mode (iOS `CreateCommentType.replyToComment`).
            if (parentCommentId.isNotBlank()) {
                loadParentComment(parentCommentId)
            }
        }
    }

    /**
     * Resolves the parent comment and its author so the sheet can show
     * the comment the reply hangs off, exactly as iOS does.
     */
    private suspend fun loadParentComment(parentId: String) {
        val parent = loopCommentRepository.fetchCommentWithAuthor(parentId).getOrNull()
        if (parent == null) return
        _state.update { it.copy(parentComment = parent) }
    }

    fun onEvent(event: CreateCommentEvent) {
        when (event) {
            is CreateCommentEvent.TextChanged -> {
                val newText = if (event.text.length > 500) event.text.take(500) else event.text
                _state.update { it.copy(text = newText, error = null) }
            }
            CreateCommentEvent.CancelTapped -> emit(CreateCommentNavEvent.Dismiss)
            CreateCommentEvent.SendTapped -> submit()
        }
    }

    fun createComment() {
        submit()
    }

    private fun submit() {
        val text = _state.value.text
        if (text.isBlank() || loopId.isBlank() || text.length > 500) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val userCode = currentUserCode()
            if (userCode.isNullOrBlank()) {
                _state.update { it.copy(isSubmitting = false, error = "Sign in to comment") }
                return@launch
            }
            val result = loopCommentRepository.createComment(
                loopId = loopId,
                parentId = parentCommentId.ifBlank { null },
                text = text,
                userId = userCode,
            )
            _state.update { it.copy(isSubmitting = false) }
            result.fold(
                onSuccess = {
                    awardAccessComment()
                    emit(CreateCommentNavEvent.Dismiss)
                },
                onFailure = { e ->
                    _state.update { it.copy(error = e.message ?: "Failed to send") }
                    emit(CreateCommentNavEvent.OnError(e.message ?: "Failed to send"))
                },
            )
        }
    }

    /**
     * Awards the `ACCESS_COMMENT` Klouders point. The write runs in its
     * own coroutine so the dialog is dismissed without waiting for it,
     * and a failed award never surfaces as a screen error.
     */
    private fun awardAccessComment() {
        viewModelScope.launch {
            gamificationRepository.addPoints(
                action = ACTION_ACCESS_COMMENT,
                points = 3,
                referenceId = loopId,
            )
        }
    }

    /**
     * The signed-in user's `codeUser`, or `null` when the session is
     * anonymous or carries no code.
     */
    private suspend fun currentUserCode(): String? =
        sessionRepository.data
            .filterNot { it is SessionState.Loading }
            .map { (it as? SessionState.Authenticated)?.user?.userCode }
            .first()
            ?.takeIf { it.isNotBlank() }

    fun dismiss() {
        emit(CreateCommentNavEvent.Dismiss)
    }

    val maxCharacterCount: Int = 500

    val characterCountInfo: String
        get() = "${maxCharacterCount - _state.value.text.length}/${maxCharacterCount}"

    val btnPostEnabled: Boolean
        get() = _state.value.text.isNotBlank() && _state.value.text.length > 0 && _state.value.text.length <= maxCharacterCount && !_state.value.isSubmitting

    var commentText: String
        get() = _state.value.text
        set(value) {
            val newText = if (value.length > 500) value.take(500) else value
            _state.update { it.copy(text = newText, error = null) }
        }

    val isLoading: Boolean
        get() = _state.value.isSubmitting

    private fun emit(event: CreateCommentNavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }

    companion object {
        /** iOS `GamificationAction.accessComment`. */
        const val ACTION_ACCESS_COMMENT: String = "ACCESS_COMMENT"
    }
}
