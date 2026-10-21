package com.kornerz.feature.community

/**
 * Top-level route for the P7 admin-owned-communities list
 * (P7, task 9.1). Pushed onto the root shell NavHost (not nested in
 * any tab) so the bottom bar can be hidden via the regex in `AppShell`.
 *
 * Mirrors iOS `UserProfileVC.hidesBottomBarWhenPushed = true` for
 * `openMyCommunities()`.
 *
 * **Note:** the P7 `Create Community` wizard route was removed after
 * the iOS audit — iOS `UserProfileVC.setupMenuList()` does not
 * surface a "Create Community" row and the `CreateCommunityHomeVC` /
 * `NewCommunityNameVC` / `NewCommunityDescriptionVC` /
 * `SelectCommunityThemeVC` classes are unreachable dead code on
 * iOS. Android now mirrors iOS exactly and exposes only the admin
 * list. The wizard view-models and view-controllers have been
 * removed from the module.
 */
sealed class CommunityRoute {
    /** The admin-owned-communities list (10.1 / 10.2). */
    data object AdminList : CommunityRoute() {
        const val route: String = "community/admin/list"
    }
}
