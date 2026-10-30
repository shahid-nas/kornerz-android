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
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.core.ui.components.ForceUpdateSheet
import com.kornerz.feature.auth.AUTH_GRAPH_ROUTE
import com.kornerz.feature.auth.authGraph
import com.kornerz.feature.shell.AppShell
import com.kornerz.feature.subscription.navigation.SubscriptionRoute
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts the auth graph and the shell graph. The root NavHost swaps
 * between `auth_graph` and `shell_graph` based on session state — every
 * transition pops the back-stack (architecture §10.2).
 *
 * There is no splash screen: the platform splash (the launcher icon on
 * the white window background) carries the branding, and the cold-start
 * session check happens in [SessionGateViewModel]. The NavHost is not
 * composed until the gate has resolved, so a returning user goes
 * straight to the shell without the welcome screen flashing first.
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
    sessionGateViewModel: SessionGateViewModel = hiltViewModel(),
) {
    val forceUpdateState by forceUpdateViewModel.state.collectAsStateWithLifecycle()
    val sessionRoute by sessionGateViewModel.route.collectAsStateWithLifecycle()
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

    // Nothing is composed until the cold-start gate has resolved, so the
    // auth graph never flashes on the way to the shell for a returning
    // user. `null` means the session is still being read.
    val startDestination = when (sessionRoute) {
        SessionRoute.ShellGraph -> Routes.SHELL_GRAPH
        SessionRoute.AuthGraph -> AUTH_GRAPH_ROUTE
        null -> null
    }
    if (startDestination != null) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
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

/**
 * Cold-start session gate. Replaces the splash router that used to own
 * this decision: it reads the persisted session and reports where the
 * app should open.
 *
 * `SessionRepository.data` is backed by DataStore, so the first emission
 * arrives after one disk read and is already `Authenticated` or
 * `Anonymous` — there is no meaningful `Loading` window to wait out.
 */
@HiltViewModel
class SessionGateViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _route = MutableStateFlow<SessionRoute?>(null)
    val route: StateFlow<SessionRoute?> = _route.asStateFlow()

    init {
        viewModelScope.launch {
            val session = sessionRepository.data.first()
            _route.value = when (session) {
                is SessionState.Authenticated -> SessionRoute.ShellGraph
                SessionState.Anonymous, SessionState.Loading -> SessionRoute.AuthGraph
            }
        }
    }
}

/**
 * Where the app opens. [AuthGraph] is the fallback; a persisted session
 * upgrades the start destination to [ShellGraph].
 */
enum class SessionRoute {
    AuthGraph,
    ShellGraph,
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
    const val SHELL_GRAPH = "shell_graph"
}