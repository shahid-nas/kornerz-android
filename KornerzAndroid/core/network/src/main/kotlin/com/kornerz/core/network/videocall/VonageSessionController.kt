package com.kornerz.core.network.videocall

import android.content.Context
import com.opentok.android.Connection
import com.opentok.android.OpentokError
import com.opentok.android.Publisher
import com.opentok.android.PublisherKit
import com.opentok.android.Session
import com.opentok.android.Stream
import com.opentok.android.Subscriber
import com.opentok.android.SubscriberKit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 */
@Singleton
class VonageSessionController @Inject constructor() {

    private val _publishers = MutableStateFlow<Publisher?>(null)
    val publishers: StateFlow<Publisher?> = _publishers.asStateFlow()

    private val _subscribers = MutableStateFlow<List<RemoteSubscriber>>(emptyList())
    val subscribers: StateFlow<List<RemoteSubscriber>> = _subscribers.asStateFlow()

    private var session: Session? = null
    private var publisher: Publisher? = null

    /** Identity published alongside the local stream (iOS parity). */
    private var publisherInfo: PublisherInfo? = null

    /** streamIds of remote peers that currently have their hand raised. */
    private val raisedHands = MutableStateFlow<Set<String>>(emptySet())

    /** The lens the local publisher is currently streaming from. */
    private val cameraPosition = MutableStateFlow(CameraPosition.FRONT)

    private val sessionListener = object : Session.SessionListener {
        override fun onConnected(session: Session) {
            this@VonageSessionController.session = session
            val ctx = currentContext ?: return
            val builder = Publisher.Builder(ctx)
            publisherInfo?.let { builder.name(it.toJson()) }
            val pub = builder.build()
            pub.setPublisherListener(NoopPublisherListener)
            this@VonageSessionController.publisher = pub
            session.publish(pub)
            _publishers.value = pub
        }

        override fun onDisconnected(session: Session) {
            cleanup()
        }

        override fun onStreamReceived(session: Session, stream: Stream) {
            val ctx = currentContext ?: return
            val subscriber = Subscriber.Builder(ctx, stream).build()
            subscriber.setSubscriberListener(NoopSubscriberListener)
            subscriber.setVideoListener(subscriberVideoListener)
            session.subscribe(subscriber)
            _subscribers.value = _subscribers.value + RemoteSubscriber(
                subscriber = subscriber,
                streamId = stream.streamId,
                info = PublisherInfo.fromJson(stream.name),
                hasVideo = stream.hasVideo(),
                raisedHand = false,
            )
        }

        override fun onStreamDropped(session: Session, stream: Stream) {
            val current = _subscribers.value
            val match = current.firstOrNull { it.streamId == stream.streamId }
            if (match != null) {
                _subscribers.value = current - match
                raisedHands.value = raisedHands.value - stream.streamId
            }
        }

        override fun onError(session: Session, opentokError: OpentokError) {
            cleanup()
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

    /** Set by [connect]; the SDK needs a `Context` to build the `Publisher` / `Subscriber`. */
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
        val newSession = Session.Builder(ctx, VonageConfig.API_KEY, sessionId).build()
        newSession.setSessionListener(sessionListener)
        newSession.setSignalListener(signalListener)
        session = newSession
        newSession.connect(token)
        Result.success(Unit)
    } catch (t: Throwable) {
        Result.failure(t)
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
        raisedHands.value = if (active) {
            raisedHands.value + target.streamId
        } else {
            raisedHands.value - target.streamId
        }
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

    private fun cleanup() {
        _subscribers.value = emptyList()
        raisedHands.value = emptySet()
        publisher = null
        _publishers.value = null
        session = null
    }

    private object NoopPublisherListener : PublisherKit.PublisherListener {
        override fun onError(publisherKit: PublisherKit, opentokError: OpentokError) = Unit
        override fun onStreamCreated(publisherKit: PublisherKit, stream: Stream) = Unit
        override fun onStreamDestroyed(publisherKit: PublisherKit, stream: Stream) = Unit
    }

    private object NoopSubscriberListener : SubscriberKit.SubscriberListener {
        override fun onConnected(subscriberKit: SubscriberKit) = Unit
        override fun onDisconnected(subscriberKit: SubscriberKit) = Unit
        override fun onError(subscriberKit: SubscriberKit, opentokError: OpentokError) = Unit
    }
}

/**
 * One remote participant as seen by the UI layer: the live SDK
 * subscriber (needed to render the video surface) plus the display
 * metadata parsed from the publisher payload.
 */
data class RemoteSubscriber(
    val subscriber: Subscriber,
    val streamId: String,
    val info: PublisherInfo?,
    val hasVideo: Boolean,
    val raisedHand: Boolean,
) {
    /** Display name — the published nickname, falling back to the stream id. */
    val displayName: String
        get() = info?.userName?.takeIf { it.isNotBlank() } ?: streamId

    /** Avatar URL published by the remote peer, if any. */
    val thumbnailUrl: String?
        get() = info?.image?.takeIf { it.isNotBlank() }
}
