package com.kornerz.feature.kornerz.detail

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.KornerDetail
import com.kornerz.core.model.Loop
import com.kornerz.core.model.User

/**
 * UiState for the Korner detail screen (spec `korner-detail/spec.md`).
 *
 * Holds the aggregate `KornerDetail` payload wrapped in [AsyncValue]
 * (architecture §2.2) plus the two blocks iOS fetches alongside it:
 *
 *  - [kornerstones] — the Korner's **admins** (iOS
 *    `KornerDetailViewModel.fetchLeaders`, the `CommunityAdm`
 *    collection). They populate the "Kornerstones" tab.
 *  - [recentLoops] — the Korner's latest loops (iOS
 *    `KornerDetailViewModel.fetchLoops`, the `Loop` collection). They
 *    populate the "Recently in &lt;community&gt;" section.
 *
 * The **Klouders** tab is fed by `KornerDetail.members` — the
 * `Fellow` records, which iOS calls the community's followers — so it
 * needs no extra state.
 *
 * The two tab selections are screen state (they survive a recomposition
 * but not a process death), which is why they live here rather than in
 * the composable.
 */
data class KornerDetailState(
    val detail: AsyncValue<KornerDetail> = AsyncValue.Loading,
    val kornerstones: List<User> = emptyList(),
    val recentLoops: List<Loop> = emptyList(),
    val selectedNookTab: KornerDetailNookTab = KornerDetailNookTab.Current,
    val selectedUserTab: KornerDetailUserTab = KornerDetailUserTab.Kornerstones,
    /** Whether the iOS-style "About" bottom sheet is presented. */
    val isAboutSheetVisible: Boolean = false,
    /**
     * Whether a **pull-to-refresh** read is in flight. Distinct from
     * `detail is Loading`, which also covers the very first load — the
     * initial load renders the shimmer, a refresh keeps the content on
     * screen under the refresh indicator.
     */
    val isRefreshing: Boolean = false,
)
