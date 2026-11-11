package com.kornerz.core.model

import kotlinx.serialization.Serializable

/**
 * Live-session roster for one video-call room, as returned by the
 * `getSessionsInfo` Cloud Function.
 *
 * Mirrors iOS `Models/ConnectionCountResponse.swift`:
 *  - `SessionInfo` ← `SessionInfo` (one entry per session id)
 *  - `SessionInfoUser` ← one entry per participant, flattened into
 *    [attendeeNames]
 *
 * **Payload quirk.** The Cloud Function returns each attendee's `name`
 * as a *JSON-encoded string* rather than a plain string, e.g.
 * `"name": "{\"userName\":\"Ada\"}"`. iOS parses it with
 * `JSONDecoder().decode([String: String].self, ...)` and reads the
 * `userName` key (see iOS `SessionInfoUser.userName`). [attendeeNames]
 * reproduces that decode.
 *
 * `@Serializable` because [Korner], [Nook] and [UpcomingNookItem] all
 * carry it; the raw wire payload is parsed by the network layer, which
 * owns the wire format.
 */
@Serializable
data class SessionInfo(
    /** Number of participants currently connected to the room. */
    val connections: Int = 0,
    /** Participant display names, in the order the backend returned them. */
    val attendeeNames: List<String> = emptyList(),
) {
    /**
     * The iOS `SessionInfo.nookTitle` string rendered on the Nooks card.
     *
     * iOS (`ConnectionCountResponse.swift:42-51`):
     *  - no attendees → localized `no_user_in_session_msg`
     *  - one attendee → `"<name> is attending the session"`
     *  - many → `"<a>, <b> are attending the session"`
     */
    val nookTitle: String
        get() = when (attendeeNames.size) {
            0 -> NO_USER_IN_SESSION_MSG
            1 -> "${attendeeNames.first()} is attending the session"
            else -> "${attendeeNames.joinToString(", ")} are attending the session"
        }

    companion object {
        /**
         * English copy of the iOS `no_user_in_session_msg` localization
         * key (`Localizable.xcstrings` → `"No one is attending the
         * session. Be the first to join!"`).
         */
        const val NO_USER_IN_SESSION_MSG = "No one is attending the session. Be the first to join!"
    }
}
