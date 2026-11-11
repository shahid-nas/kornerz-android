package com.kornerz.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Korner domain model used by the Kornerz tab and Korner detail screens.
 *
 * Mirrors the public subset of iOS `Model/Community.swift` consumed by the
 * Phase 3 Android screens. Distinct from the narrower `Community` data
 * class used by the Home tab (`home-your-kornerz`) because the Kornerz
 * tab is a discovery surface and needs the full description, members
 * count, live flag, and joined flag.
 */
@Serializable
data class Korner(
    val id: String,
    val name: String,
    val themeColor: String,
    @SerialName("firebaseImageUrl") val coverUrl: String? = null,
    val description: String? = null,
    val membersCount: Int = 0,
    @SerialName("isLive") val isLive: Boolean = false,
    @SerialName("isJoined") val isJoined: Boolean = false,
    // P9: Vonage session id for the live Kornerz's nook; populated by
    // the Firestore API when the Korner has a live nook associated.
    @SerialName("sessionId") val sessionId: String? = null,
    // Firestore `communityId` value — distinct from `id` (which is the
    // Firestore document id). P9 uses this to join the Korner with
    // the matching live `Events/{nookId}` document's `sessionId`.
    @SerialName("communityId") val communityId: String? = null,
    // Live-session roster for the Korner's active room, resolved from
    // the `getSessionsInfo` Cloud Function. `null` when the Korner has
    // no session or the lookup failed — the card then falls back to the
    // iOS "nobody is attending" copy. Mirrors iOS
    // `KornerWrapper.sessionInfo`.
    val sessionInfo: SessionInfo? = null,
)
