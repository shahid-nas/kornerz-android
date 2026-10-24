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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kornerz.core.model.DeepLink
import com.kornerz.feature.home.HomeRoute
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
import com.kornerz.feature.loops.reply.ReplyNavEvent
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
import com.kornerz.feature.ranking.list.RankingScreen
import com.kornerz.feature.community.CommunityRoute
import com.kornerz.feature.shell.deeplink.DeepLinkResolution
import com.kornerz.feature.shell.deeplink.DeepLinkResolvingScreen
import com.kornerz.feature.shell.deeplink.DeepLinkTarget
import com.kornerz.feature.community.admin.AdminCommunitiesListScreen
import com.kornerz.feature.community.admin.detail.CommunityDetailScreen
import com.kornerz.feature.community.admin.detail.CommunityDetailViewModel
import com.kornerz.feature.videocall.VideoCallNavEvent
import com.kornerz.feature.videocall.VideocallRoute
import com.kornerz.feature.videocall.videocallGraph
import kotlinx.coroutines.launch

/**
 * A top-level, full-screen destination contributed to the shell's
 * `NavHost` by the composition root.
 *
 * P8 introduces this seam because `feature/shell` may not depend on
 * `feature/subscription` (`docs/architecture.md` §2.5 — only `app` imports
 * the feature modules), yet the subscription routes must be registered on
 * the shell's **root** `NavHost`, outside every tab's nested graph, so
 * neither activates a bottom tab. `app` passes the concrete
 * `SubscriptionRoute.Plans` / `Success` entries here at the composition
 * root, which keeps the dependency direction correct instead of
 * reversing it.
 */
data class ShellExtraRoute(
    val route: String,
    val content: @Composable (ShellExtraRouteScope) -> Unit,
)

/**
 * Everything a contributed top-level route needs from the shell: its
 * controller, the Profile tab's nested controller (so the paywall can
 * reach the existing `profile/privacy-policy` destination instead of
 * shipping a second copy of the PDF viewer), and the paywall-gate hooks.
 */
class ShellExtraRouteScope internal constructor(
    val navController: NavHostController,
    val openPrivacyPolicy: () -> Unit,
    val onPremiumGateRequests: StateFlow<Int>,
    val onOpenTermsOfUse: (String) -> Unit,
    val onPremiumRouteOpened: () -> Unit,
    val onPremiumRouteClosed: () -> Unit,
)

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
    extraRoutes: List<ShellExtraRoute> = emptyList(),
    onNavigateToPremium: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val applicationContext = LocalContext.current.applicationContext

    // P10 — the shell owns the deep-link lifecycle. The manager is a
    // process-scoped singleton (it has to be: a cold-start deep link
    // arrives before the shell exists), so it is reached through the
    // shell's ViewModel rather than constructed here.
    val deepLinkManager = viewModel.deepLinkManager
    val deepLinkResolver = viewModel.deepLinkResolver

    // P10 (task 8.7) — release any deep link that arrived before the
    // graph could route it. Declared **before** the premium-gate effect
    // so the deep-link destination is on the stack first and the paywall
    // is pushed on top of it — iOS parity: `AppTabController.viewDidLoad`
    // calls `DeepLinksManager.shared.notifyAppLaunch()` (`:24`) after
    // registering the entitlement sink.
    LaunchedEffect(deepLinkManager) {
        deepLinkManager.notifyAppLaunch()
    }

    // P10 (task 8.8) — one `deeplink/resolving` push per released link.
    LaunchedEffect(deepLinkManager) {
        deepLinkManager.releasedLinks.collect {
            navController.navigate(DeepLinkRoutePatterns.RESOLVING) {
                launchSingleTop = true
            }
        }
    }

    // The paywall gate fires from exactly two places, both owned by
    // `ShellViewModel`. The shell's only job is to act on a rising edge of
    // the request counter: no destination-change or tab-switch evaluation
    // exists here.
    val premiumGateRequests by viewModel.premiumGateRequests.collectAsState()
    LaunchedEffect(premiumGateRequests) {
        if (premiumGateRequests > 0 && currentRoute != SubscriptionRoutePatterns.PLANS) {
            viewModel.onPremiumRouteOpened()
            navController.navigate(SubscriptionRoutePatterns.PLANS) {
                launchSingleTop = true
            }
        }
    }

    val extraRouteScope = remember(navController, viewModel) {
        ShellExtraRouteScope(
            navController = navController,
            openPrivacyPolicy = {
                // The privacy-policy viewer is registered on the shell's
                // root graph (see `profileGraph`), so the root
                // controller can push it directly — no second copy of
                // the PDF viewer and no nested controller to keep alive.
                navController.navigate(ProfileRoute.PrivacyPolicy.route)
            },
            onPremiumGateRequests = viewModel.premiumGateRequests,
            onOpenTermsOfUse = { url -> applicationContext.openExternally(url) },
            onPremiumRouteOpened = viewModel::onPremiumRouteOpened,
            onPremiumRouteClosed = viewModel::onPremiumRouteClosed,
        )
    }
    val comingSoonSnackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Cross-tab navigation: the Kornerz tab hosts the
    // `kornerz/{kornerId}` destination inside its own nested
    // `NavController`, so the shell's root controller can't push to
    // it directly. Instead, the shell writes the requested id here
    // and the Kornerz tab consumes it from its own nested controller.
    //
    // The holders are **live** (`StateFlow`), not snapshots: the
    // `NavHost` builder is `remember`ed by Navigation-Compose ("the
    // contents of the builder cannot be changed"), so the graph — and
    // every value it captures — is frozen at the shell's first
    // composition. A plain `mutableStateOf` read inside the graph
    // builder would keep returning its initial value (`null`) forever,
    // so the destination would never be pushed. Passing the flow
    // **by reference** keeps the tab's view of the request live: the
    // id written before the tab switch is replayed to the collector
    // when it starts, and every id written while the tab is already
    // open is delivered as it happens.
    val pendingKornerId = remember { MutableStateFlow<String?>(null) }
    val pendingLoopTarget = remember { MutableStateFlow<LoopDetailTarget?>(null) }

    /**
     * Whether the loop detail currently on screen hides the bottom
     * bar. The detail lives inside a tab's **nested** `NavHost` — the
     * Loops tab's own feed and the Home tab's loop-of-the-day card
     * both push it — so the root route cannot express the decision.
     * Each tab lifts its nested entry's `hideBottomBar` nav argument
     * (and its comment/reply destinations) up here, and the root
     * `Scaffold` reads the flag of the **active** tab only — a flag
     * left behind by a tab the user has left cannot hide the bar on
     * another tab.
     */
    var homeLoopDetailHidesBottomBar by remember { mutableStateOf(false) }
    var loopsLoopDetailHidesBottomBar by remember { mutableStateOf(false) }

    /**
     * Executes a resolved deep-link target.
     *
     * Both destinations live inside a tab's **nested** `NavHost` — the
     * loop detail in the Loops tab, the Korner detail in the Kornerz
     * tab — so they cannot be pushed on the root controller. The shell
     * reuses its existing cross-tab mechanism instead: write the id to
     * the pending holder, switch the tab, and let the tab's nested
     * controller consume it. The tab switch's `popUpTo` also removes the
     * interstitial from the back stack, which is what iOS's
     * `removeSelfFromNavigation()` (`DeeplinkViewController.swift:41-49`)
     * does.
     */
    val executeDeepLinkTarget: (DeepLinkTarget) -> Unit = { target ->
        when (target) {
            is DeepLinkTarget.LoopDetail -> navigateToLoopDetail(
                navController = navController,
                loopId = target.loopId,
                hideBottomBar = true,
                onPending = { pendingLoopTarget.value = it },
            )

            is DeepLinkTarget.KornerDetail -> navigateToKornerDetail(
                navController = navController,
                kornerId = target.kornerId,
                onPending = { pendingKornerId.value = it },
            )
        }
    }

    /**
     * Routes a deep link through the one resolver. A link that cannot be
     * routed surfaces the shared failure alert and returns the user to
     * the Home tab — the iOS `DeeplinkViewController.showAlert` path
     * (`:51-56`).
     */
    val routeDeepLink: (DeepLink) -> Unit = { deeplink ->
        scope.launch {
            when (val resolution = deepLinkResolver.resolve(deeplink)) {
                is DeepLinkResolution.Routable -> executeDeepLinkTarget(resolution.target)

                DeepLinkResolution.NotRoutable -> {
                    comingSoonSnackbar.showSnackbar(DEEPLINK_FAILURE_MESSAGE)
                    navigateToTab(navController, ShellTab.Home)
                }
            }
        }
    }

    /**
     * Pushes the interstitial again when another link is still queued.
     *
     * The shell pushes `deeplink/resolving` once per released link, but
     * the interstitial takes **one** link at a time and removes itself
     * when it has an answer — so the second link of a burst is picked up
     * here, after the first one has been routed.
     */
    val routeNextPendingDeepLink: () -> Unit = {
        if (deepLinkManager.hasPending()) {
            navController.navigate(DeepLinkRoutePatterns.RESOLVING) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                ShellNavEvent.Logout -> onLogout()
            }
        }
    }

    Scaffold(
        bottomBar = {
            // `subscription/.*` added in P8: both subscription routes are
            // full-screen and hide the bar, mirroring iOS presenting them
            // modally with `.fullScreen`
            // (`AppTabController.swift:217-220`).
            // `deeplink/.*` added in P10: the deep-link interstitial is
            // full-screen too (inventory 13.1).
            val showBottomBar = shouldShowBottomBar(
                currentRoute = currentRoute,
                // Only the active tab's flag counts — a flag left behind
                // by a tab the user has left must not hide the bar on
                // the tab they are on now.
                hidesBottomBarForLoopDetail = hidesBottomBarForLoopDetail(
                    currentRoute = currentRoute,
                    homeHides = homeLoopDetailHidesBottomBar,
                    loopsHides = loopsLoopDetailHidesBottomBar,
                ),
            )
            if (showBottomBar) {
                KornerzBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { tab ->
                        navigateToTab(navController, tab)
                    },
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
                    navigateToKornerDetail(navController, id, onPending = { pendingKornerId.value = id })
                },
                onNavigateToProfile = {
                    navController.navigate(ProfileRoute.View.route)
                },
                onHideBottomBarChanged = { hides -> homeLoopDetailHidesBottomBar = hides },
            )
            kornerzTabGraph(
                onNavEvent = { event ->
                    when (event) {
                        KornerzNavEvent.NavigateToProfile ->
                            navController.navigate(ProfileRoute.View.route)
                        is KornerzNavEvent.NavigateToKornerDetail -> {
                            pendingKornerId.value = event.kornerId
                        }
                    }
                },
                // A loop detail opened from the Korner detail is a
                // full-screen destination — the tab bar is hidden.
                onLoopDetail = { id ->
                    navigateToLoopDetail(
                        navController = navController,
                        loopId = id,
                        hideBottomBar = true,
                        onPending = { pendingLoopTarget.value = it },
                    )
                },
                pendingKornerId = pendingKornerId,
                onPendingConsumed = { pendingKornerId.value = null },
                snackbarHostState = comingSoonSnackbar,
            )
            nooksTabGraph(
                onProfileTap = {
                    navController.navigate(ProfileRoute.View.route)
                },
                onKornerDetail = { kornerId ->
                    pendingKornerId.value = kornerId
                    navController.navigate(ShellTab.Kornerz.route) {
                        popUpTo(navController.graph.id) { saveState = true }
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
                onNavigateToKornerDetail = { kornerId ->
                    navigateToKornerDetail(
                        navController = navController,
                        kornerId = kornerId,
                        onPending = { pendingKornerId.value = it },
                    )
                },
                pendingLoopTarget = pendingLoopTarget,
                onPendingConsumed = { pendingLoopTarget.value = null },
                onHideBottomBarChanged = { loopsLoopDetailHidesBottomBar = it },
            )
            // P6 (task 9.1) — Profile is pushed onto the root NavHost
            // (mirrors iOS `UserProfileVC.hidesBottomBarWhenPushed`).
            // The bottom-bar visibility regex above matches
            // `profile/.*` so the bar is hidden across every nested
            // Profile destination.
            profileGraph(
                navController = navController,
                onBack = { navController.popBackStack() },
                onOpenPremium = onNavigateToPremium,
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
                        // The loop detail is a destination of the Loops
                        // tab's **nested** `NavHost`, so it cannot be
                        // pushed on the root controller — the shell's
                        // cross-tab mechanism (pending id + tab switch)
                        // is the only way in. The notification opens the
                        // detail in a full-screen context, so the tab
                        // bar is hidden.
                        navigateToLoopDetail(
                            navController = navController,
                            loopId = loopId,
                            hideBottomBar = true,
                            onPending = { pendingLoopTarget.value = it },
                        )
                    },
                    onOpenForumComment = { forumId ->
                        // Forum Detail is excluded from the Android
                        // port, so this link is not routable — it goes
                        // through the one resolver, which reports it as
                        // not routable and surfaces the shared failure
                        // alert.
                        routeDeepLink(DeepLink.NewComment(forumId = forumId))
                    },
                    onOpenDeeplink = { deeplink -> routeDeepLink(deeplink) },
                    onShowAlert = { msg -> scope.launch { comingSoonSnackbar.showSnackbar(msg) } },
                )
            }
            // P10 (task 8.5) — the deep-link interstitial is a
            // **top-level** route on the shell's root `NavHost`, outside
            // every tab's nested graph, so no bottom tab becomes active
            // and the bottom-bar-hidden regex above applies to it. It
            // carries no navigation argument: the link reaches it
            // through the `DeepLinkManager`.
            composable(DeepLinkRoutePatterns.RESOLVING) {
                DeepLinkResolvingScreen(
                    onResolved = { target ->
                        executeDeepLinkTarget(target)
                        routeNextPendingDeepLink()
                    },
                    onFailed = {
                        scope.launch {
                            comingSoonSnackbar.showSnackbar(DEEPLINK_FAILURE_MESSAGE)
                        }
                        navigateToTab(navController, ShellTab.Home)
                        routeNextPendingDeepLink()
                    },
                )
            }
            kloudersTabGraph()
            videocallGraph(
                onNavEvent = { event ->
                    when (event) {
                        VideoCallNavEvent.NavigateBack ->
                            navController.popBackStack()
                    }
                },
            )
            // P8 — the subscription routes are registered on the root
            // NavHost, outside every tab's nested NavHost, so no bottom
            // tab becomes active and the bottom-bar-hidden regex above
            // applies to them like any other full-screen route.
            extraRoutes.forEach { extra ->
                composable(extra.route) { extra.content(extraRouteScope) }
            }
        }
    }
}

/**
 * The P8 subscription route strings, duplicated here as literals because
 * `feature/shell` may not import `feature/subscription`
 * (`docs/architecture.md` §2.5). `app` registers the very same strings via
 * `SubscriptionRoute`, so the two must stay in step — the parity is
 * asserted by the `subscription-plans` navigation tests.
 */
internal object SubscriptionRoutePatterns {
    const val PLANS = "subscription/plans"
    const val SUCCESS = "subscription/success"
}


/**
 * Whether the loop detail on screen hides the bottom bar.
 *
 * The detail lives inside a tab's **nested** `NavHost` — the Loops
 * tab's own feed and the Home tab's loop-of-the-day card both push it
 * — so the root route cannot express the per-entry-point decision.
 * Each tab lifts its own flag up to the shell, and only the flag of
 * the **active** tab counts: a flag left behind by a tab the user has
 * left must not hide the bar on the tab they are on now.
 *
 * Extracted as a pure function so the rule is unit-testable without an
 * emulator.
 */
internal fun hidesBottomBarForLoopDetail(
    currentRoute: String?,
    homeHides: Boolean,
    loopsHides: Boolean,
): Boolean = when (currentRoute) {
    ShellTab.Home.route -> homeHides
    ShellTab.Loops.route -> loopsHides
    else -> false
}

/**
 * Whether the shell's bottom bar is visible.
 *
 * Two rules decide it:
 *  1. the **route** — the full-screen routes (`videocall/…`,
 *     `notifications/list`, `profile/…`, `community/admin/…`,
 *     `subscription/…`, `deeplink/…`) hide it;
 *  2. the **loop detail's `hideBottomBar` argument** — the loop detail
 *     is a full-screen destination, so the bar is hidden across it
 *     whichever entry point opened it (the Home carousel, the Loops
 *     feed, the Korner detail, a deep link, a notification).
 *
 * Extracted as a pure function so the rule is unit-testable without an
 * emulator.
 */
internal fun shouldShowBottomBar(
    currentRoute: String?,
    hidesBottomBarForLoopDetail: Boolean,
): Boolean {
    if (hidesBottomBarForLoopDetail) return false
    return currentRoute?.matches(
        Regex(
            "videocall/.*|notifications/list|profile/.*|community/admin/.*" +
                "|subscription/.*|deeplink/.*|loops/.*/comment",
        ),
    ) == false
}

/**
 * The P10 deep-link route strings. The interstitial is registered on the
 * shell's root `NavHost`, outside every tab's nested graph, so no bottom
 * tab becomes active and the bottom bar is hidden for it.
 */
internal object DeepLinkRoutePatterns {
    const val RESOLVING = "deeplink/resolving"
}

/**
 * The alert iOS shows when a deep link cannot be routed —
 * `DeeplinkViewController.showAlert` (`:51-56`). The user is returned
 * to the Home tab.
 */
internal const val DEEPLINK_FAILURE_MESSAGE =
    "Failed to load deeplink. Will navigate back to home screen"

/** iOS `SubscriptionViewModel.swift:152-157` opens the EULA externally. */
private fun android.content.Context.openExternally(url: String) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(url),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

private fun navigateToTab(navController: NavHostController, tab: ShellTab) {
    navController.navigate(tab.route) {
        popUpTo(navController.graph.id) {
            saveState = true
        }
        launchSingleTop = true
        // The Loops tab's **root** is the feed. Restoring the tab's saved
        // state would bring back the loop detail that was open earlier — a
        // deep link, a notification, a row tap — so the tab is re-created
        // from scratch whenever the user selects it. The other tabs keep
        // their state, so their nested destinations survive a tab switch.
        restoreState = tab != ShellTab.Loops
    }
}

private fun navigateToKornerDetail(
    navController: NavHostController,
    kornerId: String,
    onPending: (String) -> Unit = {},
) {
    onPending(kornerId)
    navController.navigate(ShellTab.Kornerz.route) {
        popUpTo(navController.graph.id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun navigateToLoopDetail(
    navController: NavHostController,
    loopId: String,
    hideBottomBar: Boolean = true,
    accentColorHex: String = "",
    onPending: (LoopDetailTarget) -> Unit = {},
) {
    onPending(
        LoopDetailTarget(
            loopId = loopId,
            hideBottomBar = hideBottomBar,
            accentColorHex = accentColorHex,
        ),
    )
    navController.navigate(ShellTab.Loops.route) {
        popUpTo(navController.graph.id) { saveState = true }
        launchSingleTop = true
        // The tab is entered **fresh**: the loop the caller asked for is
        // the only detail on the tab's nested stack. Restoring the saved
        // state would push the requested detail on top of a detail that
        // was open earlier, so the back button would reveal that stale
        // screen instead of the feed.
        restoreState = false
    }
}

/**
 * The Home tab's nested `NavHost`.
 *
 * iOS pushes the loop detail onto the **Home tab's own** navigation
 * stack — `HomeTabCoordinator.navigateToLoopDetail` (`:67-73`) pushes
 * the detail with `hidesBottomBarWhenPushed = true`, so the detail
 * covers the Home feed and the tab bar is hidden. Android mirrors that
 * by giving the Home tab a nested `NavHost` that hosts the Home feed
 * and the very same loop-detail destinations the Loops tab hosts
 * (see [loopDetailGraph]).
 *
 * Tapping the loop-of-the-day card (or a recent-loops tile) therefore
 * pushes `loops/{loopId}` **inside the Home tab** — the user stays on
 * Home, the tab bar is hidden, and system back returns to the feed.
 */
internal fun androidx.navigation.NavGraphBuilder.homeTabGraph(
    onNavigateToKornerDetail: (String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onHideBottomBarChanged: (Boolean) -> Unit = {},
) {
    composable(ShellTab.Home.route) {
        val nested = rememberNavController()
        // The nested entry's `hideBottomBar` argument and the
        // comment/reply destinations decide the tab bar's visibility for
        // the Home tab — the root route (`tab/home`) cannot express
        // either, so the flag is lifted to the shell.
        val nestedEntry by nested.currentBackStackEntryAsState()
        val nestedRoute = nestedEntry?.destination?.route
        val hidesBottomBar =
            nestedEntry?.arguments?.getBoolean(LoopsRoute.Detail.ARG_HIDE_BOTTOM_BAR) == true ||
                nestedRoute?.contains("/comment") == true ||
                nestedRoute?.contains("/reply") == true
        LaunchedEffect(hidesBottomBar) { onHideBottomBarChanged(hidesBottomBar) }
        NavHost(
            navController = nested,
            startDestination = HomeRoute.Route,
        ) {
            composable(HomeRoute.Route) {
                HomeScreen(
                    modifier = Modifier,
                    onNavigateToKornerDetail = onNavigateToKornerDetail,
                    onNavigateToLoopDetail = { loopId, accentColorHex ->
                        // iOS parity: the detail is pushed on the Home
                        // tab's own stack, with the tab bar hidden —
                        // and in the very colour the tapped tile was
                        // painted with.
                        nested.navigate(
                            LoopsRoute.Detail.build(
                                loopId,
                                hideBottomBar = true,
                                accentColorHex = accentColorHex,
                            ),
                        )
                    },
                    onNavigateToProfile = onNavigateToProfile,
                )
            }
            loopDetailGraph(
                navController = nested,
                onNavigateToKornerDetail = onNavigateToKornerDetail,
            )
        }
    }
}

/**
 * The loop-detail destinations — `loops/{loopId}`, the create-comment
 * sheet and the reply thread — hosted by a tab's nested `NavHost`.
 *
 * Both the Loops tab and the Home tab host them, because iOS pushes
 * the loop detail onto the **originating tab's** navigation stack
 * (`LoopsTabCoordinator` for the feed, `HomeTabCoordinator` for the
 * Home carousel): the detail covers the tab it was opened from and the
 * system back button returns to it.
 */
internal fun androidx.navigation.NavGraphBuilder.loopDetailGraph(
    navController: NavHostController,
    onNavigateToKornerDetail: (String) -> Unit = {},
) {
    composable(
        route = LoopsRoute.Detail.PATTERN,
        arguments = loopsDetailNavArguments,
    ) {
        LoopDetailScreen(
            modifier = Modifier,
            onNavEvent = { event ->
                when (event) {
                    LoopDetailNavEvent.Pop -> {
                        // The in-app back button — same target as the
                        // system back gesture: the detail is popped off
                        // the tab's own stack, so the user returns to
                        // the list it was opened from.
                        navController.popBackStack()
                    }
                    is LoopDetailNavEvent.OpenCreateComment -> {
                        navController.navigate(
                            LoopsRoute.Comment.build(
                                loopId = event.loopId,
                                accentColorHex = event.accentColorHex,
                            ),
                        )
                    }
                    is LoopDetailNavEvent.OpenReply -> {
                        navController.navigate(
                            LoopsRoute.Reply.build(
                                loopId = event.loopId,
                                commentId = event.commentId,
                                accentColorHex = event.accentColorHex,
                            ),
                        )
                    }
                    is LoopDetailNavEvent.NavigateToKornerDetail -> {
                        // The Korner detail lives inside the Kornerz
                        // tab's nested `NavHost`, so the shell's
                        // pending-Korner seam is the only way in.
                        onNavigateToKornerDetail(event.communityId)
                    }
                }
            },
        )
    }
    composable(
        route = LoopsRoute.Comment.PATTERN,
        arguments = loopsCommentNavArguments,
    ) {
        CreateCommentScreen(
            modifier = Modifier,
            onDismiss = { navController.popBackStack() },
        )
    }
    composable(
        route = LoopsRoute.Reply.PATTERN,
        arguments = loopsReplyNavArguments,
    ) {
        ReplyScreen(
            modifier = Modifier,
            onNavEvent = { event ->
                when (event) {
                    ReplyNavEvent.Pop -> navController.popBackStack()
                    is ReplyNavEvent.OpenCreateComment ->
                        // The thread's add-comment bar opens the
                        // create-comment sheet in reply mode —
                        // iOS `showAddCommentSheet(type:
                        // .replyToComment(loopId, commentInfo))`.
                        navController.navigate(
                            LoopsRoute.Comment.build(
                                loopId = event.loopId,
                                parentCommentId = event.parentCommentId,
                                accentColorHex = event.accentColorHex,
                            ),
                        )
                    is ReplyNavEvent.ShowError -> { /* surface via snackbar in shell */ }
                }
            },
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
    onLoopDetail: (String) -> Unit = {},
    pendingKornerId: MutableStateFlow<String?>,
    onPendingConsumed: () -> Unit = {},
) {
    composable(ShellTab.Kornerz.route) {
        val nested = rememberNavController()
        val scope = rememberCoroutineScope()
        // The pending id is consumed as a **flow**, not as a captured
        // value: the `NavHost` builder is remembered, so the parameter
        // would otherwise stay frozen at the graph's creation value and
        // the `kornerz/{kornerId}` destination would never be pushed.
        // The collector receives the id written before the tab switch
        // (a `StateFlow` replays its current value to a new
        // subscriber) and every id written while the tab is open.
        LaunchedEffect(nested) {
            pendingKornerId.collect { id ->
                if (id != null) {
                    // Cleared before the push so a cancelled tab switch
                    // cannot replay the same id on the next visit.
                    onPendingConsumed()
                    nested.navigate(KornerzRoute.Detail.build(id))
                }
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
                                // The nook detail is a destination of
                                // this tab's **nested** `NavHost` — the
                                // shell's root controller cannot resolve
                                // `nooks/{nookId}`, so the push has to
                                // happen on the tab's own controller.
                                nested.navigate(NooksRoute.Detail.build(event.nookId))
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
                                // The nook detail is a destination of
                                // the Nooks tab's **nested** `NavHost` —
                                // the shell's root controller cannot
                                // resolve `nooks/{nookId}`, so the push
                                // has to happen on the tab's own
                                // controller. `popBackStack` then
                                // returns to the Nooks tab list.
                                nested.navigate(NooksRoute.Detail.build(event.nookId))
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

/**
 * Nested graph for the Loops tab — hosts the feed (`loops/list`) and
 * the loop-detail destinations (`loops/{loopId}`, the comment sheet and
 * the reply thread).
 *
 * The tab's **root** is the feed. The nested back stack is saved and
 * restored together with the tab (the shell switches tabs with
 * `saveState` / `restoreState`), so a detail that was open earlier — a
 * deep link, a notification, a row tap — would still be on top when the
 * user came back to the tab. The shell therefore asks the tab to return
 * to its root ([rootRequested]) whenever the user selects the Loops tab
 * from the bottom bar.
 */
internal fun androidx.navigation.NavGraphBuilder.loopsTabGraph(
    onProfileTap: () -> Unit = {},
    onNavigateToKornerDetail: (String) -> Unit = {},
    pendingLoopTarget: MutableStateFlow<LoopDetailTarget?>,
    onPendingConsumed: () -> Unit = {},
    onHideBottomBarChanged: (Boolean) -> Unit = {},
) {
    composable(ShellTab.Loops.route) {
        val nested = rememberNavController()
        // The nested controller's current entry carries the loop
        // detail's `hideBottomBar` nav argument; it is lifted to the
        // shell so the root `Scaffold` can fold it into the bottom-bar
        // visibility predicate. The comment and reply destinations hide
        // the bar as well.
        val nestedEntry by nested.currentBackStackEntryAsState()
        val nestedRoute = nestedEntry?.destination?.route
        val hidesBottomBar =
            nestedEntry?.arguments?.getBoolean(LoopsRoute.Detail.ARG_HIDE_BOTTOM_BAR) == true ||
                nestedRoute?.contains("/comment") == true ||
                nestedRoute?.contains("/reply") == true
        LaunchedEffect(hidesBottomBar) { onHideBottomBarChanged(hidesBottomBar) }
        LaunchedEffect(nested) {
            pendingLoopTarget.collect { target ->
                if (target != null) {
                    // Cleared before the push so a cancelled tab switch
                    // cannot replay the same target on the next visit.
                    onPendingConsumed()
                    nested.navigate(
                        LoopsRoute.Detail.build(
                            target.loopId,
                            target.hideBottomBar,
                            target.accentColorHex,
                        ),
                    )
                }
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
                            is LoopsNavEvent.NavigateToLoopDetail ->
                                // The loop detail is a full-screen
                                // destination: the tab bar is hidden
                                // across it, whatever opened it — the
                                // Loops feed included.
                                nested.navigate(
                                    LoopsRoute.Detail.build(
                                        event.loopId,
                                        hideBottomBar = true,
                                        accentColorHex = event.accentColorHex,
                                    ),
                                )
                        }
                    },
                )
            }
            loopDetailGraph(
                navController = nested,
                onNavigateToKornerDetail = onNavigateToKornerDetail,
            )
        }
    }
}

/**
 * The Klouders tab — the P10 Klouders leaderboard (inventory 2.6 +
 * 2.7), hosted by `feature/ranking`.
 *
 * Before P10 this was the P0 `EmptyTabScreen` placeholder with a
 * "Log out" affordance, which existed only so the P1 logout path was
 * testable. P1 shipped a real logout in P6, so the affordance is gone
 * and the tab renders the live leaderboard.
 *
 * The tab has no nested `NavHost` — the leaderboard is a single screen
 * with no detail destination — so the destination is registered
 * directly on the shell's root `NavHost`.
 */
internal fun androidx.navigation.NavGraphBuilder.kloudersTabGraph() {
    composable(ShellTab.Klouders.route) {
        RankingScreen()
    }
}

/**
 * P6 (task 9.1) — Profile is **pushed** onto the root NavHost (not
 * a tab), mirroring iOS `UserProfileVC.hidesBottomBarWhenPushed =
 * true`. The bottom bar is hidden for every `profile/.*` route via
 * the regex in `AppShell`'s `Scaffold.bottomBar`.
 *
 * Every Profile destination is registered **directly on the shell's
 * root `NavHost`**. An earlier revision hosted them in a *nested*
 * `NavHost` inside the `profile/view` destination, driven by a
 * `NavHostController` hoisted to `AppShell` scope. That arrangement
 * crashed with `IllegalStateException: ViewModelStore should be set
 * before setGraph call`: `NavHost` calls
 * `NavController.setViewModelStore()` with the store of the *current*
 * back-stack entry, so a second visit to Profile handed the shared
 * controller a different `ViewModelStore` while its `backQueue` was
 * already populated — and `setViewModelStore` requires an empty
 * back queue. Registering the destinations on the root graph gives
 * every destination the root controller's single store, which is the
 * same lifetime as the graph itself.
 *
 * The P8 paywall reaches the privacy-policy viewer through the same
 * root graph now (see `ShellExtraRouteScope.openPrivacyPolicy`).
 */
internal fun androidx.navigation.NavGraphBuilder.profileGraph(
    navController: NavHostController,
    onBack: () -> Unit = {},
    onOpenPremium: () -> Unit = {},
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
        ProfileScreen(
            onBack = onBack,
            onNavigateToPremium = onOpenPremium,
            onNavigateToNotifications = onOpenNotifications,
            onNavigateToEditBasicInfo = { navController.navigate(ProfileRoute.EditBasicInfo.route) },
            onNavigateToPrivacyPolicy = { navController.navigate(ProfileRoute.PrivacyPolicy.route) },
            onNavigateToMyCommunities = onOpenMyCommunities,
            onNavigateToLogout = onOpenLogout,
            onNavigateToDeleteAccount = { /* dialog is owned by the ProfileScreen itself */ },
        )
    }
    composable(ProfileRoute.EditBasicInfo.route) {
        EditBasicInfoScreen(
            onBack = { navController.popBackStack() },
            onNavigateToChange = { field -> navController.navigate(ProfileRoute.ChangeBasicInfo.build(field.key)) },
        )
    }
    dialog(
        route = ProfileRoute.ChangeBasicInfo.PATTERN,
        arguments = listOf(
            androidx.navigation.navArgument(ProfileRoute.ChangeBasicInfo.ARG_FIELD) { type = NavType.StringType },
        ),
    ) {
        ChangeBasicInfoScreen(
            onNavigateToSuccess = { msg -> navController.navigate(ProfileRoute.ChangeBasicInfoSuccess.build(msg)) },
            onShowError = onShowError,
            onBack = { navController.popBackStack() },
        )
    }
    composable(ProfileRoute.ChangeBasicInfoSuccess.PATTERN) {
        ChangeBasicInfoSuccessScreen(onClose = { navController.popBackStack(ProfileRoute.EditBasicInfo.route, inclusive = false) })
    }
    composable(ProfileRoute.PrivacyPolicy.route) {
        PrivacyPolicyScreen(onBack = { navController.popBackStack() })
    }
}
