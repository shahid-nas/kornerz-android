package com.kornerz.feature.auth.recovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController

@Composable
fun ForgotPasswordScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is ForgotPasswordNavEvent.NavigateToOtp ->
                    navController.navigate(com.kornerz.feature.auth.AuthRoute.OtpConfirm.build(event.email))
            }
        }
    }
    ForgotPasswordContent(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        onBack = { navController.popBackStack() },
    )
}

@Composable
fun OtpConfirmScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: OtpConfirmViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is OtpConfirmNavEvent.NavigateToChangePassword -> navController.navigate(
                    com.kornerz.feature.auth.AuthRoute.ChangePassword.build(event.email, event.resetToken),
                )
            }
        }
    }
    OtpConfirmContent(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        onBack = { navController.popBackStack() },
    )
}

@Composable
fun ChangePasswordScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.navEvents.collect {
            navController.popBackStack(
                route = com.kornerz.feature.auth.AuthRoute.Welcome.route,
                inclusive = false,
            )
        }
    }
    ChangePasswordContent(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        onBack = { navController.popBackStack() },
    )
}
