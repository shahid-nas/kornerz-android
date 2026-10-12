package com.kornerz.feature.videocall

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.videocall.CameraPosition
import com.kornerz.core.network.videocall.VonageSessionController
import com.kornerz.feature.videocall.data.VideoCallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * Hilt-managed ViewModel for the video call screen. Owns the
 * `VideoCallState` UDF state and a `Channel<VideoCallNavEvent>` for
 * side-channel navigation (architecture §2.4).
 *
 * `sessionId` is read from `SavedStateHandle["sessionId"]` (set by
 * the `composable("videocall/{sessionId}")` route factory in
 * `VideocallRoute.kt`). On `init`, `VideoCallRepository.connect` is
 * collected into `_state.callStatus`.
 */
@HiltViewModel
class VideoCallViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val videoCallRepository: VideoCallRepository,
    private val vonageSessionController: VonageSessionController,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>(ARG_SESSION_ID).orEmpty()

    private val _state = MutableStateFlow(VideoCallState())
    val state: StateFlow<VideoCallState> = _state.asStateFlow()

    private val _navEvents = Channel<VideoCallNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<VideoCallNavEvent> = _navEvents.receiveAsFlow()

    private var connectJob: Job? = null

    init {
        if (sessionId.isNotBlank()) {
            vonageSessionController.currentContext = appContext
            connect(sessionId)
        } else {
            _state.update { it.copy(callStatus = AsyncValue.Error(IllegalArgumentException("Missing sessionId"))) }
        }
    }

    fun onEvent(event: VideoCallEvent) {
        when (event) {
            VideoCallEvent.ToggleAudio -> {
                val next = !_state.value.isAudioOn
                _state.update { it.copy(isAudioOn = next) }
                vonageSessionController.setPublishAudio(next)
            }
            VideoCallEvent.ToggleVideo -> {
                val next = !_state.value.isVideoOn
                _state.update { it.copy(isVideoOn = next) }
                vonageSessionController.setPublishVideo(next)
            }
            VideoCallEvent.FlipCamera -> {
                vonageSessionController.setCameraPosition(CameraPosition.BACK)
            }
            VideoCallEvent.ToggleHandRaised -> {
                _state.update { it.copy(isHandRaised = !it.isHandRaised) }
            }
            VideoCallEvent.EndCall -> {
                videoCallRepository.disconnect()
                viewModelScope.launch { _navEvents.send(VideoCallNavEvent.NavigateBack) }
            }
            VideoCallEvent.Retry -> {
                _state.update { it.copy(callStatus = AsyncValue.Loading) }
                connect(sessionId)
            }
        }
    }

    private fun connect(sessionId: String) {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            try {
                videoCallRepository.connect(sessionId).collect { callSession ->
                    _state.update { it.copy(callStatus = AsyncValue.Success(callSession)) }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(callStatus = AsyncValue.Error(t)) }
            }
        }
    }

    companion object {
        const val ARG_SESSION_ID: String = "sessionId"
    }
}