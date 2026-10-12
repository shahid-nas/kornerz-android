package com.kornerz.core.network.videocall

import android.content.Context
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
 * `StateFlow<List<Subscriber>>`. Both are single-slot / list slots;
 * `VonageSessionController` is a `@Singleton` and a new call
 * reconnects from scratch (idempotent — `connect` / `disconnect`
 * cancel the previous run).
 */
@Singleton
class VonageSessionController @Inject constructor() {

    private val _publishers = MutableStateFlow<Publisher?>(null)
    val publishers: StateFlow<Publisher?> = _publishers.asStateFlow()

    private val _subscribers = MutableStateFlow<List<Subscriber>>(emptyList())
    val subscribers: StateFlow<List<Subscriber>> = _subscribers.asStateFlow()

    private var session: Session? = null
    private var publisher: Publisher? = null
    private val sessionListener = object : Session.SessionListener {
        override fun onConnected(session: Session) {
            this@VonageSessionController.session = session
            val ctx = currentContext ?: return
            val pub = Publisher.Builder(ctx).build()
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
            session.subscribe(subscriber)
            _subscribers.value = _subscribers.value + subscriber
        }

        override fun onStreamDropped(session: Session, stream: Stream) {
            val current = _subscribers.value
            val match = current.firstOrNull { it.stream.streamId == stream.streamId }
            if (match != null) {
                _subscribers.value = current - match
            }
        }

        override fun onError(session: Session, opentokError: OpentokError) {
            cleanup()
        }
    }

    /** Set by [connect]; the SDK needs a `Context` to build the `Publisher` / `Subscriber`. */
    var currentContext: Context? = null

    fun connect(token: String, sessionId: String): Result<Unit> = try {
        disconnect()
        val ctx = currentContext ?: return Result.failure(
            IllegalStateException("VonageSessionController.connect called without a Context"),
        )
        val newSession = Session.Builder(ctx, VonageConfig.API_KEY, sessionId).build()
        newSession.setSessionListener(sessionListener)
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
        when (position) {
            CameraPosition.FRONT -> pub.cycleCamera()
            CameraPosition.BACK -> pub.cycleCamera()
        }
    }

    private fun cleanup() {
        _subscribers.value = emptyList()
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