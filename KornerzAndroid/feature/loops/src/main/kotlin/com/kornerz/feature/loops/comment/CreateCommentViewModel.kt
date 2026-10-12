package com.kornerz.feature.loops.comment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.feature.loops.LoopsRoute
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
class CreateCommentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loopCommentRepository: LoopCommentRepository,
) : ViewModel() {

    val loopId: String = savedStateHandle.get<String>(LoopsRoute.Comment.ARG_LOOP_ID).orEmpty()

    private val _state = MutableStateFlow(CreateCommentState())
    val state: StateFlow<CreateCommentState> = _state.asStateFlow()

    private val _navEvents = Channel<CreateCommentNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<CreateCommentNavEvent> = _navEvents.receiveAsFlow()

    // Placeholder signed-in user id; P7 wires this to FirebaseAuth.
    private val currentUserId: String = ""

    fun onEvent(event: CreateCommentEvent) {
        when (event) {
            is CreateCommentEvent.TextChanged -> _state.update { it.copy(text = event.text, error = null) }
            CreateCommentEvent.CancelTapped -> emit(CreateCommentNavEvent.Dismiss)
            CreateCommentEvent.SendTapped -> submit()
        }
    }

    private fun submit() {
        val text = _state.value.text
        if (text.isBlank() || loopId.isBlank() || currentUserId.isBlank()) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = loopCommentRepository.createComment(
                loopId = loopId,
                parentId = null,
                text = text,
                userId = currentUserId,
            )
            _state.update { it.copy(isSubmitting = false) }
            result.fold(
                onSuccess = { emit(CreateCommentNavEvent.Dismiss) },
                onFailure = { e ->
                    _state.update { it.copy(error = e.message ?: "Failed to send") }
                    emit(CreateCommentNavEvent.OnError(e.message ?: "Failed to send"))
                },
            )
        }
    }

    private fun emit(event: CreateCommentNavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }
}
