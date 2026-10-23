package com.kornerz.core.model

/**
 * The leaderboard medal tier of a ranked row.
 *
 * Promoted out of the former `UserRanking.Medal` nested enum so the
 * medal vocabulary is a first-class domain concept rather than a
 * detail of a row model — the Klouders screen renders the tier and the
 * data layer assigns it.
 *
 * Mirrors iOS `UserRanking.UserMadal` (`kornerz/Kornerz/HomeVC/Ranking/
 * RankingViewModel.swift:30-46`). The tier is assigned by the row's
 * **final position** in the sorted leaderboard: gold, silver, bronze
 * for the first three rows, none after — iOS
 * `GamificationService.fillMadalInformation` (`:154-166`).
 */
enum class Rank {
    GOLD,
    SILVER,
    BRONZE,
    NONE,
}
