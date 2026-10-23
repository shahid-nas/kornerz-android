package com.kornerz.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.network.AuthRepository
import com.kornerz.core.network.fcm.FcmTokenRegistrar
import com.kornerz.feature.profile.accountdeletion.UserDeletedHisAccountEventBus
import com.kornerz.feature.shell.deeplink.DeepLinkManager
import com.kornerz.feature.shell.deeplink.DeepLinkResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the shell-level navigation effects and the paywall gate's lifecycle.
 *
 * The [PremiumGate] is collected here — and only here — so the gate has a
 * single owner and cannot be evaluated from a second place by accident.
 *
 * The P10 deep-link collaborators are exposed here for the same reason:
 * the shell is the only place that owns the navigation graph, so it is
 * the only place that may hold the [DeepLinkManager] and the
 * [DeepLinkResolver].
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val fcmTokenRegistrar: FcmTokenRegistrar,
    private val userDeletedEventBus: UserDeletedHisAccountEventBus,
    private val premiumGate: PremiumGate,
    private val entitlementReconciler: SubscriptionEntitlementReconciler,
    val deepLinkManager: DeepLinkManager,
    val deepLinkResolver: DeepLinkResolver,
) : ViewModel() {

    private val _navEvents = Channel<ShellNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<ShellNavEvent> = _navEvents.receiveAsFlow()

    private val _premiumGateRequests = MutableStateFlow(0)

    /**
     * A counter rather than a boolean: the shell collector reacts to a
     * *rising edge*, and a plain flag would not re-fire if the user
     * dismissed the paywall, logged back in, and should see it again after
     * [PremiumGate.reset].
     */
    val premiumGateRequests: StateFlow<Int> = _premiumGateRequests.asStateFlow()

    init {
        // P6 (task 9.5) — listen for the shared `.userDeletedHisAccount`
        // event bus and re-emit as a [ShellNavEvent.Logout] so the root
        // NavHost swaps back to the auth graph.
        viewModelScope.launch {
            userDeletedEventBus.events.collect {
                // P8: an account deletion must clear the gate's latches
                // before the shell leaves, so the next login evaluates
                // from scratch.
                premiumGate.reset()
                _navEvents.trySend(ShellNavEvent.Logout)
            }
        }

        // P8: the shell graph being composed *is* the post-login call
        // site — `LoginVC.showSubscriptionScreen()` (`:219-232`) fires as
        // login hands off to the shell.
        requestPremiumGateEvaluation()
    }

    /**
     * The gate's two firing points, per the `premium-gate` spec:
     * post-login (this method, called from `init`) and shell resume
     * ([onShellResumed]).
     *
     * No destination-change or tab-switch evaluation exists anywhere in
     * this class — a free user who dismissed the paywall must not have it
     * slam back on every tab change.
     */
    fun requestPremiumGateEvaluation() {
        viewModelScope.launch {
            // Reconcile first so the gate evaluates against the server's
            // verdict rather than a stale local expiry.
            entitlementReconciler.reconcile()
            if (premiumGate.requestPresent()) {
                _premiumGateRequests.value = _premiumGateRequests.value + 1
            }
        }
    }

    /** Shell resume — the second (and last) gate call site. */
    fun onShellResumed() {
        requestPremiumGateEvaluation()
    }

    /** P8: the paywall was pushed, so clause 1 now blocks a re-push. */
    fun onPremiumRouteOpened() = premiumGate.markPlansRouteOpen(true)

    /** P8: the paywall was popped; clause 1 no longer blocks. */
    fun onPremiumRouteClosed() = premiumGate.markPlansRouteOpen(false)

    fun onLogout() {
        viewModelScope.launch {
            // Clear the latches first: a logout is the boundary the
            // `premium-gate` spec names for `reset()`, so the next login
            // re-evaluates from scratch.
            premiumGate.reset()
            fcmTokenRegistrar.clearOnLogout()
            _navEvents.trySend(ShellNavEvent.Logout)
        }
    }
}

sealed interface ShellNavEvent {
    data object Logout : ShellNavEvent
}