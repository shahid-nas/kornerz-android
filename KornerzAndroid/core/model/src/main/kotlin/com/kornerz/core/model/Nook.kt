package com.kornerz.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Home-only subset of an Event (the iOS concept renamed to "Nook" on
 * Android). Upcoming nooks only need id, community, title, start date
 * and a live flag. The full Nook schema (attendees, location, RSVP)
 * lands in P6 (Nooks phase).
 */
@Serializable
data class Nook(
    val id: String,
    @SerialName("communityId") val communityId: String,
    val title: String,
    @SerialName("startDate") val startDate: Instant,
    @SerialName("isLive") val isLive: Boolean = false,
)
