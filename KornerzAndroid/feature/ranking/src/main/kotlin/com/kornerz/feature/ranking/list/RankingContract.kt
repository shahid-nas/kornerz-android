package com.kornerz.feature.ranking.list

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.UserRanking

/**
 * The Klouders leaderboard's UI state.
 *
 * Follows the architecture's UDF contract (architecture §2.2): the
 * rows are exposed as an [AsyncValue] and the screen pattern-matches
 * exhaustively on `Loading` / `Success` / `Error` — no `isLoading`
 * booleans and no try/catch ladders.
 */
data class RankingState(
    val rankings: AsyncValue<List<UserRanking>> = AsyncValue.Loading,
)
