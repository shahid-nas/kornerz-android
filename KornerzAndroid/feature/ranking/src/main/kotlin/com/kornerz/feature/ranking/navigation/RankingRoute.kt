package com.kornerz.feature.ranking.navigation

/**
 * The Klouders tab's destination.
 *
 * The Klouders tab hosts the leaderboard directly — unlike the Home /
 * Kornerz / Nooks / Loops / Profile tabs, it has **no** nested
 * `NavHost`, because the leaderboard is a single screen with no detail
 * destination. The tab route and the leaderboard route are therefore
 * the same string, and the shell registers the destination on its
 * root `NavHost`.
 *
 * Mirrors the per-feature route objects used by P4 (`nooks`), P5
 * (`loops`) and P6 (`profile`).
 */
object RankingRoute {
    const val LIST: String = "tab/klouders"
}
