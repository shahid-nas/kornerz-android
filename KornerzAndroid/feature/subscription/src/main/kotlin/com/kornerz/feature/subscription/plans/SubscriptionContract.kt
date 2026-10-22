package com.kornerz.feature.subscription.plans

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.SubscriptionProduct
import com.kornerz.core.model.SubscriptionProductType

/**
 * UDF contract for the plans screen (inventory 11.1 host + 11.2 view).
 * Mirrors the shape every P0–P7 screen uses: one immutable [SubscriptionState],
 * one `Channel`-backed nav-event stream, and a ViewModel that owns all of it.
 *
 * @param products the resolved Play catalog, or the loading/error state the
 *   screen dispatches its four render states on.
 * @param selected the single-selected plan. Defaults to
 *   [SubscriptionProductType.MONTHLY], mirroring iOS
 *   `SubscriptionViewModel.selectedSubscription`'s initial value.
 * @param isTrialEligible re-derived from the **selected** product's
 *   `hasFreeTrialOffer` on every selection change, so it can never disagree
 *   with the button label.
 * @param billingNotice a non-fatal message for
 *   `BillingConnectionState.Unavailable` — the `dev` flavor's unregistered
 *   application id lands here. It renders *alongside* the screen chrome, not
 *   in place of it.
 * @param error a terminal failure message, or `null`.
 *   [com.kornerz.feature.subscription.data.SubscriptionError.UserCancelled]
 *   deliberately leaves this `null`: cancelling the Play sheet is a normal
 *   return path, not something to apologise for.
 */
data class SubscriptionState(
    val products: AsyncValue<List<SubscriptionProduct>> = AsyncValue.Loading,
    val selected: SubscriptionProductType = SubscriptionProductType.MONTHLY,
    val isTrialEligible: Boolean = false,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val billingNotice: String? = null,
    val error: String? = null,
) {
    /** The selected product once the catalog has resolved, else `null`. */
    val selectedProduct: SubscriptionProduct?
        get() = (products as? AsyncValue.Success)
            ?.data
            ?.firstOrNull { it.planId == selected.rawValue }

    /** The price shown in the "1 week free, then {price}" caption. */
    val selectedPriceLabel: String?
        get() = selectedProduct?.priceLabel
}

/**
 * One-shot navigation effects. Emitted on a `Channel` and drained by the
 * shell, so a tap can never re-fire the destination on configuration
 * change.
 */
sealed interface SubscriptionNavEvent {

    /**
     * The purchase completed, was acknowledged, and the entitlement was
     * written. The shell navigates forward to `SubscriptionRoute.Success`.
     * A failed acknowledgement produces [PurchaseFailed] instead and never
     * reaches here.
     */
    data class PurchaseSucceeded(val plan: SubscriptionProductType) : SubscriptionNavEvent

    data class PurchaseFailed(val message: String) : SubscriptionNavEvent

    /** Restore recovered an active purchase. */
    data class Restored(val plan: SubscriptionProductType) : SubscriptionNavEvent

    /** Reuses the existing P6 `profile/privacy-policy` destination. */
    data object OpenPrivacyPolicy : SubscriptionNavEvent

    /** Opens the Apple EULA externally via `Intent.ACTION_VIEW`. */
    data object OpenTermsOfUse : SubscriptionNavEvent
}