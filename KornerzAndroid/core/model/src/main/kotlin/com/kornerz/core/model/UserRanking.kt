package com.kornerz.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of the global gamification leaderboard. Mirrors iOS
 * `UserRanking` (`kornerz/Kornerz/HomeVC/Ranking/RankingViewModel.swift`).
 *
 * The `madal` value is the medal tier — the P10 Klouders leaderboard
 * surfaces it on the first three rows. The property type is the
 * top-level [Rank] enum; the `@SerialName("madal")` spelling is the
 * backend's and is preserved verbatim.
 */
@Serializable
data class UserRanking(
    @SerialName("userId") val userId: String,
    @SerialName("userName") val userName: String = "",
    @SerialName("points") val points: Int = 0,
    @SerialName("madal") val medal: Rank = Rank.NONE,
)
