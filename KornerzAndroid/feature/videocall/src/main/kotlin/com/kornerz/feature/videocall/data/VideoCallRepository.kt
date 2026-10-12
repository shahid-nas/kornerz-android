package com.kornerz.feature.videocall.data

import com.kornerz.core.model.CallSession
import com.kornerz.core.model.SubscriberSnapshot
import com.kornerz.core.network.videocall.SessionTokenService
import com.kornerz.core.network.videocall.VonageSessionController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the video call pipeline. Orchestrates
 * [SessionTokenService] (WebSocket → Vonage session token) and
 * [VonageSessionController] (Vonage SDK lifecycle) into a single
 * `Flow<CallSession>` (architecture §2.3).
 *
 * State machine yields three states (iOS `VideoCallViewModel`
 * parity):
 *  - `Authenticating` — token fetch in flight
 *  - `Connecting`    — token received, Vonage session connecting
 *  - `Connected`     — local publisher published, subscribers
 *                      projected as snapshots
 *
 * The repository is `@Singleton` — `connect` / `disconnect` are
 * idempotent so successive calls don't throw.
 */
@Singleton
class VideoCallRepository @Inject constructor(
    private val sessionTokenService: SessionTokenService,
    private val vonageSessionController: VonageSessionController,
) {

    fun connect(sessionId: String): Flow<CallSession> = flow {
        disconnect()
        emit(CallSession(sessionId = sessionId, status = CallSession.STATUS_AUTHENTICATING))
        val tokenResult = sessionTokenService.fetchToken(sessionId)
        val tokenValue = tokenResult.getOrElse { t ->
            vonageSessionController.disconnect()
            throw t
        }
        emit(CallSession(sessionId = sessionId, status = CallSession.STATUS_CONNECTING))
        val connectResult = vonageSessionController.connect(tokenValue, sessionId)
        if (connectResult.isFailure) {
            throw connectResult.exceptionOrNull()
                ?: IllegalStateException("Vonage connect failed")
        }
        combine(
            vonageSessionController.publishers,
            vonageSessionController.subscribers,
        ) { publisher, subscribers ->
            CallSession(
                sessionId = sessionId,
                status = if (publisher != null) CallSession.STATUS_CONNECTED else CallSession.STATUS_CONNECTING,
                localPublisherId = publisher?.stream?.streamId,
                remoteSubscribers = subscribers.map {
                    SubscriberSnapshot(
                        streamId = it.stream.streamId,
                        subscriberName = it.stream.name,
                        hasVideo = it.stream.hasVideo(),
                    )
                },
            )
        }.collect { snapshot -> emit(snapshot) }
    }

    fun disconnect(): Result<Unit> = vonageSessionController.disconnect()
}