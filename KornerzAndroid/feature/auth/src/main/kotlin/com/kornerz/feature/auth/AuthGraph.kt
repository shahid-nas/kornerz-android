package com.kornerz.feature.auth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.kornerz.core.storage.country.CountryCodesRepository
import com.kornerz.core.storage.interests.KornerInterestRepository
import com.kornerz.feature.auth.login.LoginScreen
import com.kornerz.feature.auth.recovery.ChangePasswordScreen
import com.kornerz.feature.auth.recovery.ForgotPasswordScreen
import com.kornerz.feature.auth.recovery.OtpConfirmScreen
import com.kornerz.feature.auth.signup.SignupScreen
import com.kornerz.feature.auth.welcome.WelcomeScreen

/**
 * Adds the `auth_graph` nested graph to the supplied [NavGraphBuilder].
 * The root NavHost passes its [NavHostController] so child screens can
 * navigate within the auth graph (architecture §2.4 — ViewModels never
 * touch the controller directly).
 *
 * `WelcomeScreen` is the entry destination for unauthenticated users
 * (mirrors the iOS `SplashScreen` in `kornerz/Kornerz/SwiftUI/OnBoarding/Splash/`).
 */
fun NavGraphBuilder.authGraph(
    navController: NavHostController,
    countryRepository: CountryCodesRepository,
    interestsRepository: KornerInterestRepository,
) {
    navigation(
        route = AUTH_GRAPH_ROUTE,
        startDestination = AuthRoute.Welcome.route,
    ) {
        composable(AuthRoute.Welcome.route) {
            WelcomeScreen(navController = navController)
        }
        composable(AuthRoute.Login.route) {
            LoginScreen(navController = navController)
        }
        composable(
            route = AuthRoute.Signup.route,
            arguments = listOf(
                navArgument(AuthRoute.Signup.ARG_STEP) { type = NavType.IntType; defaultValue = 1 },
            ),
        ) {
            SignupScreen(
                navController = navController,
                countryRepository = countryRepository,
                interestsRepository = interestsRepository,
            )
        }
        composable(AuthRoute.ForgotPassword.route) {
            ForgotPasswordScreen(navController = navController)
        }
        composable(
            route = AuthRoute.OtpConfirm.route,
            arguments = listOf(
                navArgument(AuthRoute.OtpConfirm.ARG_EMAIL) { type = NavType.StringType },
            ),
        ) {
            OtpConfirmScreen(navController = navController)
        }
        composable(
            route = AuthRoute.ChangePassword.route,
            arguments = listOf(
                navArgument(AuthRoute.ChangePassword.ARG_EMAIL) { type = NavType.StringType },
                navArgument(AuthRoute.ChangePassword.ARG_TOKEN) { type = NavType.StringType },
            ),
        ) {
            ChangePasswordScreen(navController = navController)
        }
    }
}

const val AUTH_GRAPH_ROUTE = "auth_graph"