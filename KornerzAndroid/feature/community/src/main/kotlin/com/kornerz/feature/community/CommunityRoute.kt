package com.kornerz.feature.community

/**
 * Top-level routes for the P7 admin-owned-communities vertical.
 * Pushed onto the root shell NavHost (not nested in any tab) so the
 * bottom bar can be hidden via the regex in `AppShell`.
 *
 * Mirrors iOS `UserProfileVC.hidesBottomBarWhenPushed = true` for
 * `openMyCommunities()` and the subsequent push to
 * `CommunityDetailAsAdminVC` (lines 26–29 of
 * `AdminCommunityListController`).
 *
 * **Note:** the P7 `Create Community` wizard route was removed after
 * the iOS audit — iOS `UserProfileVC.setupMenuList()` does not
 * surface a "Create Community" row and the `CreateCommunityHomeVC` /
 * `NewCommunityNameVC` / `NewCommunityDescriptionVC` /
 * `SelectCommunityThemeVC` classes are unreachable dead code on
 * iOS. Android now mirrors iOS exactly. The wizard view-models and
 * view-controllers have been removed from the module.
 */
sealed class CommunityRoute {
    /** The admin-owned-communities list (iOS `AdminCommunityListController`). */
    data object AdminList : CommunityRoute() {
        const val route: String = "community/admin/list"
    }

    /**
     * The read-only community-detail stats dashboard pushed after a
     * row tap on the admin list (iOS `CommunityDetailAsAdminVC`).
     * Carries the 6-char community `code` as a nav argument.
     */
    data object AdminDetail : CommunityRoute() {
        const val route: String = "community/admin/detail/{code}"

        const val ARG_CODE: String = "code"

        fun build(code: String): String = "community/admin/detail/$code"
    }

    /**
     * The create-forum (create loop) wizard pushed from the admin detail
     * screen's "forums" pill (iOS `CommunityDetailAsAdminVC.createForum`
     * → `AddForumTitleScreen`). Carries the owned community's `communityId`
     * so the new loop is written to the right community.
     */
    data object CreateForum : CommunityRoute() {
        const val route: String = "community/create-forum/{communityId}"

        const val ARG_COMMUNITY_ID: String = "communityId"

        /**
         * The route prefix shared by every destination of the create-loop
         * flow. The wizard is a full-screen flow, so the shell hides the
         * bottom bar across all of it — the prefix keeps that rule true
         * whatever destination the flow is on.
         */
        const val ROUTE_PREFIX: String = "community/create-forum"

        fun build(communityId: String): String = "$ROUTE_PREFIX/$communityId"
    }
}
