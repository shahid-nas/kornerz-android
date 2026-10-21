package com.kornerz.feature.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kornerz.feature.home.HomeScreen
import com.kornerz.feature.kornerz.KornerzRoute
import com.kornerz.feature.kornerz.KornerzScreen
import com.kornerz.feature.kornerz.KornerzNavEvent
import com.kornerz.feature.kornerz.detail.KornerDetailNavEvent
import com.kornerz.feature.kornerz.detail.KornerDetailScreen
import com.kornerz.feature.kornerz.detail.KornerDetailViewModel
import com.kornerz.feature.loops.LoopsRoute
import com.kornerz.feature.loops.comment.CreateCommentScreen
import com.kornerz.feature.loops.detail.LoopDetailNavEvent
import com.kornerz.feature.loops.detail.LoopDetailScreen
import com.kornerz.feature.loops.list.LoopsNavEvent
import com.kornerz.feature.loops.list.LoopsScreen
import com.kornerz.feature.loops.loopsCommentNavArguments
import com.kornerz.feature.loops.loopsDetailNavArguments
import com.kornerz.feature.loops.loopsReplyNavArguments
import com.kornerz.feature.loops.reply.ReplyScreen
import com.kornerz.feature.notifications.NotificationsRoute
import com.kornerz.feature.notifications.list.NotificationsScreen
import com.kornerz.feature.nooks.NooksRoute
import com.kornerz.feature.nooks.detail.NookDetailNavEvent
import com.kornerz.feature.nooks.detail.NookDetailScreen
import com.kornerz.feature.nooks.detail.NookDetailViewModel
import com.kornerz.feature.nooks.list.NooksNavEvent
import com.kornerz.feature.nooks.list.NooksScreen
import com.kornerz.feature.profile.ProfileRoute
import com.kornerz.feature.profile.changebasicinfo.ChangeBasicInfoScreen
import com.kornerz.feature.profile.editbasicinfo.EditBasicInfoScreen
import com.kornerz.feature.profile.privacy.PrivacyPolicyScreen
import com.kornerz.feature.profile.successbasicinfo.ChangeBasicInfoSuccessScreen
import com.kornerz.feature.profile.view.ProfileScreen
import com.kornerz.feature.community.CommunityRoute
import com.kornerz.feature.community.admin.AdminCommunitiesListScreen
import com.kornerz.feature.community.admin.detail.CommunityDetailScreen
import com.kornerz.feature.community.admin.detail.CommunityDetailViewModel
import com.kornerz.feature.videocall.VideoCallNavEvent
import com.kornerz.feature.videocall.VideocallRoute
import com.kornerz.feature.videocall.videocallGraph
import kotlinx.coroutines.launch

/**
 * Five-tab bottom-navigation shell. Mirrors iOS `AppTabController`.
 *
 * Phase 4 wires the Nooks tab to a nested `NavHost` with two
 * destinations — `nooks/list` (root feed) and `nooks/{nookId}` (per-tab
 * detail). The same `nooks/{nookId}` destination is also hosted as a
 * nested route inside the Kornerz tab's `NavHost` so the Korner
 * detail's nook tiles can push into it without leaving the Kornerz
 * tab. The nook detail screen's "back" button uses
 * `NavController.popBackStack()` so each tab sees its own back
 * behavior — matches iOS `NookDetailView.swift`'s reliance on the
 * system's per-tab `NavigationStack`.
 *
 * The P3 "Coming in Phase 4" snackbar for nook detail is replaced
 * with a real navigation call into the nested `nooks/{nookId}` route.
 */
@Composable
fun AppShell(
    navController: NavHostController = rememberNavController(),
    onLogout: () -> Unit = {},
    viewModel: ShellViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val comingSoonSnackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Cross-tab navigation: the Kornerz tab hosts the
    // `kornerz/{kornerId}` destination inside its own nested
    // `NavController`, so the shell's root controller can't push to
    // it directly. Instead, the shell writes the requested id here
    // and the Kornerz tab consumes it via a `LaunchedEffect` on its
    // own nested controller.
    var pendingKornerId by remember { mutableStateOf<String?>(null) }
    var pendingLoopId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                ShellNavEvent.Logout -> onLogout()
            }
        }
    }

    Scaffold(
        bottomBar = {
            val showBottomBar = currentRoute?.matches(
                Regex("videocall/.*|notifications/list|profile/.*|community/admin/.*"),
            ) == false
            if (showBottomBar) {
                KornerzBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { tab -> navigateToTab(navController, tab) },
                )
            }
        },
        snackbarHost = { SnackbarHost(comingSoonSnackbar) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ShellTab.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            homeTabGraph(
                onNavigateToKornerDetail = { id ->
                    navigateToKornerDetail(navController, id, onPending = { pendingKornerId = id })
                },
                onNavigateToLoopDetail = { id ->
                    navigateToLoopDetail(navController, id, onPending = { pendingLoopId = id })
                },
                onNavigateToProfile = {
                    navController.navigate(ProfileRoute.View.route)
                },
            )
            kornerzTabGraph(
                onNavEvent = { event ->
                    when (event) {
                        KornerzNavEvent.NavigateToProfile ->
                            navController.navigate(ProfileRoute.View.route)
                        is KornerzNavEvent.NavigateToKornerDetail -> {
                            pendingKornerId = event.kornerId
                        }
                    }
                },
                pendingKornerId = pendingKornerId,
                onPendingConsumed = { pendingKornerId = null },
                snackbarHostState = comingSoonSnackbar,
                onNookDetail = { nookId ->
                    navigateToNookDetailInKornerzTab(navController, nookId)
                },
            )
            nooksTabGraph(
                onProfileTap = {
                    navController.navigate(ProfileRoute.View.route)
                },
                onNookDetail = { nookId ->
                    navigateToNookDetailInNooksTab(navController, nookId)
                },
                onKornerDetail = { kornerId ->
                    pendingKornerId = kornerId
                    navController.navigate(ShellTab.Kornerz.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onJoinVideoCall = { sessionId ->
                    navController.navigate(VideocallRoute.Call.build(sessionId))
                },
            )
            loopsTabGraph(
                onProfileTap = {
                    navController.navigate(ProfileRoute.View.route)
                },
                onLoopDetail = { id ->
                    navigateToLoopDetail(navController, id, onPending = { pendingLoopId = id })
                },
                pendingLoopId = pendingLoopId,
                onPendingConsumed = { pendingLoopId = null },
            )
            // P6 (task 9.1) — Profile is pushed onto the root NavHost
            // (mirrors iOS `UserProfileVC.hidesBottomBarWhenPushed`).
            // The bottom-bar visibility regex above matches
            // `profile/.*` so the bar is hidden across every nested
            // Profile destination.
            profileGraph(
                onOpenNotifications = { navController.navigate(NotificationsRoute.LIST) },
                onOpenMyCommunities = { navController.navigate(CommunityRoute.AdminList.route) },
                onOpenLogout = { viewModel.onLogout() },
                onShowError = { msg -> scope.launch { comingSoonSnackbar.showSnackbar(msg) } },
            )
            // P7 (task 9.2) — Admin-owned-communities list is a
            // top-level route (mirrors iOS `UserProfileVC.openMyCommunities`).
            // The P7 create-community wizard route was removed after
            // the iOS audit — iOS does not surface a Create Community
            // row in the profile menu.
            composable(CommunityRoute.AdminList.route) {
                AdminCommunitiesListScreen(
                    onBack = { navController.popBackStack() },
                    onShowError = { msg -> scope.launch { comingSoonSnackbar.showSnackbar(msg) } },
                    onOpenCommunityDetail = { code ->
                        navController.navigate(CommunityRoute.AdminDetail.build(code))
                    },
                )
            }
            // P7+ (community-detail parity) — read-only stats dashboard
            // for an owned community. Pushed after a row tap on the
            // admin list. Mirrors iOS `CommunityDetailAsAdminVC`.
            composable(
                route = CommunityRoute.AdminDetail.route,
                arguments = listOf(
                    navArgument(CommunityDetailViewModel.ARG_CODE) { type = NavType.StringType },
                ),
            ) {
                CommunityDetailScreen(
                    onBack = { navController.popBackStack() },
                    onShowError = { msg -> scope.launch { comingSoonSnackbar.showSnackbar(msg) } },
                )
            }
            // P6 (task 9.1) — Notifications feed (top-level so the
            // bottom bar can be hidden — see the regex above)
            composable(NotificationsRoute.LIST) {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLoopComment = { loopId ->
                        navController.navigate(LoopsRoute.Detail.build(loopId)) {
                            popUpTo(ShellTab.Home.route) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenForumComment = { forumId ->
                        // The forum screen is not in the live flow
                        // for P6 — surface a "coming soon" snackbar
                        // until P7 ships a forum route.
                        scope.launch {
                            comingSoonSnackbar.showSnackbar("Forum coming soon")
                        }
                    },
                    onOpenDeeplink = { deeplink ->
                        scope.launch {
                            comingSoonSnackbar.showSnackbar("Deeplink coming in Phase 10")
                        }
                    },
                    onShowAlert = { msg -> scope.launch { comingSoonSnackbar.showSnackbar(msg) } },
                )
            }
            kloudersTabGraph(onLogout = viewModel::onLogout)
            videocallGraph(
                onNavEvent = { event ->
                    when (event) {
                        VideoCallNavEvent.NavigateBack ->
                            navController.popBackStack()
                    }
                },
            )
        }
    }
}

private fun navigateToTab(navController: NavHostController, tab: ShellTab) {
    navController.navigate(tab.route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun navigateToKornerDetail(
    navController: NavHostController,
    kornerId: String,
    onPending: (String) -> Unit = {},
) {
    onPending(kornerId)
    navController.navigate(ShellTab.Kornerz.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun navigateToNookDetailInKornerzTab(navController: NavHostController, nookId: String) {
    navController.navigate(NooksRoute.Detail.build(nookId))
}

private fun navigateToNookDetailInNooksTab(navController: NavHostController, nookId: String) {
    // Nooks tab's nested NavHost owns the detail route — just push
    // into it via the shell NavController. The nested graph is
    // entered as a child of the Nooks tab, so popBackStack returns
    // to the Nooks tab list.
    navController.navigate(NooksRoute.Detail.build(nookId))
}

private fun navigateToLoopDetail(
    navController: NavHostController,
    loopId: String,
    onPending: (String) -> Unit = {},
) {
    onPending(loopId)
    navController.navigate(ShellTab.Loops.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun androidx.navigation.NavGraphBuilder.tabComposable(
    tab: ShellTab,
) {
    composable(tab.route) {
        EmptyTabScreen(tabName = tab.label, phaseNumber = tab.phase)
    }
}

private fun androidx.navigation.NavGraphBuilder.tabComposableWithLogout(
    tab: ShellTab,
    onLogout: () -> Unit,
) {
    composable(tab.route) {
        EmptyTabScreen(tabName = tab.label, phaseNumber = tab.phase, onLogout = onLogout)
    }
}

internal fun androidx.navigation.NavGraphBuilder.homeTabGraph(
    onNavigateToKornerDetail: (String) -> Unit = {},
    onNavigateToLoopDetail: (String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
) {
    composable(ShellTab.Home.route) {
        HomeScreen(
            modifier = Modifier,
            onNavigateToKornerDetail = onNavigateToKornerDetail,
            onNavigateToLoopDetail = onNavigateToLoopDetail,
            onNavigateToProfile = onNavigateToProfile,
        )
    }
}

/**
 * Nested graph for the Kornerz tab — owns the list, detail, and
 * nook-detail routes (per design decision "One feature module, one
 * nested NavHost inside the Kornerz tab"). Tab-level back-stack
 * rule lives here so the bottom bar visibility rule is satisfied
 * without custom logic.
 */
internal fun androidx.navigation.NavGraphBuilder.kornerzTabGraph(
    onNavEvent: (KornerzNavEvent) -> Unit = {},
    snackbarHostState: SnackbarHostState,
    onNookDetail: (String) -> Unit = {},
    pendingKornerId: String? = null,
    onPendingConsumed: () -> Unit = {},
) {
    composable(ShellTab.Kornerz.route) {
        val nested = rememberNavController()
        val scope = rememberCoroutineScope()
        LaunchedEffect(pendingKornerId) {
            val id = pendingKornerId
            if (id != null) {
                nested.navigate(KornerzRoute.Detail.build(id))
                onPendingConsumed()
            }
        }
        NavHost(
            navController = nested,
            startDestination = KornerzRoute.List.route,
        ) {
            composable(KornerzRoute.List.route) {
                KornerzScreen(
                    modifier = Modifier,
                    onNavEvent = onNavEvent,
                )
            }
            composable(
                route = KornerzRoute.Detail.PATTERN,
                arguments = listOf(
                    navArgument(KornerDetailViewModel.ARG_KORNER_ID) { type = NavType.StringType },
                ),
            ) {
                KornerDetailScreen(
                    modifier = Modifier,
                    onNavEvent = { event ->
                        when (event) {
                            is KornerDetailNavEvent.NavigateToNookDetail -> {
                                onNookDetail(event.nookId)
                            }
                            is KornerDetailNavEvent.NavigateToMemberProfile -> {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Member profile coming in Phase 6")
                                }
                            }
                        }
                    },
                )
            }
            composable(
                route = NooksRoute.Detail.PATTERN,
                arguments = listOf(
                    navArgument(NookDetailViewModel.ARG_NOOK_ID) { type = NavType.StringType },
                ),
            ) {
                NookDetailScreen(
                    modifier = Modifier,
                    onNavEvent = { event ->
                        when (event) {
                            is NookDetailNavEvent.NavigateToKornerDetail -> {
                                nested.navigate(KornerzRoute.Detail.build(event.kornerId))
                            }
                        }
                    },
                    onBack = { nested.popBackStack() },
                )
            }
        }
    }
}

/**
 * Nested graph for the Nooks tab — hosts the cross-Korner nooks
 * feed list and the per-tab nook detail destination. Back-press
 * inside any nested destination pops within the Nooks tab; the
 * bottom bar stays pinned. The `videocall/{sessionId}` route is
 * registered on the root shell graph (see [AppShell]) so it can be
 * reached cross-tab.
 */
internal fun androidx.navigation.NavGraphBuilder.nooksTabGraph(
    onProfileTap: () -> Unit = {},
    onNookDetail: (String) -> Unit = {},
    onKornerDetail: (String) -> Unit = {},
    onJoinVideoCall: (String) -> Unit = {},
) {
    composable(ShellTab.Nooks.route) {
        val nested = rememberNavController()
        NavHost(
            navController = nested,
            startDestination = NooksRoute.List.route,
        ) {
            composable(NooksRoute.List.route) {
                NooksScreen(
                    modifier = Modifier,
                    onNavEvent = { event ->
                        when (event) {
                            NooksNavEvent.NavigateToProfile -> onProfileTap()
                            is NooksNavEvent.NavigateToNookDetail -> {
                                onNookDetail(event.nookId)
                            }
                            is NooksNavEvent.NavigateToKornerDetail -> {
                                onKornerDetail(event.kornerId)
                            }
                            is NooksNavEvent.JoinVideoCall -> {
                                // Bubble to the shell so the call can
                                // be routed through the root
                                // NavController — the videocall
                                // route is registered on the shell
                                // graph (AppShell.kt) so it can be
                                // reached from any tab, and so the
                                // shell can hide the bottom bar for
                                // the duration of the call.
                                onJoinVideoCall(event.sessionId)
                            }
                        }
                    },
                )
            }
            composable(
                route = NooksRoute.Detail.PATTERN,
                arguments = listOf(
                    navArgument(NookDetailViewModel.ARG_NOOK_ID) { type = NavType.StringType },
                ),
            ) {
                NookDetailScreen(
                    modifier = Modifier,
                    onBack = { nested.popBackStack() },
                )
            }
        }
    }
}

internal fun androidx.navigation.NavGraphBuilder.loopsTabGraph(
    onProfileTap: () -> Unit = {},
    onLoopDetail: (String) -> Unit = {},
    pendingLoopId: String? = null,
    onPendingConsumed: () -> Unit = {},
) {
    composable(ShellTab.Loops.route) {
        val nested = rememberNavController()
        LaunchedEffect(pendingLoopId) {
            val id = pendingLoopId
            if (id != null) {
                nested.navigate(LoopsRoute.Detail.build(id))
                onPendingConsumed()
            }
        }
        NavHost(
            navController = nested,
            startDestination = LoopsRoute.List.route,
        ) {
            composable(LoopsRoute.List.route) {
                LoopsScreen(
                    modifier = Modifier,
                    onNavEvent = { event ->
                        when (event) {
                            LoopsNavEvent.NavigateToProfile -> onProfileTap()
                            is LoopsNavEvent.NavigateToLoopDetail -> onLoopDetail(event.loopId)
                        }
                    },
                )
            }
            composable(
                route = LoopsRoute.Detail.PATTERN,
                arguments = loopsDetailNavArguments,
            ) {
                LoopDetailScreen(
                    modifier = Modifier,
                    onNavEvent = { event ->
                        when (event) {
                            is LoopDetailNavEvent.OpenCreateComment -> {
                                nested.navigate(LoopsRoute.Comment.build(event.loopId))
                            }
                            is LoopDetailNavEvent.OpenReply -> {
                                nested.navigate(LoopsRoute.Reply.build(event.loopId, event.commentId))
                            }
                            is LoopDetailNavEvent.NavigateToKornerDetail -> { /* P3 korner deep link */ }
                        }
                    },
                )
            }
            dialog(
                route = LoopsRoute.Comment.PATTERN,
                arguments = loopsCommentNavArguments,
            ) {
                CreateCommentScreen(modifier = Modifier)
            }
            composable(
                route = LoopsRoute.Reply.PATTERN,
                arguments = loopsReplyNavArguments,
            ) {
                ReplyScreen(modifier = Modifier)
            }
        }
    }
}

internal fun androidx.navigation.NavGraphBuilder.kloudersTabGraph(onLogout: () -> Unit) =
    tabComposableWithLogout(ShellTab.Klouders, onLogout)

/**
 * P6 (task 9.1) — Profile is **pushed** onto the root NavHost (not
 * a tab), mirroring iOS `UserProfileVC.hidesBottomBarWhenPushed =
 * true`. The bottom bar is hidden for every `profile/.*` route via
 * the regex in `AppShell`'s `Scaffold.bottomBar`. The root
 * `profile/view` composable hosts a nested NavHost that owns the
 * edit-basic-info, change-basic-info (dialog), change-success, and
 * privacy-policy destinations.
 */
internal fun androidx.navigation.NavGraphBuilder.profileGraph(
    onOpenNotifications: () -> Unit,
    onOpenMyCommunities: () -> Unit = {},
    onOpenLogout: () -> Unit = {},
    onOpenLoopComment: (String) -> Unit = {},
    onOpenForumComment: (String) -> Unit = {},
    onOpenDeeplink: (String) -> Unit = {},
    onShowAlert: (String) -> Unit = {},
    onShowError: (String) -> Unit = {},
) {
    composable(ProfileRoute.View.route) {
        val nested = rememberNavController()
        androidx.navigation.compose.NavHost(
            navController = nested,
            startDestination = ProfileRoute.View.route,
        ) {
            composable(ProfileRoute.View.route) {
                ProfileScreen(
                    onNavigateToNotifications = onOpenNotifications,
                    onNavigateToEditBasicInfo = { nested.navigate(ProfileRoute.EditBasicInfo.route) },
                    onNavigateToPrivacyPolicy = { nested.navigate(ProfileRoute.PrivacyPolicy.route) },
                    onNavigateToMyCommunities = onOpenMyCommunities,
                    onNavigateToLogout = onOpenLogout,
                    onNavigateToDeleteAccount = { /* dialog is owned by the ProfileScreen itself */ },
                )
            }
            composable(ProfileRoute.EditBasicInfo.route) {
                EditBasicInfoScreen(
                    onBack = { nested.popBackStack() },
                    onNavigateToChange = { field -> nested.navigate(ProfileRoute.ChangeBasicInfo.build(field.key)) },
                )
            }
            dialog(
                route = ProfileRoute.ChangeBasicInfo.PATTERN,
                arguments = listOf(
                    androidx.navigation.navArgument(ProfileRoute.ChangeBasicInfo.ARG_FIELD) { type = NavType.StringType },
                ),
            ) {
                ChangeBasicInfoScreen(
                    onNavigateToSuccess = { msg -> nested.navigate(ProfileRoute.ChangeBasicInfoSuccess.build(msg)) },
                    onShowError = onShowError,
                    onBack = { nested.popBackStack() },
                )
            }
            composable(ProfileRoute.ChangeBasicInfoSuccess.PATTERN) {
                ChangeBasicInfoSuccessScreen(onClose = { nested.popBackStack(ProfileRoute.EditBasicInfo.route, inclusive = false) })
            }
            composable(ProfileRoute.PrivacyPolicy.route) {
                PrivacyPolicyScreen(onBack = { nested.popBackStack() })
            }
        }
    }
}
