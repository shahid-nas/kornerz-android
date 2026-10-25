package com.kornerz.feature.ranking.list

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.UserRanking
import com.kornerz.core.ui.components.header.TabHeaderData

/**
 * The Klouders leaderboard's UI state.
 *
 * Follows the architecture's UDF contract (architecture §2.2): the
 * rows are exposed as an [AsyncValue] and the screen pattern-matches
 * exhaustively on `Loading` / `Success` / `Error` — no `isLoading`
 * booleans and no try/catch ladders.
 *
 * [header] is the shared avatar + points row every bottom-bar tab
 * renders (architecture §2.5 — `TabHeaderContent` in `:core:ui`).
 */
data class RankingState(
    val header: AsyncValue<TabHeaderData> = AsyncValue.Loading,
    val rankings: AsyncValue<List<UserRanking>> = AsyncValue.Loading,
)

/** The English value of iOS's `klouders` localisation key. */
const val SCREEN_TITLE: String = "Klouders"
