package com.kornerz.feature.subscription.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.SubscriptionProductType
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.subscription.data.SubscriptionError
import com.kornerz.feature.subscription.data.SubscriptionException
import com.kornerz.feature.subscription.data.SubscriptionRepository
import com.kornerz.feature.subscription.data.billing.BillingConnectionState
import com.kornerz.feature.subscription.data.billing.BillingPurchaseResult
import com.kornerz.feature.subscription.data.billing.BillingUnavailableReason
import com.kornerz.feature.subscription.data.billing.SubscriptionBillingGateway
import com.kornerz.feature.subscription.data.deriveExpiryFrom
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UDF ViewModel for the plans screen (inventory 11.1 + 11.2).
 *
 * Owns the Play purchase flow end to end:
 * resolve the catalog → select a plan → launch the sheet → acknowledge →
 * grant the entitlement → emit [SubscriptionNavEvent.PurchaseSucceeded].
 *
 * The ViewModel never touches a `com.android.billingclient` type: every
 * Play value arrives already projected, through [SubscriptionRepository]
 * and [SubscriptionBillingGateway]'s `StateFlow` / plain-data results.
 * Talking to the seam rather than to `BillingClientApi` directly is also
 * what makes this class identical on both flavors — the `dev` variant's
 * simulated gateway satisfies the same contract.
 */
@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val billingGateway: SubscriptionBillingGateway,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SubscriptionState())
    val state: StateFlow<SubscriptionState> = _state.asStateFlow()

    /**
     * The session user's nickname for the "Welcome {nickname}" line, with
     * the iOS `"Klouder"` fallback applied.
     *
     * Kept as its own stream rather than a field on [SubscriptionState] so
     * that class stays byte-identical to the `subscription-plans` spec's
     * declared contract.
     */
    private val _nickname = MutableStateFlow(NICKNAME_FALLBACK)
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    private val _navEvents = Channel<SubscriptionNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<SubscriptionNavEvent> = _navEvents.receiveAsFlow()

    init {
        observeBillingConnection()
        observeSession()
        loadCatalog()
        collectPurchaseOutcomes()
    }

    /**
     * Surfaces a non-fatal notice for an unavailable billing connection —
     * chiefly the `dev` flavor's `com.kornerz.kornerz.dev` application id,
     * which is not a registered Play listing. The screen still renders its
     * full chrome with a disabled subscribe button; nothing crashes.
     */
    private fun observeBillingConnection() {
        viewModelScope.launch {
            billingGateway.connectionState.collect { connection ->
                val notice = if (connection is BillingConnectionState.Unavailable) {
                    when (connection.reason) {
                        BillingUnavailableReason.UnsupportedApplicationId ->
                            NOTICE_UNSUPPORTED_APPLICATION
                        BillingUnavailableReason.NoPlayStore -> NOTICE_NO_PLAY_STORE
                        else -> NOTICE_BILLING_UNAVAILABLE
                    }
                } else {
                    null
                }
                _state.value = _state.value.copy(billingNotice = notice)
            }
        }
    }

    /** Keeps the welcome line's nickname in step with the session. */
    private fun observeSession() {
        viewModelScope.launch {
            sessionRepository.data.collect { session ->
                _nickname.value = (session as? SessionState.Authenticated)
                    ?.user
                    ?.nickname
                    ?.takeIf { it.isNotBlank() }
                    ?: NICKNAME_FALLBACK
            }
        }
    }

    fun loadCatalog() {
        viewModelScope.launch {
            _state.value = _state.value.copy(products = AsyncValue.Loading, error = null)
            val products = subscriptionRepository.observeCatalog()
            _state.value = _state.value.copy(products = products)
            // A resolved catalog with no plans is an error state, not an
            // empty screen: the Play Console is misconfigured for this app
            // id and the user needs a retry affordance, not a blank page.
            if (products is AsyncValue.Success && products.data.isEmpty()) {
                _state.value = _state.value.copy(
                    products = AsyncValue.Error(IllegalStateException(EMPTY_CATALOG_MESSAGE)),
                )
            }
            syncTrialEligibility()
        }
    }

    /**
     * Single-select. Recomputes trial eligibility from the newly selected
     * product rather than carrying the previous value forward, so the
     * "Try It Free" / "Subscribe" label can never disagree with the card
     * that is stroked.
     */
    fun onPlanSelected(type: SubscriptionProductType) {
        _state.value = _state.value.copy(selected = type)
        syncTrialEligibility()
    }

    private fun syncTrialEligibility() {
        val products = _state.value.products as? AsyncValue.Success
        val selected = products?.data?.firstOrNull {
            it.planId == _state.value.selected.rawValue
        }
        _state.value = _state.value.copy(isTrialEligible = selected?.hasFreeTrialOffer == true)
    }

    /**
     * The subscribe tap: set `isPurchasing`, launch the Play sheet, then
     * await the outcome on [SubscriptionBillingGateway.purchaseOutcomes].
     *
     * Acknowledgement strictly precedes the entitlement write. A failed
     * acknowledgement is [SubscriptionError.NotAcknowledged], which
     * **blocks** the success navigation — Google refunds anything left
     * unacknowledged after three days, so navigating on would show a
     * confirmation the backend then reverses.
     */
    fun onSubscribeTap() {
        if (_state.value.isPurchasing) return
        val product = _state.value.selectedProduct ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(isPurchasing = true, error = null)
            val launch = billingGateway.launchPurchase(product.planId, product.offerTag)
            if (launch.isFailure) {
                val failure = launch.exceptionOrNull()
                finishPurchase(
                    (failure as? SubscriptionException)?.errorKind
                        ?: SubscriptionError.BillingUnavailable,
                )
                return@launch
            }
            // The outcome arrives on purchaseOutcomes, collected in init.
        }
    }

    /**
     * Drains the gateway's asynchronous purchase results for the client's
     * whole lifetime — not just while the plans screen is composed — so a
     * purchase finished outside the app is still observed on resume.
     */
    private fun collectPurchaseOutcomes() {
        viewModelScope.launch {
            billingGateway.purchaseOutcomes.collect { outcome ->
                if (!_state.value.isPurchasing) return@collect
                when (outcome) {
                    is BillingPurchaseResult.Purchased ->
                        completePurchase(outcome.token, outcome.productId, outcome.purchaseTimeMillis)
                    BillingPurchaseResult.Pending ->
                        finishPurchase(SubscriptionError.PurchasePending)
                    is BillingPurchaseResult.Failed ->
                        finishPurchase(outcome.error)
                }
            }
        }
    }

    /**
     * Acknowledges, grants, then navigates. Any failure short-circuits with
     * [SubscriptionNavEvent.PurchaseFailed] and leaves `isPurchasing`
     * cleared.
     */
    private suspend fun completePurchase(
        token: String,
        productId: String,
        purchaseTimeMillis: Long,
    ) {
        val plan = SubscriptionProductType.fromRawValue(productId)
        if (plan == null) {
            finishPurchase(SubscriptionError.Unknown("Unrecognized product id $productId"))
            return
        }

        val ack = subscriptionRepository.acknowledge(token)
        if (ack.isFailure) {
            finishPurchase(SubscriptionError.NotAcknowledged)
            return
        }

        val email = currentEmail()
        if (email == null) {
            finishPurchase(SubscriptionError.Unknown("Anonymous session; cannot grant entitlement"))
            return
        }

        val expiry = plan.deriveExpiryFrom(purchaseTimeMillis)
        val grant = subscriptionRepository.grantEntitlement(email, plan, expiry)
        if (grant.isFailure) {
            finishPurchase(SubscriptionError.Unknown(grant.exceptionOrNull()?.message ?: "Grant failed"))
            return
        }

        _state.value = _state.value.copy(isPurchasing = false, error = null)
        _navEvents.trySend(SubscriptionNavEvent.PurchaseSucceeded(plan))

        // The server's verdict is authoritative but must never block the
        // success screen, so it reconciles in the background; a failure
        // here leaves the locally-derived entitlement standing.
        viewModelScope.launch {
            subscriptionRepository.reconcileEntitlement()
        }
    }

    /**
     * The restore tap: re-derive the entitlement from Play. Restoring never
     * creates a purchase and never charges the user.
     */
    fun onRestoreTap() {
        if (_state.value.isRestoring) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isRestoring = true, error = null)
            val purchases = subscriptionRepository.restore()
            val records = purchases.getOrElse { throwable ->
                _state.value = _state.value.copy(isRestoring = false)
                failUnlessCancelled(SubscriptionError.Unknown(throwable.message ?: "Restore failed"))
                return@launch
            }

            val latest = records.maxByOrNull { it.purchaseTimeMillis }
            if (latest == null) {
                // No active subscription is a normal outcome, not an
                // error: "restore" on a free account should say so calmly.
                _state.value = _state.value.copy(isRestoring = false)
                _navEvents.trySend(
                    SubscriptionNavEvent.PurchaseFailed(NOTHING_TO_RESTORE),
                )
                return@launch
            }

            val plan = SubscriptionProductType.fromRawValue(latest.productId)
            val email = currentEmail()
            if (plan == null || email == null) {
                _state.value = _state.value.copy(isRestoring = false)
                _navEvents.trySend(SubscriptionNavEvent.PurchaseFailed(NOTHING_TO_RESTORE))
                return@launch
            }

            val grant = subscriptionRepository.grantEntitlement(
                email = email,
                plan = plan,
                expiry = plan.deriveExpiryFrom(latest.purchaseTimeMillis),
            )
            _state.value = _state.value.copy(isRestoring = false)
            if (grant.isSuccess) {
                _navEvents.trySend(SubscriptionNavEvent.Restored(plan))
            } else {
                _navEvents.trySend(
                    SubscriptionNavEvent.PurchaseFailed(RESTORE_FAILED),
                )
            }
        }
    }

    fun onPrivacyPolicyTap() {
        _navEvents.trySend(SubscriptionNavEvent.OpenPrivacyPolicy)
    }

    fun onTermsOfUseTap() {
        _navEvents.trySend(SubscriptionNavEvent.OpenTermsOfUse)
    }

    fun onDismissError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Clears the in-flight purchase flag when the screen leaves the
     * composition without a terminal outcome — for example when the user
     * backs out of the Play sheet by navigating away. The
     * `PurchasesUpdatedListener` stays registered for the client's
     * lifetime, so a purchase completed later is still delivered.
     */
    fun onScreenDisposed() {
        if (_state.value.isPurchasing) {
            _state.value = _state.value.copy(isPurchasing = false)
        }
    }

    /** Shared terminal path: clears `isPurchasing` and reports the error. */
    private fun finishPurchase(error: SubscriptionError) {
        _state.value = _state.value.copy(isPurchasing = false)
        failUnlessCancelled(error)
    }

    /**
     * `UserCancelled` is a normal return path — the user closed the Play
     * sheet — so it must leave `error` null and merely re-enable the
     * subscribe button. Every other case surfaces a message.
     */
    private fun failUnlessCancelled(error: SubscriptionError) {
        if (error == SubscriptionError.UserCancelled) return
        _state.value = _state.value.copy(error = error.toMessage())
        _navEvents.trySend(SubscriptionNavEvent.PurchaseFailed(error.toMessage()))
    }

    private suspend fun currentEmail(): String? =
        (sessionRepository.data.first() as? SessionState.Authenticated)
            ?.user
            ?.email
            ?.takeIf { it.isNotBlank() }

    private fun SubscriptionError.toMessage(): String = when (this) {
        SubscriptionError.BillingUnavailable -> MESSAGE_BILLING_UNAVAILABLE
        SubscriptionError.ProductUnavailable -> MESSAGE_PRODUCT_UNAVAILABLE
        SubscriptionError.UserCancelled -> ""
        SubscriptionError.NetworkError -> MESSAGE_NETWORK
        SubscriptionError.PurchasePending -> MESSAGE_PURCHASE_PENDING
        SubscriptionError.NotAcknowledged -> MESSAGE_NOT_ACKNOWLEDGED
        is SubscriptionError.Unknown -> message
    }

    private companion object {
        /** iOS `AppHelper.shared.user?.nickName ?? "Klouder"`
         *  (`kornerz/Kornerz/InAppPurchase/SubscriptionScreen.swift:37-41`). */
        const val NICKNAME_FALLBACK = "Klouder"

        const val NOTICE_BILLING_UNAVAILABLE = "Billing is unavailable in this build"
        const val NOTICE_NO_PLAY_STORE =
            "Google Play is not available on this device"
        const val NOTICE_UNSUPPORTED_APPLICATION =
            "Billing is unavailable in this build"

        const val EMPTY_CATALOG_MESSAGE = "No subscription plans available"
        const val NOTHING_TO_RESTORE = "No active subscription found"
        const val RESTORE_FAILED = "Could not restore your subscription"

        const val MESSAGE_BILLING_UNAVAILABLE = "Billing is unavailable right now"
        const val MESSAGE_PRODUCT_UNAVAILABLE = "Subscription plans are unavailable"
        const val MESSAGE_NETWORK = "Check your connection and try again"
        const val MESSAGE_PURCHASE_PENDING = "Your purchase is pending approval"
        const val MESSAGE_NOT_ACKNOWLEDGED =
            "Your purchase could not be confirmed. Please try again."
    }
}
