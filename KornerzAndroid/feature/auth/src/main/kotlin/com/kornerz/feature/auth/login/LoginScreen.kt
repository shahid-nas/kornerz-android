package com.kornerz.feature.auth.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
 * - `NavigateToShell` — the root NavHost owns the auth→shell swap. This
 *   screen pops the auth back-stack so the user can't accidentally
 *   navigate back into Login after signing in.
 * - `NavigateToSignup` / `NavigateToForgotPassword` — push the matching
 *   route within the auth graph.
 */
@Composable
fun LoginScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                LoginNavEvent.NavigateToShell -> {
                    navController.popBackStack(
                        route = AuthRoute.Welcome.route,
                        inclusive = true,
                    )
                }
                LoginNavEvent.NavigateToSignup -> navController.navigate(
                    AuthRoute.Signup.build(step = 1),
                )
                LoginNavEvent.NavigateToForgotPassword -> navController.navigate(
                    AuthRoute.ForgotPassword.route,
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoginContent(state = state, onEvent = viewModel::onEvent)
    }
}