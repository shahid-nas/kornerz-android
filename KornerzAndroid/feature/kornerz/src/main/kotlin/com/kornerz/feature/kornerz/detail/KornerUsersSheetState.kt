package com.kornerz.feature.kornerz.detail

import com.kornerz.core.model.User

/**
 * The UiState of the "View All" sheet — the vertical list of every
 * Kornerstone / Klouder a Korner has. 1:1 port of iOS
 * `KornerUsersListViewModel`
 * (`kornerz/Kornerz/SwiftUI/Dashboard/Korner/Sections/KornerUsersListViewModel.swift`).
 *
 * iOS keeps the whole roster of user **codes** in the parent ViewModel
 * (`KornerDetailViewModel.leadersIds` / `followersIds`) and slices it
 * page by page:
 *
 * ```swift
 * let start = offset
 * let end = min(start + pageSize, ids.count)
 * let chunk = Array(ids[start..<end])
 * usersRepository.fetchUsers(in: chunk) { … self.users.append(contentsOf: users); self.offset = end }
 * ```
 *
 * The Android port keeps the same shape: the sheet owns the codes, the
 * offset, and the users resolved so far.
 *
 * @param title the sheet's title — "Kornerstones" or "Klouders".
 * @param isLeader whether the rows render the **triangle** avatar
 *   (Kornerstones) or the **diamond** one (Klouders). Mirrors iOS
 *   `KornerUsersSheet.isLeader`.
 * @param codes the full roster of user codes, de-duplicated and sorted.
 * @param users the users resolved so far, in roster order.
 * @param offset how many codes have been consumed so far — the index
 *   of the next page.
 * @param isLoading whether a page read is in flight.
 * @param isRosterLoading whether the roster itself is still being read.
 *   The roster read and the page reads are two separate calls, so they
 *   are two separate flags.
 * @param hasLoaded whether the first page has resolved. iOS uses the
 *   same flag to distinguish "still loading" from "loaded, and the
 *   roster is genuinely empty".
 */
data class KornerUsersSheetState(
    val title: String = "",
    val isLeader: Boolean = true,
    val codes: List<String> = emptyList(),
    val users: List<User> = emptyList(),
    val offset: Int = 0,
    val isLoading: Boolean = false,
    val isRosterLoading: Boolean = false,
    val hasLoaded: Boolean = false,
) {
    /**
     * Whether another page is waiting to be fetched. Mirrors iOS
     * `KornerUsersListViewModel.hasMore` — `offset < ids.count`.
     */
    val hasMore: Boolean get() = offset < codes.size

    /**
     * Whether the sheet should show its empty state. Mirrors iOS
     * `KornerUsersListView.content`: the empty message appears only
     * once the first read has resolved and came back empty.
     */
    val isEmpty: Boolean get() = hasLoaded && users.isEmpty()

    /**
     * Whether the sheet is still waiting on its very first read — the
     * roster itself, or the first page of users from it. The sheet
     * shows a spinner for this state.
     */
    val isInitialLoad: Boolean get() = isRosterLoading || (!hasLoaded && isLoading)
}
