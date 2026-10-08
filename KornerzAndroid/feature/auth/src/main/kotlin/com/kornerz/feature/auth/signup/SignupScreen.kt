package com.kornerz.feature.auth.signup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kornerz.core.storage.country.CountryCodesRepository
import com.kornerz.core.storage.interests.KornerInterestRepository

/**
 * Stateful entry point for the signup wizard. The screen reads the
 * wizard state and routes to the matching step composable based on
 * [SignupWizardState.step]. Navigation between steps is purely a
 * state mutation — no Compose `Navigation` calls for in-wizard step
 * transitions.
 */
@Composable
fun SignupScreen(
    navController: NavHostController,
    countryRepository: CountryCodesRepository,
    interestsRepository: KornerInterestRepository,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    viewModel: SignupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                SignupNavEvent.NavigateToShell -> {
                    navController.popBackStack(
                        route = com.kornerz.feature.auth.AuthRoute.Welcome.route,
                        inclusive = true,
                    )
                }
                SignupNavEvent.NavigateBack -> navController.popBackStack()
            }
        }
    }

    SignupStepScaffold(
        title = stepTitle(state.step),
        stepNumber = state.step,
        totalSteps = 9,
        state = state,
        onEvent = viewModel::onEvent,
        continueLabel = if (state.step == 9) "Create account" else "Continue",
        modifier = modifier,
    ) {
        when (state.step) {
            1 -> SignupFullNameStep(state, viewModel::onEvent)
            2 -> SignupNicknameStep(state, viewModel::onEvent)
            3 -> SignupEmailStep(state, viewModel::onEvent)
            4 -> SignupPasswordStep(state, viewModel::onEvent)
            5 -> SignupPhoneStep(state, viewModel::onEvent)
            6 -> SignupCountryCodeStep(state, viewModel::onEvent, countryRepository)
            7 -> SignupLanguageStep(state, viewModel::onEvent)
            8 -> SignupInterestsStep(state, viewModel::onEvent, interestsRepository)
            9 -> SignupPhotoStep(state, viewModel::onEvent)
        }
    }
}

private fun stepTitle(step: Int): String = when (step) {
    1 -> "What's your name?"
    2 -> "Pick a nickname"
    3 -> "What's your email?"
    4 -> "Create a password"
    5 -> "Add your phone number"
    6 -> "Choose your country"
    7 -> "Choose your language"
    8 -> "Pick your favorites"
    9 -> "Add a photo"
    else -> ""
}