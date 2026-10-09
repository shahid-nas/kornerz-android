package com.kornerz.feature.home

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Navigation entry for the Home tab. Public `composable` factory kept
 * separate from the private `HomeScreen` Composable so the shell's
 * `NavHost` can wire the destination without depending on internal
 * state shapes.
 */
object HomeRoute {

    /** Route used by the tab host. */
    const val Route: String = "home_root"

    /**
     * Compose the Home tab destination. Routes the VM's `navEvents` to
     * [onNavEvent] so the host can show a "Coming in Phase N" snackbar.
     */
    fun NavGraphBuilder.composable(onNavEvent: (HomeNavEvent) -> Unit) {
        composable(Route) {
            val viewModel: HomeViewModel = hiltViewModel()
            LaunchedEffect(viewModel) {
                viewModel.navEvents.collect { onNavEvent(it) }
            }
            HomeScreen(viewModel = viewModel, modifier = Modifier)
        }
    }
}
