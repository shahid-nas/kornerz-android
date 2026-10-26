package com.kornerz.core.network.videocall

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.opentok.android.BaseVideoRenderer
import com.opentok.android.Connection
import com.opentok.android.OpentokError
import com.opentok.android.Publisher
import com.opentok.android.PublisherKit
import com.opentok.android.Session
import com.opentok.android.Stream
import com.opentok.android.Subscriber
import com.opentok.android.SubscriberKit
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the OpenTok Android SDK `Session` / `Publisher` / `Subscriber`
 * lifecycle behind a small project-specific surface so the rest of
 * the app never sees the SDK (arch §2.5).
 *
 * Lifecycle (mirrors iOS `sessionDidConnect` / `streamCreated` /
 * `streamDestroyed`):
 *  1. `connect(token, sessionId)` — build the `Session`, register a
 *     `SessionListener`, then `session.connect(token)`.
 *  2. On `onConnected` — build a `Publisher` and `session.publish(it)`.
 *  3. On `onStreamReceived` — build a `Subscriber` and
 *     `session.subscribe(it)`.
 *  4. On `onStreamDropped` — remove the matching subscriber.
 *  5. `disconnect()` tears down publisher, subscribers, session.
 *
 * The `Publisher` is exposed as a `StateFlow<Publisher?>` (non-null
 * once the session reports connected). Subscribers are exposed as a
 * `StateFlow<List<RemoteSubscriber>>`. Both are single-slot / list slots;
 * `VonageSessionController` is a `@Singleton` and a new call
 * reconnects from scratch (idempotent — `connect` / `disconnect`
 * cancel the previous run).
 *
 * **Raise-hand signalling** mirrors the iOS `MeetingSignalManager`
 * contract: the signal type is `Raise_Hand_Active` / `Raise_Hand_InActive`
 * and the body is `{"type":…,"data":{"userId":…,"userName":…}}`. The
 * receiver resolves the `userId` against each remote publisher's
 * `PublisherInfo` (parsed from the stream name) so the badge lands on
 * the right tile.
 *
 * **Failure reporting** mirrors iOS: every SDK failure that would
 * otherwise leave the screen spinning on "Waiting for others to join"
 * is republished on [sessionEvents] as a [VideoCallSessionEvent.Failed]
 * (or [VideoCallSessionEvent.Disconnected] when the room goes away).
 */
@Singleton
class VonageSessionController @Inject constructor() {

    private val _publishers = MutableStateFlow<Publisher?>(null)
    val publishers: StateFlow<Publisher?> = _publishers.asStateFlow()

    private val _subscribers = MutableStateFlow<List<RemoteSubscriber>>(emptyList())
    val subscribers: StateFlow<List<RemoteSubscriber>> = _subscribers.asStateFlow()

    /**
     * Session/publisher/subscriber failures the call screen has to
     * react to. Mirrors iOS `OTSessionDelegate.didFailWithError` /
     * `sessionDidDisconnect`.
     */
    private val _sessionEvents = MutableSharedFlow<VideoCallSessionEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val sessionEvents: SharedFlow<VideoCallSessionEvent> = _sessionEvents.asSharedFlow()

    /**
     * Live snapshot of the session's state, rendered on the call screen
     * so the reason a device does not see its peers is visible without
     * logcat.
     */
    private val _diagnostics = MutableStateFlow(VideoCallDiagnostics())
    val diagnostics: StateFlow<VideoCallDiagnostics> = _diagnostics.asStateFlow()

    /** Peer connections negotiated with the other participants. */
    private val peerConnections = mutableSetOf<String>()

    private var session: Session? = null
    private var publisher: Publisher? = null

    /**
     * Whether the SDK's session is currently paused.
     *
     * The SDK renders every video through a `TextureView`, and a
     * `TextureView` only draws frames once it has been resumed. The
     * SDK resumes a renderer exclusively from `PublisherKit.onResume()`
     * / `SubscriberKit.onResume()`, and those are driven by
     * `session.onResume()` **and are guarded by the subscriber's own
     * `isPaused` flag** — so a subscriber that was never paused (which
     * is every subscriber of a call that was opened while the app was
     * already in the foreground) is never resumed by the session, and
     * its surface stays black while the media flows.
     *
     * The controller therefore resumes the renderers itself, and this
     * flag makes sure the session's own pause/resume cycle stays
     * authoritative: a renderer is only resumed while the session is
     * not paused.
     */
    private var sessionPaused = false

    /**
     * The thread the Vonage SDK's callbacks are delivered on.
     *
     * The SDK builds its `Session` with `new Handler(Looper.myLooper())`
     * — i.e. **the Looper of whatever thread constructs the session** —
     * and posts *every* callback (`onConnected`, `onStreamReceived`,
     * `onStreamDropped`, …) to that one queue.
     *
     * When the session was built on the main thread (which is what
     * happened while the repository drove the call from
     * `viewModelScope`), that queue *was* the UI thread. The server
     * pushes the peers' pre-existing streams as part of the connect
     * handshake, so those runnables land in the queue while
     * `onConnected` is still running — and `onConnected` used to block
     * that very thread for tens of seconds while it fetched the
     * session capabilities and published the local stream. The queue
     * is strictly serial, so the pending `onStreamReceived` callbacks
     * could not be dispatched until long after the native layer had
     * moved on, and the remote participants never appeared: the
     * Android client published its own stream (so iOS saw it) but
     * never received the peers' streams (so the Android grid stayed
     * on "waiting for others to join").
     *
     * A dedicated thread with its own Looper keeps the SDK's callback
     * queue off the UI thread, so a slow callback can never stall the
     * delivery of the stream events.
     */
    private var sdkThread: HandlerThread? = null

    /** The [Handler] bound to [sdkThread] — the SDK's callback queue. */
    private var sdkHandler: Handler? = null

    /**
     * The main thread's handler — used to touch the SDK's OpenGL
     * surfaces, which have to be driven from the thread that owns
     * them.
     */
    private val mainHandler: Handler? by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Streams that arrived before the controller was ready to turn them
     * into subscribers.
     *
     * The server can push a peer's stream *before* the local session
     * has finished connecting. Such an event must not be dropped —
     * it is the only notification this client will ever get for that
     * peer — so it is parked here and drained as soon as the session
     * is ready.
     */
    private val pendingStreams = mutableListOf<Stream>()

    /**
     * Whether a retry for the parked streams is already queued. Keeps
     * the retry from piling up when several streams fail at once.
     */
    private var pendingRetryScheduled = false

    /**
     * Whether the SDK reports the session as connected. Used to tell a
     * fatal session failure (the call never came up) apart from a
     * transient error raised while the call is live — only the former
     * may tear the screen down, mirroring iOS, whose
     * `session(_:didFailWithError:)` only logs.
     */
    private var sessionConnected = false

    /** Identity published alongside the local stream (iOS parity). */
    private var publisherInfo: PublisherInfo? = null

    /**
     * The token and the room of the live session, kept so the session
     * can be re-created (see [reconnectNow]).
     *
     * The token is a credential: it is never logged and never leaves
     * the process.
     */
    private var sessionToken: String? = null
    private var sessionRoom: String? = null

    /** When the last self-initiated re-connect happened (epoch millis). */
    private var lastReconnectAt = 0L

    /** The lens the local publisher is currently streaming from. */
    private val cameraPosition = MutableStateFlow(CameraPosition.FRONT)

    private val sessionListener = object : Session.SessionListener {
        override fun onConnected(session: Session) {
            Log.i(TAG, "onConnected sessionId=${session.sessionId}")
            // A newer session already replaced us — do not resurrect the
            // stale one (iOS keeps a single `OTSession` alive for the
            // lifetime of the view model).
            if (this@VonageSessionController.session != null &&
                this@VonageSessionController.session !== session
            ) {
                Log.w(TAG, "onConnected for a stale session — ignoring")
                return
            }
            this@VonageSessionController.session = session
            this@VonageSessionController.sessionConnected = true
            // The server tells the client what it is allowed to do in
            // this session. `canSubscribe == false` means the token
            // (role `publisheronly`, or a scope without
            // `stream.subscribe`) forbids receiving other clients'
            // streams — the client can publish and stays invisible to
            // its peers, which is exactly the "iOS sees Android,
            // Android sees nobody" symptom.
            //
            // The capability message only arrives once the server has
            // finished negotiating the session, so `getCapabilities()`
            // blocks until then. It used to be called *here*, on the
            // SDK's callback thread — which stalled the delivery of the
            // peers' stream events for as long as it took (tens of
            // seconds in the field) and the remote participants never
            // showed up. The capabilities are informational only, so
            // they are read on a background thread instead and the
            // callback returns immediately.
            readCapabilitiesOffThread(session)
            // Publish the local stream without blocking the callback
            // thread: `session.publish()` opens the camera and the
            // microphone and takes several seconds. iOS publishes
            // straight from `sessionDidConnect` because `OTSession`
            // delivers its callbacks on its own queue — Android needs
            // the same property, which the dedicated SDK thread gives
            // us, so the publish is handed to it instead of blocking
            // the queue it was invoked from.
            publishLocalStream(session)
            drainPendingStreams(session)
        }

        override fun onDisconnected(session: Session) {
            Log.i(TAG, "onDisconnected sessionId=${session.sessionId}")
            // Only the session that is currently live may tear the
            // controller down — a stale session's disconnect callback
            // must not wipe the state of the call that replaced it.
            if (this@VonageSessionController.session !== session) {
                Log.w(TAG, "onDisconnected for a stale session — ignoring")
                return
            }
            this@VonageSessionController.sessionConnected = false
            cleanup()
            updateDiagnostics(lastCallback = "onDisconnected")
            _sessionEvents.tryEmit(VideoCallSessionEvent.Disconnected)
        }

        override fun onStreamReceived(session: Session, stream: Stream) {
            Log.i(
                TAG,
                "onStreamReceived: streamId=${stream.streamId} hasVideo=${stream.hasVideo()} " +
                    "hasAudio=${stream.hasAudio()} name='${stream.name}' " +
                    "connection=${stream.connection?.connectionId}",
            )
            // The stale-session guard used to drop this callback
            // whenever the controller's `session` field did not point
            // at the session that raised the event. That field is
            // nulled by `cleanup()` and replaced by `connect()`, so a
            // stream event that arrived while the controller was
            // re-connecting — or before `onConnected` had assigned the
            // field — was silently thrown away. It is the *only*
            // notification this client ever gets for that peer, so
            // dropping it left the peer invisible for the rest of the
            // call. The event is now parked and replayed instead.
            if (this@VonageSessionController.session != null &&
                this@VonageSessionController.session !== session
            ) {
                Log.w(TAG, "onStreamReceived for a stale session — parking the stream")
                parkPendingStream(stream)
                return
            }
            if (this@VonageSessionController.session == null) {
                Log.w(TAG, "onStreamReceived before the session was assigned — parking the stream")
                parkPendingStream(stream)
                schedulePendingStreamRetry()
                return
            }
            subscribeToStream(session, stream)
        }

        override fun onStreamDropped(session: Session, stream: Stream) {
            Log.i(TAG, "onStreamDropped streamId=${stream.streamId}")
            // The peer left the room — the only case where the tile
            // really has to go (iOS `streamDestroyed`).
            _subscribers.value = _subscribers.value.filterNot { it.streamId == stream.streamId }
            pendingStreams.removeAll { it.streamId == stream.streamId }
            updateDiagnostics(
                subscribers = subscribersSummary(),
                lastCallback = "onStreamDropped(${stream.streamId})",
            )
        }

        override fun onError(session: Session, opentokError: OpentokError) {
            Log.e(TAG, "Session error: ${opentokError.message}")
            // A stale session's error must not fail the call that
            // replaced it.
            if (this@VonageSessionController.session !== session) {
                Log.w(TAG, "Session error for a stale session — ignoring")
                return
            }
            updateDiagnostics(lastCallback = "onError(${opentokError.message})")
            // iOS only logs here (`session(_:didFailWithError:)`) and
            // keeps the call alive. Tearing the whole session down on
            // any SDK error is what used to blank the screen: a
            // transient media error wiped the publisher *and* every
            // subscriber, leaving an empty call with no way back.
            // Only an error raised before the session ever came up is
            // fatal.
            if (sessionConnected) {
                Log.w(TAG, "Session error while connected — keeping the call alive")
                return
            }
            _sessionEvents.tryEmit(
                VideoCallSessionEvent.Failed(
                    VideoCallError.SessionFailed(opentokError.message.orEmpty()),
                ),
            )
        }
    }

    /**
     * iOS `MeetingSignalManager` signal types. The raw values are the
     * cross-platform wire format — they must not be renamed.
     */
    private object SignalTypes {
        const val RAISE_HAND_ACTIVE = "Raise_Hand_Active"
        const val RAISE_HAND_INACTIVE = "Raise_Hand_InActive"
    }

    private val signalListener = object : Session.SignalListener {
        override fun onSignalReceived(
            session: Session,
            type: String,
            data: String,
            connection: Connection,
        ) {
            applyRaiseHandSignal(type, data)
        }
    }

    /**
     * Peer-connection lifecycle. `onConnectionCreated` fires once the
     * SDK has negotiated a media connection with another participant —
     * it is the signal that the room is right and the two devices can
     * actually see each other. A room can be joined successfully while
     * no peer connection is ever created, which is exactly the
     * "connected but no subscribers" case.
     */
    private val connectionListener = object : Session.ConnectionListener {
        override fun onConnectionCreated(session: Session, connection: Connection) {
            peerConnections += connection.connectionId
            Log.i(TAG, "onConnectionCreated: connectionId=${connection.connectionId} peers=${peerConnections.size}")
            updateDiagnostics(lastCallback = "onConnectionCreated(${connection.connectionId})")
        }

        override fun onConnectionDestroyed(session: Session, connection: Connection) {
            peerConnections -= connection.connectionId
            Log.i(TAG, "onConnectionDestroyed: connectionId=${connection.connectionId} peers=${peerConnections.size}")
            updateDiagnostics(lastCallback = "onConnectionDestroyed(${connection.connectionId})")
        }
    }

    private val reconnectionListener = object : Session.ReconnectionListener {
        override fun onReconnecting(session: Session) {
            Log.w(TAG, "onReconnecting — the media server dropped the session")
            updateDiagnostics(connection = "reconnecting")
        }

        override fun onReconnected(session: Session) {
            Log.i(TAG, "onReconnected")
            updateDiagnostics(connection = "reconnected", lastCallback = "onReconnected")
            // The SDK drops the subscriber connections while the
            // session is down and does **not** re-deliver
            // `onStreamReceived` for the streams that already existed
            // — the peers stay in the room, the tiles stay empty.
            // Re-subscribe every stream we know about so the grid
            // comes back (iOS keeps its `OTSubscriber` objects bound to
            // the session across a reconnect, which is the behaviour
            // being mirrored here).
            resubscribeKnownStreams()
            // A reconnect is also the moment a "stream created" event
            // can be lost for good: the server pushes the event over
            // the signalling channel, and a channel that drops right
            // after the push never replays it. Re-creating the session
            // makes the server send the room's current streams again
            // (that is how a joining client learns about the peers that
            // are already in the room).
            if (_subscribers.value.isEmpty()) {
                reconnectNow(reason = "reconnected without subscribers")
            }
        }

        /**
         * Re-subscribes to every remote stream the controller knows
         * about. Only the subscribers the SDK reported as dropped are
         * re-subscribed — re-subscribing a live one would ask the SDK
         * for a subscription it already holds.
         */
        private fun resubscribeKnownStreams() {
            val live = this@VonageSessionController.session ?: return
            val dropped = _subscribers.value.filter { !it.connected }
            if (dropped.isEmpty()) {
                Log.i(TAG, "onReconnected: no dropped subscribers to restore")
                return
            }
            for (remote in dropped) {
                runCatching { live.subscribe(remote.subscriber) }
                    .onSuccess {
                        Log.i(TAG, "onReconnected: re-subscribed streamId=${remote.streamId}")
                        updateSubscriber(remote.subscriber) { it.copy(connected = true) }
                    }
                    .onFailure { t ->
                        Log.e(TAG, "onReconnected: re-subscribe failed for ${remote.streamId}: ${t.message}")
                    }
            }
        }
    }

    private val publisherListener = object : PublisherKit.PublisherListener {
        override fun onError(publisherKit: PublisherKit, opentokError: OpentokError) {
            // iOS surfaces this as `loadingState = .failed` + an alert.
            // Swallowing it here is what used to leave the Android
            // screen spinning on "Waiting for others to join" while the
            // local stream was never published.
            Log.e(TAG, "Publisher failed: ${opentokError.message}")
            _sessionEvents.tryEmit(
                VideoCallSessionEvent.Failed(
                    VideoCallError.PublisherFailed(opentokError.message.orEmpty()),
                ),
            )
        }

        override fun onStreamCreated(publisherKit: PublisherKit, stream: Stream) {
            Log.i(TAG, "Local stream created: ${stream.streamId}")
        }

        override fun onStreamDestroyed(publisherKit: PublisherKit, stream: Stream) {
            Log.i(TAG, "Local stream destroyed: ${stream.streamId}")
        }
    }

    private val subscriberListener = object : SubscriberKit.SubscriberListener {
        override fun onConnected(subscriberKit: SubscriberKit) {
            Log.i(TAG, "Subscriber connected: ${subscriberKit.stream?.streamId}")
            updateSubscriber(subscriberKit) {
                it.copy(connected = true, hasVideo = it.stream.hasVideo())
            }
            updateDiagnostics(
                subscribers = subscribersSummary(),
                lastCallback = "subscriberConnected(${subscriberKit.stream?.streamId})",
            )
        }

        override fun onDisconnected(subscriberKit: SubscriberKit) {
            // iOS only reacts to `streamDestroyed` (the peer left the
            // room) — a subscriber that loses its connection is kept
            // in the grid and simply downgraded to its avatar.
            //
            // Deleting it here is what made the Android call look
            // empty: `onStreamReceived` fires **once** per stream, so
            // a subscriber removed by a transient disconnect was never
            // re-created, while the peer still showed this device.
            val streamId = subscriberKit.stream?.streamId ?: return
            Log.i(TAG, "Subscriber disconnected: $streamId — keeping the tile")
            updateSubscriber(subscriberKit) { it.copy(connected = false, hasVideo = false) }
            updateDiagnostics(
                subscribers = subscribersSummary(),
                lastCallback = "subscriberDisconnected($streamId)",
            )
        }

        override fun onError(subscriberKit: SubscriberKit, opentokError: OpentokError) {
            // iOS only prints here as well. The failure is surfaced in
            // the diagnostics panel so a failed subscription is visible
            // without logcat.
            Log.e(TAG, "Subscriber failed: ${opentokError.message}")
            updateDiagnostics(lastCallback = "subscriberError(${opentokError.message})")
        }
    }

    private val subscriberVideoListener = object : SubscriberKit.VideoListener {
        override fun onVideoDataReceived(subscriberKit: SubscriberKit) = Unit

        override fun onVideoDisabled(subscriberKit: SubscriberKit, reason: String) {
            updateSubscriber(subscriberKit) { it.copy(hasVideo = false) }
        }

        override fun onVideoEnabled(subscriberKit: SubscriberKit, reason: String) {
            updateSubscriber(subscriberKit) { it.copy(hasVideo = true) }
        }

        override fun onVideoDisableWarning(subscriberKit: SubscriberKit) = Unit

        override fun onVideoDisableWarningLifted(subscriberKit: SubscriberKit) = Unit
    }

    /**
     * The `Context` the SDK builds the `Publisher` / `Subscriber` with.
     *
     * It has to be the **Activity** context, not the application one:
     * the SDK's renderer is a `GLSurfaceView`, and a `SurfaceView`
     * resolves its display and its window from the context it was
     * created with. With the application context the surface is bound
     * to the default display instead of the call screen's window, and
     * the video is drawn at the wrong place and the wrong size.
     */
    var currentContext: Context? = null

    /**
     * Identity published with the local stream so remote peers can label
     * this participant. Mirrors iOS `PublisherModel.currentUserInfo`.
     */
    fun setPublisherInfo(info: PublisherInfo?) {
        publisherInfo = info
    }

    fun connect(token: String, sessionId: String): Result<Unit> = try {
        disconnect()
        val ctx = currentContext ?: return Result.failure(
            IllegalStateException("VonageSessionController.connect called without a Context"),
        )
        sessionToken = token
        sessionRoom = sessionId
        // The SDK binds its callback queue to the Looper of the thread
        // that builds the `Session`. It must therefore be built on the
        // dedicated SDK thread — otherwise the queue is the UI thread
        // and a slow callback (publishing the local stream, reading the
        // session capabilities) delays the delivery of the peers'
        // stream events, which is what left the Android grid empty
        // while iOS saw the very same participant.
        val handler = sdkHandler ?: startSdkThread()
        val newSession = buildSessionOnSdkThread(handler, ctx, sessionId)
        newSession.setSessionListener(sessionListener)
        newSession.setSignalListener(signalListener)
        newSession.setConnectionListener(connectionListener)
        newSession.setReconnectionListener(reconnectionListener)
        session = newSession
        peerConnections.clear()
        pendingStreams.clear()
        updateDiagnostics(
            requestedRoom = sessionId,
            connection = "connecting",
            publisher = "not published",
            subscribers = "none",
            lastCallback = "connect()",
        )
        newSession.connect(token)
        Log.i(TAG, "connect requested for sessionId=$sessionId")
        Result.success(Unit)
    } catch (t: Throwable) {
        Result.failure(t)
    }

    /**
     * Starts the dedicated thread the Vonage SDK's callbacks are
     * delivered on.
     */
    private fun startSdkThread(): Handler {
        val thread = HandlerThread("vonage-session").apply { start() }
        sdkThread = thread
        return Handler(thread.looper).also { sdkHandler = it }
    }

    /**
     * Builds the SDK session **on the SDK thread**.
     *
     * The session's callback `Handler` is created from
     * `Looper.myLooper()`, so the session has to be constructed on a
     * thread that owns the SDK Looper — otherwise the callbacks are
     * delivered on whichever thread happened to build the session
     * (the UI thread, in practice).
     *
     * The wait is bounded: building a session only allocates native
     * resources, so it completes in milliseconds. If it somehow does
     * not, the call fails loudly instead of hanging the caller's
     * thread forever.
     */
    private fun buildSessionOnSdkThread(
        handler: Handler,
        ctx: Context,
        sessionId: String,
    ): Session {
        val latch = CountDownLatch(1)
        var built: Session? = null
        var failure: Throwable? = null
        handler.post {
            try {
                built = Session.Builder(ctx, VonageConfig.API_KEY, sessionId)
                    .sessionOptions(TextureViewSessionOptions)
                    .build()
            } catch (t: Throwable) {
                failure = t
            } finally {
                latch.countDown()
            }
        }
        val completed = latch.await(SESSION_BUILD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!completed) {
            throw IllegalStateException("The Vonage session could not be built within ${SESSION_BUILD_TIMEOUT_MS}ms")
        }
        return built ?: throw (failure ?: IllegalStateException("The Vonage session could not be built"))
    }

    fun disconnect(): Result<Unit> = try {
        session?.disconnect()
        cleanup()
        Result.success(Unit)
    } catch (_: Throwable) {
        cleanup()
        Result.success(Unit)
    }

    fun setPublishVideo(enabled: Boolean) {
        publisher?.setPublishVideo(enabled)
    }

    fun setPublishAudio(enabled: Boolean) {
        publisher?.setPublishAudio(enabled)
    }

    fun setCameraPosition(position: CameraPosition) {
        val pub = publisher ?: return
        if (position != cameraPosition.value) {
            pub.cycleCamera()
            cameraPosition.value = position
        }
    }

    /**
     * Flips the camera between the front and the back lens.
     * Mirrors iOS `VideoCallViewModel.rotateCameraTapped()`.
     */
    fun flipCamera() {
        val pub = publisher ?: return
        val next = if (cameraPosition.value == CameraPosition.FRONT) {
            CameraPosition.BACK
        } else {
            CameraPosition.FRONT
        }
        pub.cycleCamera()
        cameraPosition.value = next
    }

    /**
     * Broadcasts the local participant's raise-hand state to the room.
     * Mirrors iOS `VideoCallViewModel.toggleRaiseHandTapped()`.
     */
    fun sendRaiseHandSignal(active: Boolean) {
        val info = publisherInfo ?: return
        val type = if (active) SignalTypes.RAISE_HAND_ACTIVE else SignalTypes.RAISE_HAND_INACTIVE
        val body = buildString {
            append("{\"type\":\"")
            append(type)
            append("\",\"data\":{\"userId\":\"")
            append(info.userId)
            append("\",\"userName\":\"")
            append(info.userName)
            append("\"}}")
        }
        runCatching { session?.sendSignal(type, body) }
    }

    /**
     * Resolves an incoming raise-hand signal against the remote peers.
     * The signal body carries the sender's `userId`; the matching
     * subscriber is the one whose published `PublisherInfo.userId` is
     * equal to it.
     */
    private fun applyRaiseHandSignal(type: String?, data: String?) {
        val body = data?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
        val payloadType = body?.jsonObject?.get("type")?.jsonPrimitive?.content ?: type
        val active = when (payloadType) {
            SignalTypes.RAISE_HAND_ACTIVE -> true
            SignalTypes.RAISE_HAND_INACTIVE -> false
            else -> return
        }
        val userId = body?.jsonObject?.get("data")?.jsonObject
            ?.get("userId")?.jsonPrimitive?.content ?: return
        val target = _subscribers.value.firstOrNull { it.info?.userId == userId } ?: return
        _subscribers.value = _subscribers.value.map {
            if (it.streamId == target.streamId) it.copy(raisedHand = active) else it
        }
    }

    private fun updateSubscriber(
        subscriberKit: SubscriberKit,
        transform: (RemoteSubscriber) -> RemoteSubscriber,
    ) {
        val streamId = subscriberKit.stream?.streamId ?: return
        _subscribers.value = _subscribers.value.map {
            if (it.streamId == streamId) transform(it) else it
        }
    }

    /**
     * The identity published with the local stream. Falls back to an
     * empty payload when the signed-in user has not been resolved yet
     * so the stream name always parses on the remote side.
     */
    private fun publisherIdentity(): PublisherInfo =
        publisherInfo ?: PublisherInfo(userName = "", userId = "")

    /**
     * Resumes the renderers of the local publisher and of every
     * subscriber.
     *
     * Only the SDK's `GLSurfaceView`-based renderer needs this: a
     * `GLSurfaceView` **starts out paused** and only renders frames
     * once `onResume()` has been called on it. The SDK only calls
     * `renderer.onResume()` from `PublisherKit.onResume()` /
     * `SubscriberKit.onResume()`, and both are a no-op unless the kit
     * was paused first (`isPaused`). A call that is opened while the
     * app is already in the foreground is never paused, so **nothing
     * ever resumes those surfaces**: the media flows (the SDK reports
     * `hasVideo=true` and the subscriber as connected) but no frame is
     * ever drawn and every tile stays black.
     *
     * The session is configured with `TextureViewSessionOptions`, so
     * the renderer in use is normally the `TextureViewRenderer`, which
     * enables the video from the start and must **not** be resumed
     * directly — see [resume].
     */
    private fun resumeRenderers() {
        if (sessionPaused) {
            // The session is paused — the SDK's own pause cycle owns
            // the renderers and resumes them when the session resumes.
            Log.i(TAG, "resumeRenderers: the session is paused — skipping")
            return
        }
        val renderers = currentRenderers()
        if (renderers.isEmpty()) return
        val handler = mainHandler ?: return
        handler.post { resume(renderers) }
        // The renderer is resumed as soon as it is created, which is
        // usually a moment before Compose attaches the view to the
        // window. A `GLSurfaceView` that was resumed before its
        // surface existed still needs one more resume once the surface
        // is created, otherwise the GL thread waits for a surface that
        // will never arrive and nothing is drawn. The second resume
        // lands after the attachment has happened.
        handler.postDelayed({
            if (sessionPaused) return@postDelayed
            resume(renderers)
        }, RENDERER_RESUME_DELAY_MS)
    }

    /**
     * Resumes the given renderers.
     *
     * Only the renderers that draw into a `GLSurfaceView` are
     * resumed. The SDK's other renderer (`TextureViewRenderer`, used
     * when the session is configured with `useTextureViews`) keeps its
     * own state: its `onResume()` restores the *previous* video state
     * (`videoLastStatus`), which is `false` until the renderer has
     * been paused at least once — calling it on a renderer that was
     * never paused therefore turns the video **off** and the tile goes
     * black. Its own attach/detach handling already starts the
     * rendering when the view is attached to the window.
     */
    private fun resume(renderers: List<BaseVideoRenderer>) {
        val resumed = renderers.filter { renderer ->
            // A `TextureViewRenderer` is not one of the SDK's public
            // classes, so it is identified by the renderer it wraps
            // rather than by its type: its view is a `TextureView`,
            // never a `GLSurfaceView`.
            val view = renderer.view
            val isGlSurface = view is android.opengl.GLSurfaceView
            if (!isGlSurface) {
                Log.i(TAG, "resume: skipping a non-GLSurfaceView renderer (${renderer.javaClass.simpleName})")
                return@filter false
            }
            runCatching { renderer.onResume() }
                .onFailure { Log.w(TAG, "resume: onResume failed: ${it.message}") }
            true
        }
        Log.i(
            TAG,
            "resume: resumed ${resumed.size}/${renderers.size} renderer(s) " +
                "publishers=${publisher != null} subscribers=${_subscribers.value.size}",
        )
    }

    /**
     * The renderers of the local publisher and of every subscriber.
     *
     * The list is snapshotted by the caller: the renderers have to be
     * captured **before** the publisher and the subscribers are
     * dropped, or the pause that runs on teardown would find nothing
     * to stop and the orphaned renderers would keep their render
     * threads alive.
     */
    private fun currentRenderers(): List<BaseVideoRenderer> = buildList {
        publisher?.renderer?.let { add(it) }
        _subscribers.value.forEach { it.subscriber.renderer?.let { r -> add(r) } }
    }

    /**
     * Pauses the renderers — used when the session goes away so the
     * GL surfaces stop burning GPU cycles.
     */
    private fun pauseRenderers(renderers: List<BaseVideoRenderer> = currentRenderers()) {
        if (renderers.isEmpty()) return
        val handler = mainHandler ?: return
        handler.post {
            renderers.forEach { renderer ->
                runCatching { renderer.onPause() }
                    .onFailure { Log.w(TAG, "pauseRenderers: onPause failed: ${it.message}") }
            }
            Log.i(TAG, "pauseRenderers: paused ${renderers.size} renderer(s)")
        }
    }

    /**
     * Turns a remote stream into a live subscriber.
     *
     * Split out of `onStreamReceived` so the same code path serves
     * both the live callback and the streams that were parked while
     * the session was still coming up.
     *
     * The `Subscriber` — and with it the SDK's renderer, which is a
     * `TextureView` — has to be built on the **main thread**: an
     * Android view may only be created there. The callback arrives on
     * the SDK's own thread, so the work is handed over to the main
     * thread and the result is awaited with a bounded latch (the
     * session's signalling must not be blocked for long, but the
     * subscription has to be complete before the caller moves on).
     */
    private fun subscribeToStream(session: Session, stream: Stream) {
        // Never subscribe to the same stream twice — the server may
        // re-announce a stream after a reconnect, and a second
        // `Subscriber` for the same stream would leave a ghost tile in
        // the grid.
        if (_subscribers.value.any { it.streamId == stream.streamId }) {
            Log.i(TAG, "subscribeToStream: ${stream.streamId} is already subscribed — skipping")
            return
        }
        val ctx = currentContext ?: run {
            Log.w(TAG, "subscribeToStream: no Context — parking ${stream.streamId}")
            parkPendingStream(stream)
            return
        }
        val main = mainHandler ?: run {
            Log.w(TAG, "subscribeToStream: no main thread handler — parking ${stream.streamId}")
            parkPendingStream(stream)
            return
        }
        val latch = CountDownLatch(1)
        main.post {
            try {
                val subscriber = Subscriber.Builder(ctx, stream).build()
                subscriber.setSubscriberListener(subscriberListener)
                subscriber.setVideoListener(subscriberVideoListener)
                session.subscribe(subscriber)
                val info = PublisherInfo.fromJson(stream.name)
                Log.i(
                    TAG,
                    "subscribeToStream: parsed identity userName='${info?.userName}' " +
                        "userId='${info?.userId}' hasImage=${info?.image != null}",
                )
                _subscribers.value = _subscribers.value + RemoteSubscriber(
                    subscriber = subscriber,
                    stream = stream,
                    streamId = stream.streamId,
                    info = info,
                    hasVideo = stream.hasVideo(),
                    raisedHand = false,
                    connected = true,
                )
                Log.i(
                    TAG,
                    "subscribeToStream: subscriber count=${_subscribers.value.size} " +
                        "streamIds=${_subscribers.value.map { it.streamId }}",
                )
                // The SDK's `GLSurfaceView` renderer starts out paused,
                // so its first frames would never be drawn without an
                // explicit resume.
                resumeRenderers()
                updateDiagnostics(
                    connection = sessionSummary(),
                    subscribers = subscribersSummary(),
                    lastCallback = "onStreamReceived(${stream.streamId})",
                )
            } catch (t: Throwable) {
                // `session.subscribe()` throws when the session is not
                // connected yet. The stream is parked instead of being
                // lost — it is the only notification this client gets
                // for that peer.
                Log.e(TAG, "subscribeToStream failed for ${stream.streamId}: ${t.message}")
                parkPendingStream(stream)
                schedulePendingStreamRetry()
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(SUBSCRIBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "subscribeToStream: ${stream.streamId} did not complete in time")
        }
    }

    /**
     * Parks a stream that arrived before the session was ready to
     * subscribe to it.
     */
    private fun parkPendingStream(stream: Stream) {
        if (pendingStreams.none { it.streamId == stream.streamId }) {
            pendingStreams += stream
        }
        Log.i(
            TAG,
            "parkPendingStream: ${stream.streamId} parked (pending=${pendingStreams.size})",
        )
    }

    /**
     * Subscribes to the streams that arrived before the session was
     * ready.
     */
    private fun drainPendingStreams(session: Session) {
        if (pendingStreams.isEmpty()) return
        val parked = pendingStreams.toList()
        pendingStreams.clear()
        Log.i(TAG, "drainPendingStreams: replaying ${parked.size} parked stream(s)")
        for (stream in parked) {
            subscribeToStream(session, stream)
        }
    }

    /**
     * Schedules a retry for the streams that could not be subscribed
     * to yet.
     *
     * `session.subscribe()` throws when the session's native
     * connection is not up yet, which happens when the server pushes a
     * stream event in the middle of the connect handshake. The stream
     * must not be lost — it is the only notification this client gets
     * for that peer — so it stays parked and the drain is retried
     * until it succeeds.
     */
    private fun schedulePendingStreamRetry() {
        if (pendingStreams.isEmpty()) return
        val handler = sdkHandler ?: return
        if (pendingRetryScheduled) return
        pendingRetryScheduled = true
        handler.postDelayed({
            pendingRetryScheduled = false
            val live = this.session
            if (live == null) {
                Log.w(TAG, "schedulePendingStreamRetry: no live session — keeping the streams parked")
                return@postDelayed
            }
            if (pendingStreams.isEmpty()) return@postDelayed
            Log.i(TAG, "schedulePendingStreamRetry: retrying ${pendingStreams.size} parked stream(s)")
            drainPendingStreams(live)
            if (pendingStreams.isNotEmpty()) {
                schedulePendingStreamRetry()
            }
        }, PENDING_STREAM_RETRY_MS)
    }

    /**
     * Reads the session capabilities **without blocking the SDK's
     * callback thread**.
     *
     * `getCapabilities()` blocks until the server has sent its
     * capability message, which can take tens of seconds. Calling it
     * from `onConnected` stalled the SDK's callback queue for that
     * whole period, and the peers' stream events — which the server
     * pushes as part of the connect handshake — were stuck behind it
     * in the queue. The remote participants never appeared on the
     * grid even though the local stream was published and the peer
     * connection was established.
     *
     * The capabilities are only used for diagnostics, so reading them
     * on a background thread is enough.
     */
    private fun readCapabilitiesOffThread(session: Session) {
        Thread({
            val capabilities = runCatching { session.getCapabilities() }.getOrNull()
            Log.i(
                TAG,
                "Session capabilities: canPublish=${capabilities?.canPublish} " +
                    "canSubscribe=${capabilities?.canSubscribe}",
            )
            updateDiagnostics(capabilities = capabilitiesSummary(capabilities))
        }, "vonage-capabilities").apply { isDaemon = true }.start()
    }

    /**
     * Publishes the local stream.
     *
     * `session.publish()` opens the camera and the microphone, which
     * takes several seconds. It is therefore **not** called from
     * inside the SDK's `onConnected` callback: that callback runs on
     * the SDK's single callback queue, and blocking it there delays
     * the delivery of the peers' stream events. iOS gets away with
     * publishing straight from `sessionDidConnect` because `OTSession`
     * delivers its callbacks on a queue of its own; Android needs the
     * same separation, which the dedicated SDK thread provides.
     *
     * The `Publisher` — and with it the SDK's renderer, which is a
     * `TextureView` — has to be built on the **main thread**: an
     * Android view may only be created there. The blocking publish
     * itself then runs on a worker thread so that the SDK's callback
     * queue stays free.
     */
    private fun publishLocalStream(session: Session) {
        val ctx = currentContext ?: run {
            Log.w(TAG, "publishLocalStream: no Context — the local stream cannot be published")
            return
        }
        val main = mainHandler ?: run {
            Log.w(TAG, "publishLocalStream: no main thread handler — the local stream cannot be published")
            return
        }
        main.post {
            val pub = try {
                val builder = Publisher.Builder(ctx)
                // Always publish a well-formed identity payload: remote
                // peers parse the stream name to label the tile, and an
                // empty name would leave them with a blank caption.
                builder.name(publisherIdentity().toJson())
                builder.build().also { it.setPublisherListener(publisherListener) }
            } catch (t: Throwable) {
                Log.e(TAG, "Creating the local publisher failed: ${t.message}")
                _sessionEvents.tryEmit(VideoCallSessionEvent.Failed(VideoCallError.Error(t)))
                return@post
            }
            this@VonageSessionController.publisher = pub
            Log.i(TAG, "Publishing local stream with name='${publisherIdentity().toJson()}'")
            // The publish opens the camera and the microphone, which
            // takes several seconds — it runs on a worker thread so
            // that neither the UI thread nor the SDK's callback queue
            // is blocked.
            Thread({
                try {
                    session.publish(pub)
                    _publishers.value = pub
                    Log.i(TAG, "Local publisher created (stream=${pub.stream?.streamId})")
                    // The local preview's renderer starts out paused,
                    // so its first frames would never be drawn without
                    // an explicit resume.
                    resumeRenderers()
                    updateDiagnostics(
                        connection = sessionSummary(),
                        publisher = "published stream=${pub.stream?.streamId}",
                        lastCallback = "onConnected + publish",
                    )
                } catch (t: Throwable) {
                    // iOS checks the publish error and flips
                    // `loadingState` to `.failed`; without this the
                    // screen sat on "Connecting" forever because
                    // `_publishers` was never populated.
                    Log.e(TAG, "Publishing the local stream failed: ${t.message}")
                    _sessionEvents.tryEmit(VideoCallSessionEvent.Failed(VideoCallError.Error(t)))
                }
            }, "vonage-publish").apply { isDaemon = true }.start()
        }
    }

    private fun cleanup() {
        // Stop the GL surfaces before the renderers are dropped — an
        // orphaned `TextureView` keeps its render thread alive. The
        // list is captured first: the pause is posted to the main
        // thread and would otherwise find nothing left to stop.
        pauseRenderers(currentRenderers())
        _subscribers.value = emptyList()
        publisher = null
        _publishers.value = null
        session = null
        sessionConnected = false
        peerConnections.clear()
        pendingStreams.clear()
        updateDiagnostics(
            connection = "disconnected",
            publisher = "not published",
            subscribers = "none",
        )
    }

    /**
     * Re-creates the session with the same token and room.
     *
     * A fresh `connect()` is the only way to make the Vonage server
     * send the room's current streams again: the SDK exposes no way to
     * enumerate the streams of a session, and a session that
     * reconnected does not get the "stream created" events it missed.
     *
     * The old session's `onDisconnected` callback is ignored by the
     * stale-session guard (the controller already points at the new
     * session), so the call screen is not torn down.
     */
    fun reconnectNow(reason: String = "manual"): Result<Unit> {
        val token = sessionToken
        val room = sessionRoom
        if (token == null || room == null) {
            Log.w(TAG, "reconnectNow($reason): no live session to re-create")
            return Result.failure(IllegalStateException("No live session to reconnect"))
        }
        val now = System.currentTimeMillis()
        if (now - lastReconnectAt < RECONNECT_COOLDOWN_MS) {
            Log.w(TAG, "reconnectNow($reason): skipped — the last re-connect was too recent")
            return Result.failure(IllegalStateException("Re-connect is on cooldown"))
        }
        lastReconnectAt = now
        Log.i(TAG, "reconnectNow($reason): re-creating the session for room=$room")
        updateDiagnostics(lastCallback = "reconnectNow($reason)")
        return connect(token, room)
    }

    /**
     * Forwards the host activity's lifecycle to the SDK.
     *
     * The Vonage Android SDK requires the session to be told when the
     * app is paused and resumed (the tutorial's step 5). Without it the
     * SDK keeps its media devices and sockets alive across a
     * backgrounding, which on a phone leaves the session connected but
     * no longer receiving stream events — the "connected but no
     * subscribers" state.
     *
     * The calls are forwarded to the SDK thread: the session was
     * created there, so that is the thread the SDK expects its
     * lifecycle calls on.
     */
    fun onPause() {
        val live = session ?: return
        sessionPaused = true
        runCatching {
            val handler = sdkHandler
            if (handler != null) handler.post { live.onPause() } else live.onPause()
        }.onFailure { Log.w(TAG, "session.onPause() failed: ${it.message}") }
    }

    /** Counterpart of [onPause] — see the SDK's activity lifecycle contract. */
    fun onResume() {
        val live = session ?: return
        sessionPaused = false
        runCatching {
            val handler = sdkHandler
            if (handler != null) handler.post { live.onResume() } else live.onResume()
        }.onFailure { Log.w(TAG, "session.onResume() failed: ${it.message}") }
        // The SDK drops the subscriber connections while the session is
        // paused and does not re-deliver `onStreamReceived` for the
        // streams that already existed, so the peers have to be
        // re-subscribed explicitly when the call comes back to the
        // foreground.
        resubscribeAllStreams()
        // The SDK only resumes a renderer that it had paused itself.
        // The renderers this controller resumed (the ones the SDK
        // never saw paused) have to be resumed again.
        resumeRenderers()
    }

    /**
     * Re-subscribes to every remote stream the controller knows about.
     *
     * The SDK drops the subscriber connections while the session is
     * paused and does **not** re-deliver `onStreamReceived` for the
     * streams that already existed — the peers stay in the room, the
     * tiles stay empty. Re-subscribing every stream we know about
     * brings the grid back (iOS keeps its `OTSubscriber` objects bound
     * to the session across a reconnect, which is the behaviour being
     * mirrored here).
     */
    private fun resubscribeAllStreams() {
        val live = session ?: return
        val known = _subscribers.value
        if (known.isEmpty()) return
        val handler = sdkHandler ?: return
        handler.post {
            for (remote in known) {
                runCatching { live.subscribe(remote.subscriber) }
                    .onSuccess {
                        Log.i(TAG, "resubscribeAllStreams: re-subscribed streamId=${remote.streamId}")
                        updateSubscriber(remote.subscriber) { it.copy(connected = true) }
                    }
                    .onFailure { t ->
                        Log.e(TAG, "resubscribeAllStreams: re-subscribe failed for ${remote.streamId}: ${t.message}")
                    }
            }
            updateDiagnostics(
                subscribers = subscribersSummary(),
                lastCallback = "resubscribeAllStreams",
            )
        }
    }

    /**
     * Records what the token actually granted. Called by the repository
     * as soon as the token arrives — the room, the role, the scope and
     * the layout class list are the three things that decide whether
     * this device can receive the other participants' streams.
     */
    fun setTokenDiagnostics(summary: String) {
        _diagnostics.update { it.copy(token = summary) }
    }

    /**
     * Refreshes the diagnostics snapshot. Every field that is not
     * passed keeps its current value.
     */
    private fun updateDiagnostics(
        requestedRoom: String? = null,
        token: String? = null,
        connection: String? = null,
        publisher: String? = null,
        subscribers: String? = null,
        capabilities: String? = null,
        lastCallback: String? = null,
    ) {
        _diagnostics.update { current ->
            current.copy(
                requestedRoom = requestedRoom ?: current.requestedRoom,
                token = token ?: current.token,
                connection = connection ?: current.connection,
                publisher = publisher ?: current.publisher,
                subscribers = subscribers ?: current.subscribers,
                capabilities = capabilities ?: current.capabilities,
                lastCallback = lastCallback ?: current.lastCallback,
            )
        }
    }

    /**
     * Renders the session capabilities the server granted — the
     * authoritative answer to "may this client receive the other
     * participants' streams?".
     */
    private fun capabilitiesSummary(capabilities: Session.Capabilities?): String {
        if (capabilities == null) return "unknown"
        return "canPublish=${capabilities.canPublish} canSubscribe=${capabilities.canSubscribe}"
    }

    /** Renders the current peer/session state into the diagnostics panel. */
    private fun sessionSummary(): String {
        val peers = peerConnections.size
        val state = when {
            session == null -> "idle"
            _publishers.value != null -> "connected"
            else -> "connecting"
        }
        return "$state, peers=$peers"
    }

    /**
     * Renders the subscriber list for the diagnostics panel — the count
     * plus the connection state of each tile, so a dropped subscriber
     * is distinguishable from a peer that never arrived.
     */
    private fun subscribersSummary(): String =
        "count=${_subscribers.value.size} " +
            "[${_subscribers.value.joinToString { "${it.displayName}(${it.streamId}:${if (it.connected) "live" else "dropped"})" }}]"

    private companion object {
        const val TAG = "VideoCallSession"

        /**
         * The minimum gap between two self-initiated re-connects. The
         * recovery path must not turn into a loop on a flaky network.
         */
        const val RECONNECT_COOLDOWN_MS = 20_000L

        /**
         * How long the controller waits for the SDK session to be
         * built on the SDK thread. Building it only allocates native
         * resources, so this is generous.
         */
        const val SESSION_BUILD_TIMEOUT_MS = 10_000L

        /**
         * How long to wait before retrying the subscription of the
         * streams that arrived before the session was ready.
         */
        const val PENDING_STREAM_RETRY_MS = 1_000L

        /**
         * How long to wait before resuming the renderers a second
         * time, so that the resume lands after Compose has attached
         * the SDK's surface to the window.
         */
        const val RENDERER_RESUME_DELAY_MS = 500L

        /**
         * How long the controller waits for a subscription to complete
         * on the main thread. The subscription is quick; the bound
         * only stops the SDK's callback queue from being held up.
         */
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}

/**
 * One remote participant as seen by the UI layer: the live SDK
 * subscriber (needed to render the video surface) plus the display
 * metadata parsed from the publisher payload.
 */
data class RemoteSubscriber(
    val subscriber: Subscriber,
    val stream: Stream,
    val streamId: String,
    val info: PublisherInfo?,
    val hasVideo: Boolean,
    val raisedHand: Boolean,
    /**
     * Whether the SDK still holds a live subscription for this peer.
     *
     * A peer that is in the room but whose subscription dropped keeps
     * its tile (iOS parity) and is rendered with its avatar instead of
     * the video surface.
     */
    val connected: Boolean = true,
) {
    /** Display name — the published nickname, falling back to the stream id. */
    val displayName: String
        get() = info?.userName?.takeIf { it.isNotBlank() } ?: streamId

    /** Avatar URL published by the remote peer, if any. */
    val thumbnailUrl: String?
        get() = info?.image?.takeIf { it.isNotBlank() }
}

/**
 * The session options that make the SDK render the video through a
 * `TextureView` instead of the default `GLSurfaceView`.
 *
 * The default renderer draws the video into a `SurfaceView`, which
 * lives in **its own window layer above the app's content**
 * (`setZOrderMediaOverlay(true)`). That is a problem for a Compose
 * screen:
 *
 *  - the video is composited **over** the call screen's own chrome, so
 *    the participant's name and the raised-hand badge disappear behind
 *    it — iOS puts the overlay above the video (`ZStack` in
 *    `SubscriberContainer`);
 *  - turning the media-overlay flag off is not an option either: the
 *    surface is then a hole in the window and the tile's own
 *    background — drawn by Compose — would cover the video completely.
 *
 * A `TextureView` is an ordinary view: it is laid out and clipped like
 * any other, and it composites with the rest of the UI, so the video
 * fills its tile and the overlay stays on top.
 *
 * The SDK exposes the switch through `SessionOptions.useTextureViews()`
 * — the `VideoRenderFactory` that implements it is package-private —
 * so the option is provided by overriding that method.
 *
 * The `TextureViewRenderer` it selects enables the video and fills the
 * surface from the start (`videoEnabled = true`, `videoFitEnabled =
 * false`), which matches iOS's `viewScaleBehavior = .fill`. Its
 * `onResume()` must **not** be called directly: it restores the
 * renderer's *previous* video state, which is `false` until the
 * renderer has been paused at least once — calling it on a renderer
 * that was never paused turns the video off and the tile goes black.
 */
private object TextureViewSessionOptions : Session.SessionOptions() {
    override fun useTextureViews(): Boolean = true
}
