package com.kornerz.feature.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kornerz.feature.home.HomeScreen
import com.kornerz.feature.kornerz.KornerzRoute
import com.kornerz.feature.kornerz.KornerzScreen
import com.kornerz.feature.kornerz.KornerzNavEvent
import com.kornerz.feature.kornerz.detail.KornerDetailNavEvent
import com.kornerz.feature.kornerz.detail.KornerDetailScreen
import com.kornerz.feature.kornerz.detail.KornerDetailViewModel
import com.kornerz.feature.nooks.NooksRoute
import com.kornerz.feature.nooks.detail.NookDetailNavEvent
import com.kornerz.feature.nooks.detail.NookDetailScreen
import com.kornerz.feature.nooks.detail.NookDetailViewModel
import com.kornerz.feature.nooks.list.NooksScreen
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

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                ShellNavEvent.Logout -> onLogout()
            }
        }
    }

    Scaffold(
        bottomBar = {
            KornerzBottomBar(
                currentRoute = currentRoute,
                onSelect = { tab -> navigateToTab(navController, tab) },
            )
        },
        snackbarHost = { SnackbarHost(comingSoonSnackbar) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ShellTab.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            homeTabGraph(onNavigateToKornerDetail = { id ->
                navigateToKornerDetail(navController, id)
            })
            kornerzTabGraph(
                onNavEvent = { event ->
                    when (event) {
                        is KornerzNavEvent.NavigateToKornerDetail -> navigateToKornerDetail(
                            navController = navController,
                            kornerId = event.kornerId,
                        )
                    }
                },
                snackbarHostState = comingSoonSnackbar,
                onNookDetail = { nookId ->
                    navigateToNookDetailInKornerzTab(navController, nookId)
                },
            )
            nooksTabGraph(
                onNookDetail = { nookId ->
                    navigateToNookDetailInNooksTab(navController, nookId)
                },
                onKornerDetail = { kornerId ->
                    navController.navigate(ShellTab.Kornerz.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    navController.navigate(KornerzRoute.Detail.build(kornerId))
                },
            )
            loopsTabGraph()
            kloudersTabGraph(onLogout = viewModel::onLogout)
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

private fun navigateToKornerDetail(navController: NavHostController, kornerId: String) {
    navController.navigate(ShellTab.Kornerz.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    navController.navigate(KornerzRoute.Detail.build(kornerId))
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
) {
    composable(ShellTab.Home.route) {
        HomeScreen(
            modifier = Modifier,
            onNavigateToKornerDetail = onNavigateToKornerDetail,
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
) {
    composable(ShellTab.Kornerz.route) {
        val nested = rememberNavController()
        val scope = rememberCoroutineScope()
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
 * Nested graph for the Nooks tab — hosts the cross-Korner nooks feed
 * list and the per-tab nook detail destination. Back-press inside
 * `nooks/{nookId}` pops to `nooks/list`; the bottom bar stays
 * pinned.
 */
internal fun androidx.navigation.NavGraphBuilder.nooksTabGraph(
    onNookDetail: (String) -> Unit = {},
    onKornerDetail: (String) -> Unit = {},
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
                            is com.kornerz.feature.nooks.list.NooksNavEvent.NavigateToNookDetail -> {
                                onNookDetail(event.nookId)
                            }
                            is com.kornerz.feature.nooks.list.NooksNavEvent.NavigateToKornerDetail -> {
                                onKornerDetail(event.kornerId)
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

internal fun androidx.navigation.NavGraphBuilder.loopsTabGraph() =
    tabComposable(ShellTab.Loops)

internal fun androidx.navigation.NavGraphBuilder.kloudersTabGraph(onLogout: () -> Unit) =
    tabComposableWithLogout(ShellTab.Klouders, onLogout)
