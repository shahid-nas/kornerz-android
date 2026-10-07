package com.kornerz.feature.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * Branded splash shown on cold launch. Renders the wordmark on the
 * brand background; the [AppRoot] host navigates away after the
 * configured delay (see `app/.../AppRoot.kt`).
 *
 * Intentionally stateless and free of business logic in P0; a real
 * brand mark / logo will replace the `Text` placeholder when the
 * designer deliverable lands.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.space4),
            ) {
                Text(
                    text = "K",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Kornerz",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    KornerzTheme(darkTheme = false) {
        SplashScreen()
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenDarkPreview() {
    KornerzTheme(darkTheme = true) {
        SplashScreen()
    }
}