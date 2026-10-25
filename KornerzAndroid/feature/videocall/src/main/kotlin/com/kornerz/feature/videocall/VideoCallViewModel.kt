package com.kornerz.feature.videocall

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.gamification.GamificationRepository
import com.kornerz.core.network.videocall.PublisherInfo
import com.kornerz.core.network.videocall.RemoteSubscriber
import com.kornerz.core.network.videocall.VonageSessionController
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.videocall.data.VideoCallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hilt-managed ViewModel for the video call screen. Owns the
 * `VideoCallState` UDF state and a `Channel<VideoCallNavEvent>` for
 * side-channel navigation (architecture §2.4).
 *
 * `sessionId` is read from `SavedStateHandle["sessionId"]` (set by the
 * `composable("videocall/{sessionId}")` route factory in
 * `VideocallRoute.kt`). On `init`, `VideoCallRepository.connect` is
 * collected into `_state.callStatus`.
 *
 * The view model also mirrors the iOS `VideoCallViewModel` surface:
 * the live publisher handle, the remote participants (parsed from the
 * published identity payloads), the community name shown in the top
 * toolbar, and the raise-hand signalling.
 */
@HiltViewModel
class VideoCallViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val videoCallRepository: VideoCallRepository,
    private val vonageSessionController: VonageSessionController,
    private val sessionRepository: SessionRepository,
    private val gamificationRepository: GamificationRepository,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>(ARG_SESSION_ID).orEmpty()

    /** Community the call belongs to — used for the title and the award. */
    private val communityId: String = savedStateHandle.get<String>(ARG_COMMUNITY_ID).orEmpty()

    private val _state = MutableStateFlow(
        VideoCallState(
            communityName = savedStateHandle.get<String>(ARG_COMMUNITY_NAME),
        ),
    )
    val state: StateFlow<VideoCallState> = _state.asStateFlow()

    private val _navEvents = Channel<VideoCallNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<VideoCallNavEvent> = _navEvents.receiveAsFlow()

    private var connectJob: Job? = null

    /** Guards the one-time sync of the toggle flags with the publisher. */
    private var publisherSynced = false

    /** Whether the camera/microphone runtime permissions are in place. */
    private var permissionsGranted = false

    /**
     * Whether the published identity has been resolved. The session is
     * only opened once it is known, because the identity travels with
     * the stream name — a publisher created before the identity is
     * known would publish an empty name and the raise-hand signal would
     * carry no `userId`.
     */
    private var publisherInfoReady = false

    /** Guards the one-shot Klouders award for joining a nook. */
    private var hasAwardedAccessNook = false

    init {
        observeSession()
        observeParticipants()
        if (sessionId.isBlank()) {
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
                vonageSessionController.flipCamera()
            }
            VideoCallEvent.ToggleHandRaised -> {
                val next = !_state.value.isHandRaised
                _state.update { it.copy(isHandRaised = next) }
                vonageSessionController.sendRaiseHandSignal(next)
            }
            VideoCallEvent.EndCall -> {
                videoCallRepository.disconnect()
                viewModelScope.launch { _navEvents.send(VideoCallNavEvent.NavigateBack) }
            }
            VideoCallEvent.Retry -> {
                if (permissionsGranted) {
                    _state.update { it.copy(callStatus = AsyncValue.Loading) }
                    maybeConnect()
                }
            }
            is VideoCallEvent.PermissionsResult -> {
                permissionsGranted = event.granted
                if (event.granted) maybeConnect()
            }
        }
    }

    /**
     * Publishes the local identity alongside the stream so remote peers
     * can label this participant (iOS `PublisherModel.currentUserInfo`),
     * and keeps the local self-view avatar in sync.
     */
    private fun observeSession() {
        viewModelScope.launch {
            val user = (sessionRepository.data.first() as? SessionState.Authenticated)?.user
            val info = user?.let {
                PublisherInfo(
                    userName = it.nickname.takeIf { name -> name.isNotBlank() } ?: it.displayName,
                    userId = it.userCode.orEmpty(),
                    // iOS publishes `loggedInUser?.thumnailURL` here.
                    image = it.thumbnailURL?.takeIf { url -> url.isNotBlank() },
                )
            }
            vonageSessionController.setPublisherInfo(info)
            publisherInfoReady = true
            _state.update { it.copy(localThumbnailUrl = info?.image) }
            maybeConnect()
        }
    }

    /**
     * Mirrors the live publisher and the remote participants into the
     * UI state. The publisher's own audio/video flags seed the toggle
     * state once, the first time the publisher appears.
     */
    private fun observeParticipants() {
        viewModelScope.launch {
            vonageSessionController.publishers.collect { publisher ->
                if (publisher != null && !publisherSynced) {
                    publisherSynced = true
                    _state.update {
                        it.copy(
                            isVideoOn = publisher.publishVideo,
                            isAudioOn = publisher.publishAudio,
                        )
                    }
                }
                _state.update { it.copy(publisher = publisher) }
            }
        }
        viewModelScope.launch {
            vonageSessionController.subscribers.collect { subscribers ->
                _state.update { state ->
                    state.copy(participants = subscribers.map { it.toParticipant() })
                }
            }
        }
    }

    /**
     * Opens the session once both preconditions hold: the runtime
     * permissions are granted and the published identity is known.
     */
    private fun maybeConnect() {
        if (!permissionsGranted || !publisherInfoReady) return
        if (sessionId.isBlank()) return
        vonageSessionController.currentContext = appContext
        awardAccessNookOnce()
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

    /**
     * Awards the Klouders points for joining a nook. Mirrors iOS
     * `VideoCallViewModel.addGamificationPoints()` — 2 points, action
     * `ACCESS_NOOK`, referenced by the community id. The write is a
     * no-op for an anonymous session and never blocks the call.
     */
    private fun awardAccessNookOnce() {
        if (hasAwardedAccessNook) return
        hasAwardedAccessNook = true
        viewModelScope.launch {
            gamificationRepository.addPoints(
                action = ACTION_ACCESS_NOOK,
                points = 2,
                referenceId = communityId,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        videoCallRepository.disconnect()
    }

    companion object {
        const val ARG_SESSION_ID: String = "sessionId"
        const val ARG_COMMUNITY_NAME: String = "communityName"
        const val ARG_COMMUNITY_ID: String = "communityId"

        /** iOS `GamificationAction.accessNook`. */
        const val ACTION_ACCESS_NOOK: String = "ACCESS_NOOK"
    }
}

/**
 * Projects a live remote subscriber onto the UI model.
 */
private fun RemoteSubscriber.toParticipant(): VideoCallParticipant = VideoCallParticipant(
    streamId = streamId,
    userName = displayName,
    thumbnailUrl = thumbnailUrl,
    hasVideo = hasVideo,
    raisedHand = raisedHand,
    subscriber = subscriber,
)
