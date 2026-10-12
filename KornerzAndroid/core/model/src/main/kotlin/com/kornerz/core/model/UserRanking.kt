package com.kornerz.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of the global gamification leaderboard. Mirrors iOS
 * `UserRanking` (`kornerz/Kornerz/Models/Gamification.swift`). The
 * `madal` value is a medal tier — the P10 polish phase will surface
 * this on a leaderboard screen; P6 only needs the points count.
 */
@Serializable
data class UserRanking(
    @SerialName("userId") val userId: String,
    @SerialName("userName") val userName: String = "",
    @SerialName("points") val points: Int = 0,
    @SerialName("madal") val medal: Medal = Medal.NONE,
) {
    enum class Medal { GOLD, SILVER, BRONZE, NONE }
}
