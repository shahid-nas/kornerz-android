package com.kornerz.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kornerz.feature.shell.AppShell
import com.kornerz.feature.splash.SplashScreen
import kotlinx.coroutines.delay

/**
 * Top-level navigation host. Two routes:
 *
 * - `splash` — branded splash. A `LaunchedEffect` waits 1 second then
 *   navigates to `main`.
 * - `main` — the [AppShell] (bottom-nav + 5 tabs).
 *
 * Spec: docs/openspec/changes/p0-android-foundation/specs/android-app-shell/spec.md
 * §"App launches into splash screen" + §"Each tab renders an
 * empty-state placeholder".
 */
@Composable
fun AppRoot(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) {
            LaunchedEffect(Unit) {
                delay(SPLASH_DURATION_MS)
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
            SplashScreen()
        }
        composable(Routes.MAIN) {
            AppShell()
        }
    }
}

private object Routes {
    const val SPLASH = "splash"
    const val MAIN = "main"
}

private const val SPLASH_DURATION_MS = 1_000L