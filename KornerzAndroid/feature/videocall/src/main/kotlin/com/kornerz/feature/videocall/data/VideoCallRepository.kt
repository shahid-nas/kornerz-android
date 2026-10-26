package com.kornerz.feature.videocall.data

import android.util.Log
import com.kornerz.core.model.CallSession
import com.kornerz.core.model.SubscriberSnapshot
import com.kornerz.core.network.videocall.SessionTokenService
import com.kornerz.core.network.videocall.VonageSessionController
import com.kornerz.core.network.videocall.VonageTokenClaims
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
        Log.i(TAG, "connect() requested for sessionId=$sessionId")
        // Tear down any previous session *before* the token is
        // fetched. The teardown must not happen after the new session
        // has been opened: `VonageSessionController.cleanup()` nulls
        // the session field, and the stale-session guards compare
        // against it — a teardown that lands after the new session is
        // connected makes every callback of the new session look
        // stale, so the peers' streams are dropped and the grid stays
        // empty.
        disconnect()
        emit(CallSession(sessionId = sessionId, status = CallSession.STATUS_AUTHENTICATING))
        val tokenResult = sessionTokenService.fetchToken(sessionId)
        val tokenValue = tokenResult.getOrElse { t ->
            vonageSessionController.disconnect()
            Log.e(TAG, "Token fetch failed for sessionId=$sessionId: ${t.message}")
            throw t
        }
        // The token's `session_id` claim is the room the client was
        // actually granted — compare it with what we asked for.
        VonageTokenClaims.log(tokenValue, sessionId)
        vonageSessionController.setTokenDiagnostics(
            VonageTokenClaims.summary(tokenValue, sessionId),
        )
        emit(CallSession(sessionId = sessionId, status = CallSession.STATUS_CONNECTING))
        val connectResult = vonageSessionController.connect(tokenValue, sessionId)
        if (connectResult.isFailure) {
            Log.e(TAG, "Vonage connect failed for sessionId=$sessionId")
            throw connectResult.exceptionOrNull()
                ?: IllegalStateException("Vonage connect failed")
        }
        Log.i(TAG, "Vonage connect requested — waiting for onConnected (sessionId=$sessionId)")
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
                        streamId = it.streamId,
                        subscriberName = it.displayName,
                        hasVideo = it.hasVideo,
                        thumbnailUrl = it.thumbnailUrl,
                        raisedHand = it.raisedHand,
                    )
                },
            )
        }.collect { snapshot ->
            Log.i(
                TAG,
                "session snapshot: status=${snapshot.status} localPublisher=${snapshot.localPublisherId} " +
                    "remoteSubscribers=${snapshot.remoteSubscribers.size} " +
                    "streamIds=${snapshot.remoteSubscribers.map { it.streamId }}",
            )
            emit(snapshot)
        }
    }

    fun disconnect(): Result<Unit> = vonageSessionController.disconnect()

    private companion object {
        const val TAG = "VideoCallRepo"
    }
}
