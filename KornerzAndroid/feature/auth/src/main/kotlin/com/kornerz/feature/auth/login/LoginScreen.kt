package com.kornerz.feature.auth.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kornerz.feature.auth.AuthRoute

/**
 * Stateful entry point for the Login screen. Collects [LoginViewModel.state]
 * for render and [LoginViewModel.navEvents] for navigation; the latter
 * calls `navController.navigate(...)` so the ViewModel never references
 * the controller directly (architecture §2.4).
 *
 * Navigation semantics:
 * - `NavigateToShell` — the root NavHost owns the cross-graph swap; we
 *   hand it the [onNavigateToShellGraph] callback so the auth feature
 *   never has to know about the shell route. The callback is expected
 *   to `popUpTo(0) { inclusive = true }` so the back-stack is cleared
 *   and the user can't navigate back into the auth flow.
 * - `NavigateToForgotPassword` — push the matching route within the
 *   auth graph.
 */
@Composable
fun LoginScreen(
    navController: NavHostController,
    onNavigateToShellGraph: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                LoginNavEvent.NavigateToShell -> onNavigateToShellGraph()
                LoginNavEvent.NavigateToSignup -> navController.navigate(
                    AuthRoute.Signup.build(step = 1),
                )
                LoginNavEvent.NavigateToForgotPassword -> navController.navigate(
                    AuthRoute.ForgotPassword.route,
                )
            }
        }
    }

    LoginContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}