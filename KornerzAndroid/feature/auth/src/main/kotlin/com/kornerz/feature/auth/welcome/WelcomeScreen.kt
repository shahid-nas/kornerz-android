package com.kornerz.feature.auth.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kornerz.core.ui.components.KornerzButton
import com.kornerz.core.ui.components.KornerzButtonStyle
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.auth.AuthRoute
import com.kornerz.feature.auth.R

/**
 * Entry point of the auth graph. Mirrors the iOS `SplashScreen`
 * (`kornerz/Kornerz/SwiftUI/OnBoarding/Splash/SplashScreen.swift`)
 * — a full-bleed background image with the Kornerz logo at the top, a
 * "Sign Up" filled CTA + "Log In" outlined CTA, and the
 * "A space for thinkers" tagline at the bottom.
 *
 * Picked over a plain Login start destination so a fresh install sees
 * the brand surface first; both buttons push the matching route within
 * the auth graph.
 */
@Composable
fun WelcomeScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                WelcomeNavEvent.NavigateToSignup -> navController.navigate(
                    AuthRoute.Signup.build(step = 1),
                )
                WelcomeNavEvent.NavigateToLogin -> navController.navigate(AuthRoute.Login.route)
            }
        }
    }

    WelcomeContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
fun WelcomeContent(
    state: WelcomeState,
    onEvent: (WelcomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg_onboarding),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.space8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(Spacing.space32))
            Image(
                painter = painterResource(id = R.drawable.logo_kornerz),
                contentDescription = "Kornerz",
                modifier = Modifier.width(220.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space12),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                KornerzButton(
                    text = "Sign Up",
                    onClick = { onEvent(WelcomeEvent.SignUpTapped) },
                    modifier = Modifier.fillMaxWidth(),
                    textColor = Color.Black,
                )
                Spacer(modifier = Modifier.height(Spacing.space6))
                KornerzButton(
                    text = "Log In",
                    onClick = { onEvent(WelcomeEvent.LogInTapped) },
                    style = KornerzButtonStyle.Outlined,
                    modifier = Modifier.fillMaxWidth(),
                    textColor = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.space12))
            Text(
                text = "A space for thinkers",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                ),
                modifier = Modifier.padding(bottom = Spacing.space12),
            )
        }

        @Suppress("UNUSED_EXPRESSION") state
    }
}