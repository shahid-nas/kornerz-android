package com.kornerz.feature.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * Branded splash. Routes to auth or shell once [SplashViewModel] emits
 * a [SplashNavEvent]; the screen collector calls the [NavHostController].
 * The ViewModel never touches the controller directly (architecture
 * §2.4). The destination routes are owned by `feature/auth` and
 * `feature/shell` so we only emit a stable identifier here.
 */
@Composable
fun SplashScreen(
    navController: NavHostController,
    onNavigateToAuthGraph: () -> Unit,
    onNavigateToShellGraph: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                SplashNavEvent.NavigateToAuthGraph -> onNavigateToAuthGraph()
                SplashNavEvent.NavigateToShellGraph -> onNavigateToShellGraph()
            }
        }
    }
    SplashContent(state = state, modifier = modifier)
    @Suppress("UNUSED_EXPRESSION") navController
}

@Composable
fun SplashContent(
    state: SplashState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
    @Suppress("UNUSED_EXPRESSION") state
}

@Preview(showBackground = true)
@Composable
private fun SplashContentPreview() {
    KornerzTheme { SplashContent(state = SplashState()) }
}