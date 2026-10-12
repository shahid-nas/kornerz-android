package com.kornerz.core.model

/**
 * Carries the Vonage session token issued by the backend WebSocket
 * service (`ws://ws.kornerz.com:80/`). The token is opaque to the
 * client — it is consumed verbatim by `VonageSessionController`
 * when calling `Session.connect(token)`.
 *
 * NOT `@Serializable` — the token has no persistence need.
 */
data class SessionToken(
    val token: String,
)