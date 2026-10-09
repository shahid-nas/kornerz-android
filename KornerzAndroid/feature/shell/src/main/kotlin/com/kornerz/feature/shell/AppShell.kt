package com.kornerz.feature.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kornerz.feature.home.HomeScreen

/**
 * Five-tab bottom-navigation shell. Mirrors iOS `AppTabController`.
 *
 * Phase 2 wires the Home tab to the new `HomeScreen` from
 * `feature/home`. The remaining tabs keep their P0 "Coming in Phase N"
 * placeholder. Pull-to-refresh, per-section loading state and `Coming
 * in Phase N` snackbars live in `feature/home`.
 */
@Composable
fun AppShell(
    navController: NavHostController = rememberNavController(),
    onLogout: () -> Unit = {},
    viewModel: ShellViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(Unit) {
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
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ShellTab.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            homeTabGraph()
            kornerzTabGraph()
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

internal fun androidx.navigation.NavGraphBuilder.homeTabGraph() {
    composable(ShellTab.Home.route) {
        HomeScreen(modifier = Modifier)
    }
}

internal fun androidx.navigation.NavGraphBuilder.kornerzTabGraph() =
    tabComposable(ShellTab.Kornerz)

internal fun androidx.navigation.NavGraphBuilder.nooksTabGraph() =
    tabComposable(ShellTab.Nooks)

internal fun androidx.navigation.NavGraphBuilder.loopsTabGraph() =
    tabComposable(ShellTab.Loops)

internal fun androidx.navigation.NavGraphBuilder.kloudersTabGraph(onLogout: () -> Unit) =
    tabComposableWithLogout(ShellTab.Klouders, onLogout)
