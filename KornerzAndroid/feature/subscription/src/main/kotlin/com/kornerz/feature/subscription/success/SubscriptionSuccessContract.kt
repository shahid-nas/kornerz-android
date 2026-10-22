package com.kornerz.feature.subscription.success

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

/**
 * Owns the success screen's single navigation effect.
 *
 * The ViewModel exists for contract consistency: every screen in P0–P8 has
 * a `@HiltViewModel`, so a reviewer can assume the UDF shape holds. It is
 * deliberately trivial — no repository is injected, because **this screen
 * performs no data work at all** (see [SubscriptionSuccessNavEvent]).
 */
@HiltViewModel
class SubscriptionSuccessViewModel @Inject constructor() : ViewModel() {

    private val _navEvents = Channel<SubscriptionSuccessNavEvent>(Channel.BUFFERED)

    /** Single-event stream drained by the shell. */
    val navEvents: Flow<SubscriptionSuccessNavEvent> = _navEvents.receiveAsFlow()

    /**
     * The "Done" tap. Pops the route and marks the paywall gate satisfied
     * for the process, so a user who just paid is never re-gated.
     */
    fun onDoneTap() {
        _navEvents.trySend(SubscriptionSuccessNavEvent.Dismissed)
    }
}

/**
 * The success screen's only navigation effect.
 *
 * `SubscriptionSuccessScreen` is **pure presentation**. The entitlement
 * write happens upstream in the purchase flow — in `SubscriptionViewModel`
 * on Android, mirroring iOS, where `SubscriptionController.updateSubscription`
 * writes upstream (`:76-79`) and `SubscriptionSuccessScreen` never touches
 * the backend. So there is deliberately no restored/refetch here: doing so
 * would both duplicate the write and delay the tap.
 */
sealed interface SubscriptionSuccessNavEvent {

    /** "Done" tapped: pop back to the pre-gate destination, gate satisfied. */
    data object Dismissed : SubscriptionSuccessNavEvent
}