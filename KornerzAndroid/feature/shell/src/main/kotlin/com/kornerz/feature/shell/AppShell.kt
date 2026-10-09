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
import kotlinx.coroutines.launch

/**
 * Five-tab bottom-navigation shell. Mirrors iOS `AppTabController`.
 *
 * Phase 3 wires the Kornerz tab to a nested `NavHost` with two
 * destinations — `kornerz/list` (root list view) and
 * `kornerz/{kornerId}` (detail view). Home's carousel/dashboard
 * navigation now routes to the real Korner detail destination rather
 * than the P2 "Coming in Phase 3" snackbar.
 *
 * Pull-to-refresh, per-section loading state and "Coming in Phase N"
 * snackbars live in `feature/home`. Nook detail and Member profile
 * destinations are stubbed snackbars (P4 / P6) and resolved at the
 * shell level — the feature modules never touch `NavController`.
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
            )
            nooksTabGraph()
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
 * Nested graph for the Kornerz tab — owns the list and detail routes
 * (per design decision "One feature module, one nested NavHost inside
 * the Kornerz tab"). Tab-level back-stack rule lives here so the bottom
 * bar visibility rule is satisfied without custom logic.
 */
internal fun androidx.navigation.NavGraphBuilder.kornerzTabGraph(
    onNavEvent: (KornerzNavEvent) -> Unit = {},
    snackbarHostState: SnackbarHostState,
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
                        val message = when (event) {
                            is KornerDetailNavEvent.NavigateToNookDetail -> "Nook detail coming in Phase 4"
                            is KornerDetailNavEvent.NavigateToMemberProfile -> "Member profile coming in Phase 6"
                        }
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    },
                )
            }
        }
    }
}

internal fun androidx.navigation.NavGraphBuilder.nooksTabGraph() =
    tabComposable(ShellTab.Nooks)

internal fun androidx.navigation.NavGraphBuilder.loopsTabGraph() =
    tabComposable(ShellTab.Loops)

internal fun androidx.navigation.NavGraphBuilder.kloudersTabGraph(onLogout: () -> Unit) =
    tabComposableWithLogout(ShellTab.Klouders, onLogout)
