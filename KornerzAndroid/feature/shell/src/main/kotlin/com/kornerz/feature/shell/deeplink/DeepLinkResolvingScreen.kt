package com.kornerz.feature.shell.deeplink

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The deep-link interstitial (inventory 13.1). Mirrors iOS
 * `DeeplinkViewController` (`kornerz/Kornerz/DeepLinks/DeeplinkViewController.swift`).
 *
 * The interstitial is a transient full-screen route: it takes the head
 * of the [DeepLinkManager]'s queue, asks the [DeepLinkResolver] whether
 * the link is routable, and then either navigates to the destination or
 * reports the failure. It removes itself from the back stack either
 * way — iOS's `removeSelfFromNavigation()` (`:41-49`).
 *
 * The route carries **no** navigation argument: the link is handed to
 * the interstitial through the manager, so no id has to be encoded into
 * the route.
 */
sealed interface DeepLinkResolvingState {

    /** The link is being resolved. */
    data object Resolving : DeepLinkResolvingState

    /** The link resolved to a live destination. */
    data class Resolved(val target: DeepLinkTarget) : DeepLinkResolvingState

    /** The link could not be routed. */
    data object Failed : DeepLinkResolvingState
}

/**
 * Resolves the pending deep link. The link is taken from the manager's
 * queue and handed to the resolver; the outcome is exposed as state so
 * the screen can act on it exactly once.
 */
@HiltViewModel
class DeepLinkResolvingViewModel @Inject constructor(
    private val deepLinkManager: DeepLinkManager,
    private val deepLinkResolver: DeepLinkResolver,
) : ViewModel() {

    private val _state = MutableStateFlow<DeepLinkResolvingState>(DeepLinkResolvingState.Resolving)
    val state: StateFlow<DeepLinkResolvingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val deeplink = deepLinkManager.takeNext()
            _state.value = if (deeplink == null) {
                // Nothing queued — the manager's queue was drained
                // between the shell pushing the route and the
                // interstitial composing. There is no link to report.
                DeepLinkResolvingState.Failed
            } else {
                when (val resolution = deepLinkResolver.resolve(deeplink)) {
                    is DeepLinkResolution.Routable ->
                        DeepLinkResolvingState.Resolved(resolution.target)

                    DeepLinkResolution.NotRoutable ->
                        DeepLinkResolvingState.Failed
                }
            }
        }
    }
}

/**
 * The deep-link interstitial's host.
 *
 * @param onResolved called with the destination to navigate to; the
 *   caller is responsible for removing the interstitial from the back
 *   stack.
 * @param onFailed called when the link is not routable; the caller
 *   surfaces the alert and returns the user to the Home tab.
 */
@Composable
fun DeepLinkResolvingScreen(
    onResolved: (DeepLinkTarget) -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeepLinkResolvingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        when (val current = state) {
            is DeepLinkResolvingState.Resolved -> onResolved(current.target)
            DeepLinkResolvingState.Failed -> onFailed()
            DeepLinkResolvingState.Resolving -> Unit
        }
    }

    DeepLinkResolvingContent(modifier = modifier.fillMaxSize())
}
