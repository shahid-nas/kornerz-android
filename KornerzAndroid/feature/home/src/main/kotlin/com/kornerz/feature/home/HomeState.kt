package com.kornerz.feature.home

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Community
import com.kornerz.core.model.Loop
import com.kornerz.core.model.Nook
import com.kornerz.core.model.User

/**
 * Immutable render state for the Home tab root. Every section is
 * exposed as an `AsyncValue<…>` (architecture §2.2) so the UI layers
 * pattern-match exhaustively on `Loading` / `Success(empty)` /
 * `Success(items)` / `Error`. No `isLoading` booleans anywhere.
 */
data class HomeState(
    val header: AsyncValue<HomeHeaderData> = AsyncValue.Loading,
    val loopOfDay: AsyncValue<Loop> = AsyncValue.Loading,
    val upcomingNooks: AsyncValue<List<Nook>> = AsyncValue.Loading,
    val yourKornerz: AsyncValue<List<Community>> = AsyncValue.Loading,
    val recentLoops: AsyncValue<List<Loop>> = AsyncValue.Loading,
)

/** Avatar / points projection for the home header. */
data class HomeHeaderData(
    val user: User,
    val points: Int,
)
