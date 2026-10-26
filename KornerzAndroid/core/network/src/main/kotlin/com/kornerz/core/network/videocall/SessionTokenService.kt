package com.kornerz.core.network.videocall

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches a Vonage session token from the backend WebSocket
 * (`ws://ws.kornerz.com:80/`).
 *
 * Mirrors iOS `OpenTokTokenService.swift` + `WebSocketService.swift`
 * line-for-line: open the socket, on `onOpen` send the Vonage
 * `sessionId` as UTF-8 bytes, await the JSON `{"msg": "<token>"}`
 * reply, then `close(1000, "ok")`. The socket is closed as soon
 * as the token arrives — no reconnect logic this phase (see
 * `design.md` D2).
 *
 * A 10-second timeout fires if no token arrives.
 *
 * This class is intentionally tiny — the WebSocket handshake is a
 * one-shot, no-state operation. State and connection lifecycle for
 * the actual video session live in `VonageSessionController`.
 */
@Singleton
class SessionTokenService @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun fetchToken(sessionId: String): Result<String> =
        fetchToken(sessionId, DEFAULT_TIMEOUT_MS, OkHttpWebSocketFactory)

    internal suspend fun fetchToken(
        sessionId: String,
        timeoutMs: Long,
        factory: WebSocketFactory,
    ): Result<String> {
        val deferred = CompletableDeferred<Result<String>>()
        val listener = TokenListener(sessionId, deferred)
        val webSocket = try {
            factory.create(
                client,
                Request.Builder().url(WS_URL).build(),
                listener,
            )
        } catch (t: Throwable) {
            return Result.failure(VideoCallError.Error(t))
        }
        listener.webSocket = webSocket

        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            webSocket.close(1000, "timeout")
            Result.failure(VideoCallError.SocketTimeout)
        } catch (e: CancellationException) {
            // The caller went away (a retry superseded this attempt, or
            // the screen was dismissed). Swallowing the cancellation
            // would let the caller treat it as a token failure and tear
            // down the *live* session that replaced it.
            webSocket.close(1000, "cancelled")
            throw e
        } catch (t: Throwable) {
            Result.failure(VideoCallError.Error(t))
        }
    }

    private class TokenListener(
        private val sessionId: String,
        private val deferred: CompletableDeferred<Result<String>>,
    ) : WebSocketListener() {
        var webSocket: WebSocket? = null

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "Token socket open — sending sessionId='$sessionId'")
            webSocket.send(sessionId.toByteArray().toByteString(0, sessionId.toByteArray().size))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            webSocket.close(1000, "ok")
            val token = parseToken(text)
            Log.i(
                TAG,
                "Token received for sessionId='$sessionId' (length=${token?.length ?: 0}, " +
                    "present=${token != null})",
            )
            deferred.complete(
                if (token != null) Result.success(token)
                else Result.failure(VideoCallError.FailedToGetToken),
            )
        }

        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
            onMessage(webSocket, bytes.utf8())
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Token socket failure for sessionId='$sessionId': ${t.message}")
            if (!deferred.isCompleted) {
                deferred.complete(Result.failure(VideoCallError.SocketFailedToConnect))
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "Token socket closed code=$code reason=$reason (sessionId='$sessionId')")
            if (!deferred.isCompleted) {
                deferred.complete(Result.failure(VideoCallError.SocketFailedToConnect))
            }
        }

        private fun parseToken(text: String): String? = try {
            JSONObject(text).optString("msg").takeIf { it.isNotEmpty() }
        } catch (_: JSONException) {
            null
        }
    }

    companion object {
        private const val WS_URL = "ws://ws.kornerz.com:80/"
        private const val DEFAULT_TIMEOUT_MS = 10_000L
        private const val TAG = "VideoCallToken"
    }
}