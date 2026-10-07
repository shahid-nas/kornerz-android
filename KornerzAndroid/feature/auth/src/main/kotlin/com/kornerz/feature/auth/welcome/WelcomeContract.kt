package com.kornerz.feature.auth.welcome

/** UI state for the welcome / landing screen. Empty for now — the screen
 *  renders both CTAs and a tagline regardless of state. */
data class WelcomeState(
    val dummy: Unit = Unit,
)

sealed interface WelcomeEvent {
    data object SignUpTapped : WelcomeEvent
    data object LogInTapped : WelcomeEvent
}

sealed interface WelcomeNavEvent {
    data object NavigateToSignup : WelcomeNavEvent
    data object NavigateToLogin : WelcomeNavEvent
}