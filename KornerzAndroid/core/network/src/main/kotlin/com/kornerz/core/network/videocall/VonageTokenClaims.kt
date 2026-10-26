package com.kornerz.core.network.videocall

import android.util.Base64
import android.util.Log
import org.json.JSONObject

/**
 * Best-effort decode of the Vonage session token's JWT payload.
 *
 * The token the backend hands out over `ws://ws.kornerz.com:80/` is a
 * JWT, and its payload carries the room the client was actually
 * granted:
 *
 *  - `session_id` — the Vonage session (room) the client may join.
 *    If this does not match the session id the app asked for, the
 *    client is in a *different* room than its peers and no remote
 *    stream will ever arrive.
 *  - `role` — `publisher`, `subscriber`, `moderator` or `publisheronly`.
 *    A subscriber-only token cannot publish, and a publisher-only
 *    token still receives streams.
 *  - `scope` — `session.connect`, `session.publish`, …
 *  - `initial_layout_class_list` — the layout classes assigned to the
 *    streams **this client publishes**, used to compose archives and
 *    broadcasts. It is **not** a subscription filter: an empty list
 *    is the default of the official Vonage server SDKs and does not
 *    stop the client from receiving the other clients' streams.
 *  - `exp` — the expiry.
 *
 * None of this is a secret — the payload is base64, not encrypted —
 * but it is still a credential, so only the *claims* are logged, never
 * the token itself.
 */
object VonageTokenClaims {

    private const val TAG = "VideoCallTokenClaims"

    /**
     * Returns the token's claims, or an empty map when the token is
     * not a decodable JWT.
     */
    fun of(token: String): Map<String, Any?> {
        val parts = token.split('.')
        if (parts.size < 2) {
            Log.w(TAG, "Token is not a JWT (segments=${parts.size})")
            return emptyMap()
        }
        return runCatching {
            val normalized = parts[1].replace('-', '+').replace('_', '/')
            val padded = normalized.padEnd((normalized.length + 3) / 4 * 4, '=')
            val decoded = String(Base64.decode(padded, Base64.NO_WRAP))
            val json = JSONObject(decoded)
            json.keys().asSequence().associateWith { key -> json.get(key) }
        }.getOrElse {
            Log.w(TAG, "Could not decode the token payload: ${it.message}")
            emptyMap()
        }
    }

    /**
     * A one-line summary of what the token grants, rendered on the
     * call screen's diagnostics panel.
     */
    fun summary(token: String, requestedSessionId: String): String {
        val claims = of(token)
        if (claims.isEmpty()) return "unreadable token"
        val granted = claims["session_id"]?.toString() ?: "?"
        val role = claims["role"]?.toString() ?: "?"
        val scope = claims["scope"]?.toString() ?: "?"
        val layout = claims["initial_layout_class_list"]?.toString() ?: "?"
        return "room=$granted role=$role scope=$scope layout=$layout match=${granted == requestedSessionId}"
    }

    /**
     * Logs the room/role/scope the token grants. Call this right after
     * the token arrives — it is the single most useful line in the
     * whole call pipeline when a client does not see its peers.
     */
    fun log(token: String, requestedSessionId: String) {
        val claims = of(token)
        if (claims.isEmpty()) {
            Log.w(TAG, "No token claims available for the requested sessionId=$requestedSessionId")
            return
        }
        val grantedSessionId = claims["session_id"]?.toString()
        val role = claims["role"]?.toString()
        val scope = claims["scope"]?.toString()
        val layout = claims["initial_layout_class_list"]
        Log.i(
            TAG,
            "requested sessionId=$requestedSessionId | granted session_id=$grantedSessionId | " +
                "role=$role | scope=$scope | publishedStreamLayoutClasses=$layout | " +
                "sessionMatch=${grantedSessionId == requestedSessionId}",
        )
        // Every claim, so nothing is hidden behind a typo in a claim
        // name (the token is a credential — only the decoded claims are
        // printed, never the token itself).
        Log.i(TAG, "all token claims: ${claims.entries.joinToString { "${it.key}=${it.value}" }}")

        if (grantedSessionId != requestedSessionId) {
            Log.e(
                TAG,
                "The token grants room '$grantedSessionId' but the app asked for " +
                    "'$requestedSessionId' — this client is in a DIFFERENT room than its peers.",
            )
        }
        if (role != null && role.contains("publisheronly", ignoreCase = true)) {
            Log.e(
                TAG,
                "The token role is '$role' — a publisher-only token can publish but can " +
                    "NOT subscribe to other clients' streams. Remote subscribers will never " +
                    "appear on this device.",
            )
        }
        if (role != null && !role.contains("publisher", ignoreCase = true) &&
            !role.contains("subscriber", ignoreCase = true) &&
            !role.contains("moderator", ignoreCase = true)
        ) {
            Log.e(
                TAG,
                "The token role is '$role' — this is not one of Vonage's documented roles " +
                    "(publisher, subscriber, publisheronly, moderator). The client may not " +
                    "be allowed to subscribe to the other clients' streams.",
            )
        }
        if (scope != null && !scope.contains("session.connect", ignoreCase = true)) {
            Log.e(TAG, "The token scope '$scope' does not include session.connect.")
        }
    }
}
