package com.kornerz.core.network.videocall

/**
 * A live snapshot of everything the call pipeline knows about the
 * session, surfaced on the call screen so the state of a call can be
 * inspected without reading logcat.
 *
 * The question this exists to answer is "why does this device not see
 * the other participants?", which has three very different answers:
 *
 *  1. the device is in a different Vonage room than its peers
 *     → [token] shows `match=false`;
 *  2. the token does not allow the device to receive streams
 *     → [token] shows `role=publisheronly` (or a missing role);
 *  3. the room is right and the token is fine, but the SDK never
 *     delivered the peers' streams
 *     → [connection] shows `peers=1` while [subscribers] stays empty.
 *     That last case is a client-side bug, not a server-side one:
 *     the SDK's callback queue is bound to the thread that built the
 *     session, and a slow callback on that thread delays the delivery
 *     of the stream events.
 */
data class VideoCallDiagnostics(
    /** The room the app asked the backend for. */
    val requestedRoom: String = "—",
    /** The room the token actually grants, plus the role/scope/layout. */
    val token: String = "—",
    /** Session state and the number of negotiated peer connections. */
    val connection: String = "—",
    /** The local publisher's stream id, once it is published. */
    val publisher: String = "—",
    /**
     * What the server lets this client do in the room —
     * `canPublish` / `canSubscribe`.
     *
     * `canSubscribe=false` is the smoking gun for "the peer never
     * appears on this device": the token was minted without subscribe
     * rights, so the server never pushes the other clients' streams to
     * this connection.
     */
    val capabilities: String = "—",
    /** The remote subscribers the SDK has delivered. */
    val subscribers: String = "—",
    /** The most recent SDK callback, with its payload. */
    val lastCallback: String = "—",
)
