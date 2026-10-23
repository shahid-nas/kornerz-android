package com.kornerz.feature.shell.deeplink

import com.kornerz.core.model.DeepLink
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds deep links until the app can route them. Mirrors iOS
 * `DeepLinksManager` (`kornerz/Kornerz/DeepLinks/DeeplinkManager.swift`).
 *
 * iOS's manager has three responsibilities — accept a link, mark the
 * app as launched, and route the held link once the app is loaded — and
 * this one has the same three:
 *
 * | Member | Behaviour |
 * |---|---|
 * | [handle] | Accepts a link. Before the app is ready the link is queued; afterwards it is released. |
 * | [notifyAppLaunch] | Marks the app ready and releases every queued link, in queue order. |
 * | [pending] | A FIFO queue of links not yet taken by the interstitial. |
 *
 * **Why the queue exists.** A cold start from a deep link delivers the
 * intent to `MainActivity` long before the shell's `NavHost` exists —
 * the splash → auth → shell chain still has to resolve. The link is
 * held in memory until the shell is composed, exactly as iOS holds it
 * in its `CurrentValueSubject` until `notifyAppLaunch()`.
 *
 * **A second link never overwrites the first.** It is queued behind
 * it, and the queue is released in order.
 *
 * The manager holds links **in memory only**. On process death the
 * link is gone, which is honest and matches iOS.
 */
@Singleton
class DeepLinkManager @Inject constructor() {

    /**
     * The links waiting to be taken by the interstitial. Guarded by
     * [lock] — `MainActivity` hands links over from `onCreate` /
     * `onNewIntent`, which are not coroutines.
     */
    private val lock = Any()
    private val pending = ArrayDeque<DeepLink>()

    /** Whether the shell has reported that it is ready to route. */
    private var appLaunched = false

    /**
     * The links that have been released for routing.
     *
     * A [Channel] rather than a `SharedFlow`: a channel **buffers** its
     * emissions, so a link released before the shell starts collecting
     * is not dropped. A cold start's link is released by
     * [notifyAppLaunch], which the shell calls after its `NavHost` is
     * composed — but the buffer makes the ordering irrelevant.
     */
    private val released = Channel<DeepLink>(Channel.BUFFERED)

    /** The released links, in release order. */
    val releasedLinks: Flow<DeepLink> = released.receiveAsFlow()

    /**
     * Accepts a link.
     *
     * Before [notifyAppLaunch] the link is queued; afterwards it is
     * released immediately.
     */
    fun handle(deeplink: DeepLink) {
        val isReady = synchronized(lock) {
            pending.addLast(deeplink)
            appLaunched
        }
        if (isReady) {
            released.trySend(deeplink)
        }
    }

    /**
     * Marks the app as ready to route and releases every queued link,
     * in the order it was queued.
     *
     * Called by the shell once its `NavHost` is composed, so a cold
     * start's link is released only when the graph can route it.
     */
    fun notifyAppLaunch() {
        val queued = synchronized(lock) {
            appLaunched = true
            pending.toList()
        }
        queued.forEach { released.trySend(it) }
    }

    /**
     * Takes the head of the queue — the link the interstitial is about
     * to resolve. Returns `null` when the queue is empty.
     */
    fun takeNext(): DeepLink? = synchronized(lock) { pending.removeFirstOrNull() }

    /** Whether any link is still waiting to be routed. */
    fun hasPending(): Boolean = synchronized(lock) { pending.isNotEmpty() }
}
