package com.kornerz.kornerz

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kornerz.core.network.forceupdate.ForceUpdateRepository
import com.kornerz.core.network.forceupdate.ForceUpdateState
import com.kornerz.core.storage.country.CountryCodesRepository
import com.kornerz.core.storage.interests.KornerInterestRepository
import com.kornerz.core.ui.components.ForceUpdateSheet
import com.kornerz.feature.auth.AUTH_GRAPH_ROUTE
import com.kornerz.feature.auth.authGraph
import com.kornerz.feature.shell.AppShell
import com.kornerz.feature.splash.SplashScreen
import com.kornerz.feature.subscription.navigation.SubscriptionRoute
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts the splash, the auth graph, and the shell graph. The root
 * NavHost swaps between `auth_graph` and `shell_graph` based on session
 * state — every transition pops the back-stack (architecture §10.2).
 *
 * On top of the NavHost, the non-dismissible [ForceUpdateSheet] is
 * rendered whenever Remote Config reports a Required state.
 */
@Composable
fun AppRoot(
    navController: NavHostController = rememberNavController(),
    countryRepository: CountryCodesRepository,
    interestsRepository: KornerInterestRepository,
    forceUpdateViewModel: ForceUpdateOverlayViewModel = hiltViewModel(),
) {
    val forceUpdateState by forceUpdateViewModel.state.collectAsStateWithLifecycle()
    val applicationId = LocalContext.current.packageName

    val applicationContext = LocalContext.current.applicationContext
    val premiumGate = remember(applicationContext) {
        EntryPointAccessors.fromApplication(
            applicationContext,
            PremiumGateEntryPoint::class.java,
        ).premiumGate()
    }

    val navigateToAuthGraph: () -> Unit = {
        navController.navigate(AUTH_GRAPH_ROUTE) {
            popUpTo(0) { inclusive = true }
        }
    }
    val navigateToShellGraph: () -> Unit = {
        navController.navigate(Routes.SHELL_GRAPH) {
            popUpTo(0) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                navController = navController,
                onNavigateToAuthGraph = navigateToAuthGraph,
                onNavigateToShellGraph = navigateToShellGraph,
            )
        }
        authGraph(
            navController = navController,
            countryRepository = countryRepository,
            interestsRepository = interestsRepository,
            onNavigateToShellGraph = navigateToShellGraph,
        )
        composable(Routes.SHELL_GRAPH) {
            // P8 — the composition root supplies the concrete subscription
            // routes, because only `app` may import both `feature/shell`
            // and `feature/subscription`.
            AppShell(
                onLogout = navigateToAuthGraph,
                extraRoutes = rememberSubscriptionRoutes(premiumGate),
                onNavigateToPremium = {
                    navController.navigate(SubscriptionRoute.Plans.route) {
                        launchSingleTop = true
                    }
                },
            )
        }
    }

    (forceUpdateState as? ForceUpdateState.Required)?.let { required ->
        ForceUpdateSheet(
            currentVersion = required.currentVersion,
            minVersion = required.minVersion,
            applicationId = applicationId,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@HiltViewModel
class ForceUpdateOverlayViewModel @Inject constructor(
    private val forceUpdateRepository: ForceUpdateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ForceUpdateState>(ForceUpdateState.Idle)
    val state: StateFlow<ForceUpdateState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            forceUpdateRepository.state.collect { _state.value = it }
        }
    }
}

private object Routes {
    const val SPLASH = "splash"
    const val SHELL_GRAPH = "shell_graph"
}