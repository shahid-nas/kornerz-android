package com.kornerz.feature.kornerz

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Korner
import com.kornerz.core.ui.components.header.TabHeaderData

/**
 * UiState for the Kornerz tab. Composes the three carousel sections —
 * Your Kornerz, Recommended for you, New this week — each backed by
 * an independent `AsyncValue<List<Korner>>` (architecture §2.2).
 *
 * [header] mirrors the iOS `HomeHeaderView` row that iOS embeds at
 * the top of every tab; lifted into a shared component in `:core:ui`
 * so Android tabs can match (architecture §2.5).
 *
 * Sections hide themselves when their wrapped value is
 * `Success(empty)` per spec `kornerz-tab/spec.md`. An `Error` shows
 * a small retry row inside the section.
 */
data class KornerzState(
    val header: AsyncValue<TabHeaderData> = AsyncValue.Loading,
    val yourKornerz: AsyncValue<List<Korner>> = AsyncValue.Loading,
    val recommended: AsyncValue<List<Korner>> = AsyncValue.Loading,
    val newThisWeek: AsyncValue<List<Korner>> = AsyncValue.Loading,
)