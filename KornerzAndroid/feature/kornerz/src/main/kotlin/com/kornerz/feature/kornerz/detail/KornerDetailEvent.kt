package com.kornerz.feature.kornerz.detail

/**
 * UiEvent for the Korner detail screen.
 *
 * Emitted by the content composable; the VM forwards them to either
 * the navigation channel or the local state mutator.
 */
sealed interface KornerDetailEvent {
    /** Re-reads the Korner after a failure. */
    data object Retry : KornerDetailEvent

    /**
     * Pull-to-refresh — re-reads the Korner and keeps the current
     * content on screen while the read is in flight (iOS
     * `.refreshable { viewModel.fetchData(forcedRefresh: true) }`).
     */
    data object Refresh : KornerDetailEvent

    /** The hero's back chevron was tapped. */
    data object BackClicked : KornerDetailEvent

    /** The hero's (or the About sheet's) Follow / Unfollow capsule was tapped. */
    data object FollowClicked : KornerDetailEvent

    /** The hero's "About ⌄" affordance was tapped — opens the About sheet. */
    data object AboutClicked : KornerDetailEvent

    /** The About sheet was dismissed. */
    data object AboutDismissed : KornerDetailEvent

    /** The "Current" / "Upcoming" tab of the Nooks section was selected. */
    data class NookTabSelected(val tab: KornerDetailNookTab) : KornerDetailEvent

    /** The "Kornerstones" / "Klouders" tab of the users section was selected. */
    data class UserTabSelected(val tab: KornerDetailUserTab) : KornerDetailEvent

    /**
     * The carousel's "View All" tile was tapped — opens the sheet
     * listing every member of [tab].
     *
     * Mirrors iOS `KornerUsersView.viewAllButton(tab:)`, which sets
     * `viewModel.usersSheet = tab.sheet`.
     */
    data class ViewAllClicked(val tab: KornerDetailUserTab) : KornerDetailEvent

    /** The "View All" sheet was dismissed. */
    data object UsersSheetDismissed : KornerDetailEvent

    /**
     * The "View All" sheet scrolled to its end — the next page of
     * members is requested.
     *
     * Mirrors iOS `KornerUsersListViewModel.loadNextPage()`, which the
     * sheet's trailing load-more trigger calls.
     */
    data object UsersSheetLoadMore : KornerDetailEvent

    /** A nook card was tapped — opens the nook detail. */
    data class NookTileClicked(val nookId: String) : KornerDetailEvent

    /** A user avatar was tapped — opens the member profile. */
    data class MemberTileClicked(val userId: String) : KornerDetailEvent

    /**
     * A "Recently in" loop row was tapped — opens the loop detail.
     *
     * [accentColorHex] is the colour the tapped row was painted with,
     * so the detail opens in the very same colour (iOS hands
     * `loopInfo.color` to the loop detail).
     */
    data class LoopTileClicked(
        val loopId: String,
        val accentColorHex: String,
    ) : KornerDetailEvent

    /** A nook card's "Join" capsule was tapped. */
    data class JoinCtaClicked(val nookId: String) : KornerDetailEvent
}
