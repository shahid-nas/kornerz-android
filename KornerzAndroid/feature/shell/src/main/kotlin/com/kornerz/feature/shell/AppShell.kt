package com.kornerz.feature.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * Five-tab bottom-navigation shell. Mirrors iOS `AppTabController`:
 * one nested graph per top-level destination, each currently rendering
 * an empty-state placeholder. P1+ adds the first real screen to a tab;
 * subsequent phases swap each tab's content.
 */
@Composable
fun AppShell(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

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
            kloudersTabGraph()
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

internal fun androidx.navigation.NavGraphBuilder.homeTabGraph() =
    tabComposable(ShellTab.Home)

internal fun androidx.navigation.NavGraphBuilder.kornerzTabGraph() =
    tabComposable(ShellTab.Kornerz)

internal fun androidx.navigation.NavGraphBuilder.nooksTabGraph() =
    tabComposable(ShellTab.Nooks)

internal fun androidx.navigation.NavGraphBuilder.loopsTabGraph() =
    tabComposable(ShellTab.Loops)

internal fun androidx.navigation.NavGraphBuilder.kloudersTabGraph() =
    tabComposable(ShellTab.Klouders)