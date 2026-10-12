package com.kornerz.feature.nooks.list

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Korner
import com.kornerz.core.model.UpcomingNookItem
import com.kornerz.core.ui.components.header.TabHeaderData

/**
 * UiState for the Nooks tab list (architecture §2.2).
 *
 * Two independent paging state machines:
 *  - [live] — page of approved Korners ordered by `viewsCount` desc.
 *    Mirrors iOS `NooksTabViewModel.kornerz`.
 *  - [upcoming] — page of open Events joined with their parent
 *    Korner and publisher. Mirrors iOS
 *    `NooksTabViewModel.nooks`.
 *
 * [selectedType] drives which section is rendered (mirrors iOS
 * `NooksType`). [header] mirrors the iOS `HomeHeaderView` row that
 * iOS embeds on every tab — shared via `:core:ui` so Android tabs can
 * match (architecture §2.5).
 */
data class NooksState(
    val header: AsyncValue<TabHeaderData> = AsyncValue.Loading,
    val selectedType: NooksType = NooksType.Live,
    val live: AsyncValue<LiveSectionState> = AsyncValue.Loading,
    val upcoming: AsyncValue<UpcomingSectionState> = AsyncValue.Loading,
)

/** Live-tab section state — list of Korners plus paging flags. */
data class LiveSectionState(
    val items: List<Korner> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
)

/** Upcoming-tab section state — list of joined items plus paging flags. */
data class UpcomingSectionState(
    val items: List<UpcomingNookItem> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
)