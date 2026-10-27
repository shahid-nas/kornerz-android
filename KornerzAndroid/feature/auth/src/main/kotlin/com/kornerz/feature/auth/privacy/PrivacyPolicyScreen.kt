package com.kornerz.feature.auth.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kornerz.feature.auth.AuthRoute

/**
 * Stateful entry point for the standalone Privacy Policy screen.
 * Owns navigation: on Accept it pushes `auth/signup/1` (the start of
 * the wizard), and on Decline it pops back to the previous auth
 * destination (the Welcome screen).
 */
@Composable
fun PrivacyPolicyScreen(
    navController: NavHostController,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    viewModel: PrivacyPolicyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                PrivacyPolicyNavEvent.NavigateToSignup -> navController.navigate(
                    AuthRoute.Signup.build(step = 1),
                )
                PrivacyPolicyNavEvent.NavigateBack -> navController.popBackStack()
            }
        }
    }

    PrivacyPolicyContent(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        onBack = { navController.popBackStack() },
    )
}