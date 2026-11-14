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
    /** Whether the next page of recent loops is being fetched. */
    val isLoadingMoreRecentLoops: Boolean = false,
    /** Whether another page of recent loops is available. */
    val hasMoreRecentLoops: Boolean = true,
    val selectedNookTab: KornerDetailNookTab = KornerDetailNookTab.Current,
    val selectedUserTab: KornerDetailUserTab = KornerDetailUserTab.Kornerstones,
    /** Whether the iOS-style "About" bottom sheet is presented. */
    val isAboutSheetVisible: Boolean = false,
    /**
     * The state of the "View All" sheet — the full vertical list of a
     * tab's members — or `null` when the sheet is not presented.
     *
     * iOS holds the presented sheet in `KornerDetailViewModel.usersSheet`
     * (a `KornerUsersSheet?`) and the sheet's own paging in a separate
     * `KornerUsersListViewModel`. Android keeps the paging state here
     * and nulls it out when the sheet is dismissed, the way the About
     * sheet's flag works.
     */
    val usersSheet: KornerUsersSheetState? = null,
    /**
     * Whether a **pull-to-refresh** read is in flight. Distinct from
     * `detail is Loading`, which also covers the very first load — the
     * initial load renders the shimmer, a refresh keeps the content on
     * screen under the refresh indicator.
     */
    val isRefreshing: Boolean = false,
    /**
     * The accent colour the screen was opened with, as a `#RRGGBB`
     * hex string.
     *
     * iOS hands the **tile's** colour to the detail screen
     * (`HomeTabCoordinator.navigateToKornerDetail(_:color:)` and
     * `KornerzTabCoordinator.navigateToCommunityDetail(_:color:)`), so
     * the detail paints itself in the very colour the user tapped.
     * Android carries the same colour as a nav argument; it is empty
     * when the destination was opened without one — a deep link, a
     * nook detail — and the screen falls back to the Korner
     * document's own theme colour.
     */
    val handoverThemeColorHex: String? = null,
) {
    /**
     * The colour the screen paints itself with.
     *
     * The handover colour wins — it is the colour the user tapped —
     * and the Korner document's own theme colour is the fallback. The
     * brand Coral swatch is the last resort, mirroring iOS's
     * `.init(korner: .shimmer, color: .gray, …)` placeholder.
     */
    val resolvedThemeColorHex: String
        get() = handoverThemeColorHex?.takeIf { isHexColor(it) }
            ?: (detail as? AsyncValue.Success)?.data?.korner?.themeColor?.takeIf { isHexColor(it) }
            ?: DEFAULT_THEME_COLOR_HEX

    private companion object {
        /** The brand Coral swatch — the same default the Home carousel uses. */
        const val DEFAULT_THEME_COLOR_HEX = "#E27D5A"

        /** Whether [hex] is a six-digit hex colour, with or without the `#`. */
        fun isHexColor(hex: String): Boolean {
            val digits = hex.trim().removePrefix("#")
            return digits.length == 6 && digits.all { it.isHexDigit() }
        }

        fun Char.isHexDigit(): Boolean =
            this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}
