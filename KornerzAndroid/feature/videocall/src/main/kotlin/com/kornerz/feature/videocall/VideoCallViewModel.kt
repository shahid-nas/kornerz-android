package com.kornerz.feature.videocall

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.gamification.GamificationRepository
import com.kornerz.core.network.videocall.PublisherInfo
import com.kornerz.core.network.videocall.RemoteSubscriber
import com.kornerz.core.network.videocall.VideoCallSessionEvent
import com.kornerz.core.network.videocall.VonageSessionController
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.videocall.data.VideoCallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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
     * The permission grant, as a flow.
     *
     * The session used to be opened from two independent places — the
     * permission result and the identity resolution — and each of them
     * tore the previous attempt down and started a new one. Two
     * sessions in the same room meant the server could deliver the
     * peer's stream to the attempt the app had already abandoned while
     * the app was listening on the other one, so the peer never showed
     * up. The two preconditions are now combined into a single trigger
     * that opens exactly one session.
     */
    private val permissionsGrantedState = MutableStateFlow(false)

    /** Whether the published identity has been resolved, as a flow. */
    private val publisherInfoReadyState = MutableStateFlow(false)

    /**
     * Whether the published identity has been resolved. The session is
     * only opened once it is known, because the identity travels with
     * the stream name — a publisher created before the identity is
     * known would publish an empty name and the raise-hand signal would
     * carry no `userId`.
     */
    private var publisherInfoReady = false

    /** Whether a session has already been opened for this call. */
    private var connectStarted = false

    /** Guards the one-shot Klouders award for joining a nook. */
    private var hasAwardedAccessNook = false

    /**
     * Set once the user ends the call, so the session's own
     * "disconnected" callback does not navigate back a second time.
     */
    private var hasEnded = false

    /**
     * Set once the session reported a failure. The repository's
     * publisher/subscriber `combine` keeps emitting ("Connecting"
     * after the session was torn down) and would otherwise overwrite
     * the error state with a success — the user would be back to
     * staring at a spinner with no explanation.
     */
    private var hasFailed = false

    init {
        Log.i(TAG, "VideoCallViewModel created: sessionId='$sessionId' communityId='$communityId'")
        observeSession()
        observeParticipants()
        observeSessionEvents()
        observePreconditions()
        if (sessionId.isBlank()) {
            _state.update { it.copy(callStatus = AsyncValue.Error(IllegalArgumentException("Missing sessionId"))) }
        }
    }

    /**
     * Re-creates the Vonage session with the same token and room.
     *
     * Debug affordance (the diagnostics panel's "reconnect" button):
     * a fresh connect makes the server send the room's current streams
     * again, which is the only way to recover a "stream created" event
     * that was lost while the signalling channel was flapping.
     */
    fun reconnectSession() {
        Log.i(TAG, "reconnectSession requested")
        vonageSessionController.reconnectNow(reason = "manual")
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
                hasEnded = true
                videoCallRepository.disconnect()
                viewModelScope.launch { _navEvents.send(VideoCallNavEvent.NavigateBack) }
            }
            VideoCallEvent.Retry -> {
                if (permissionsGranted) {
                    hasFailed = false
                    connectStarted = false
                    // The previous attempt's session has to go before a
                    // new one is opened — otherwise two sessions for the
                    // same room are live at the same time.
                    videoCallRepository.disconnect()
                    _state.update { it.copy(callStatus = AsyncValue.Loading) }
                    startConnect()
                }
            }
            is VideoCallEvent.PermissionsResult -> {
                permissionsGranted = event.granted
                Log.i(TAG, "PermissionsResult granted=${event.granted}")
                permissionsGrantedState.value = event.granted
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
                    userName = it.nickname.takeIf { name -> name.isNotBlank() }
                        ?: it.displayName.takeIf { name -> name.isNotBlank() }
                        ?: Build.MODEL,
                    userId = it.userCode.orEmpty(),
                    // iOS publishes `loggedInUser?.thumnailURL` here.
                    image = it.thumbnailURL?.takeIf { url -> url.isNotBlank() },
                )
            } ?: PublisherInfo(
                // iOS falls back to `UIDevice.current.name` when the
                // user is not signed in — the stream name still has to
                // parse on the remote side.
                userName = Build.MODEL,
                userId = "",
                image = null,
            )
            vonageSessionController.setPublisherInfo(info)
            publisherInfoReady = true
            Log.i(TAG, "Publisher identity resolved: ${info.toJson()}")
            _state.update { it.copy(localThumbnailUrl = info.image) }
            publisherInfoReadyState.value = true
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
                Log.i(TAG, "publishers → ${publisher?.stream?.streamId ?: "null"}")
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
                Log.i(
                    TAG,
                    "subscribers → count=${subscribers.size} " +
                        "streamIds=${subscribers.map { it.streamId }} " +
                        "names=${subscribers.map { it.displayName }}",
                )
                _state.update { state ->
                    state.copy(participants = subscribers.map { it.toParticipant() })
                }
            }
        }
        viewModelScope.launch {
            vonageSessionController.diagnostics.collect { diagnostics ->
                _state.update { it.copy(diagnostics = diagnostics) }
            }
        }
    }

    /**
     * Reacts to the session-level failures the Vonage SDK reports.
     *
     * iOS handles the same conditions through `OTSessionDelegate`:
     * `session(_:didFailWithError:)` fails the call (and iOS raises an
     * alert), and `sessionDidDisconnect(_:)` calls `onClose?()`,
     * dismissing the call screen.
     *
     * Without this the Android screen used to keep showing "Waiting
     * for others to join" for a call whose session had already died.
     */
    private fun observeSessionEvents() {
        viewModelScope.launch {
            vonageSessionController.sessionEvents.collect { event ->
                when (event) {
                    is VideoCallSessionEvent.Failed -> {
                        hasFailed = true
                        _state.update { it.copy(callStatus = AsyncValue.Error(event.error)) }
                    }

                    VideoCallSessionEvent.Disconnected -> {
                        if (!hasEnded) {
                            _navEvents.send(VideoCallNavEvent.NavigateBack)
                        }
                    }
                }
            }
        }
    }

    /**
     * Combines the two preconditions of a call — the runtime
     * permissions and the published identity — into a single trigger
     * that opens **one** session.
     *
     * iOS opens exactly one `OTSession` per call (`VideoCallViewModel
     * .connectToOpenTok`). Android used to open one per precondition,
     * and each attempt tore the previous one down, which left the
     * device with two sessions in the same room: the server could
     * deliver the peer's stream to the attempt the app had already
     * abandoned, and the app (listening on the newer session) never
     * saw the peer. That is exactly the "iOS sees Android, Android
     * sees nobody" symptom.
     */
    private fun observePreconditions() {
        viewModelScope.launch {
            combine(
                permissionsGrantedState,
                publisherInfoReadyState,
            ) { granted, identityReady -> granted && identityReady }
                .distinctUntilChanged()
                .filter { it }
                .collect { startConnect() }
        }
    }

    /**
     * The `Context` the SDK builds the `Publisher` / `Subscriber` with.
     *
     * The call screen hands over the **Activity** context: the SDK's
     * renderer is a `SurfaceView`, which resolves its display and its
     * window from the context it was created with. With the
     * application context the surface is bound to the default display
     * instead of the call screen's window, and the video ends up drawn
     * at the wrong place and at the wrong size.
     *
     * The application context is kept as a fallback for the case where
     * the screen has not been composed yet.
     */
    fun setCallContext(context: Context) {
        vonageSessionController.currentContext = context
    }

    /**
     * Opens the session. Idempotent — the second call is ignored so a
     * late precondition can never start a second session.
     */
    private fun startConnect() {
        if (connectStarted) {
            Log.i(TAG, "startConnect: ignored — a session is already open")
            return
        }
        if (sessionId.isBlank()) return
        connectStarted = true
        // The Activity context is preferred — see [setCallContext]. The
        // application context is only a fallback for the case where the
        // call screen was not composed before the session was opened.
        if (vonageSessionController.currentContext == null) {
            vonageSessionController.currentContext = appContext
        }
        awardAccessNookOnce()
        Log.i(
            TAG,
            "startConnect: sessionId='$sessionId' permissions=$permissionsGranted identity=$publisherInfoReady",
        )
        connectJob = viewModelScope.launch {
            try {
                videoCallRepository.connect(sessionId).collect { callSession ->
                    if (!hasFailed) {
                        Log.i(
                            TAG,
                            "callStatus → ${callSession.status} participants=${callSession.remoteSubscribers.size}",
                        )
                        _state.update { it.copy(callStatus = AsyncValue.Success(callSession)) }
                    }
                }
            } catch (e: CancellationException) {
                // A superseded attempt (retry, or the screen going away)
                // is not a call failure — the next attempt owns the
                // state from here.
                throw e
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

        private const val TAG = "VideoCallVM"
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
    isConnected = connected,
)
