package com.kornerz.feature.auth.signup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kornerz.core.network.community.CommunitiesRepository
import com.kornerz.core.storage.country.CountryCodesRepository

/**
 * Stateful entry point for the signup wizard. Every step is rendered
 * with the iOS-matching branded layout (Aqua background, cloud
 * decoration, white Satoshi title, transparent bar text field, black
 * capsule "Next" CTA). Navigation between steps is a state mutation —
 * no Compose `Navigation` calls for in-wizard step transitions.
 */
@Composable
fun SignupScreen(
    navController: NavHostController,
    countryRepository: CountryCodesRepository,
    communitiesRepository: CommunitiesRepository,
    onNavigateToShellGraph: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    viewModel: SignupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                SignupNavEvent.NavigateToShell -> onNavigateToShellGraph()
                SignupNavEvent.NavigateBack -> navController.popBackStack()
            }
        }
    }

    val onBack: () -> Unit = { viewModel.onEvent(SignupEvent.Back) }
    val onContinue: () -> Unit = { viewModel.onEvent(SignupEvent.Continue) }

    when (state.step) {
        1 -> SignupFullNameContent(
            state = state,
            onEvent = viewModel::onEvent,
            onBack = onBack,
            modifier = modifier,
        )
        2 -> SignupEmailStep(
            state = state,
            onEvent = viewModel::onEvent,
            onBack = onBack,
        )
        3 -> SignupPhoneStep(
            state = state,
            onEvent = viewModel::onEvent,
            onBack = onBack,
            repository = countryRepository,
        )
        4 -> SignupLanguageStep(
            state = state,
            onEvent = viewModel::onEvent,
            onBack = onBack,
        )
        5 -> SignupInterestsStep(
            state = state,
            onEvent = viewModel::onEvent,
            onBack = onBack,
            repository = communitiesRepository,
        )
        6 -> SignupNicknameStep(
            state = state,
            onEvent = viewModel::onEvent,
            onBack = onBack,
            onSuggestNickname = { viewModel.onEvent(SignupEvent.SuggestNickname) },
        )
        7 -> SignupPhotoStep(
            state = state,
            onEvent = viewModel::onEvent,
            onBack = onBack,
        )
        8 -> SignupPasswordStep(
            state = state,
            onEvent = viewModel::onEvent,
            onBack = onBack,
        )
    }
}
