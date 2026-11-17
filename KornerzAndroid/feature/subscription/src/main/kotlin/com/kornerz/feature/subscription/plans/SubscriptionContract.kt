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
 * @param isTrialEligible whether **this user** may start the selected
 *   plan's free trial. Re-derived from the **selected** product's
 *   [SubscriptionProduct.isTrialEligible] on every selection change, so
 *   it can never disagree with the button label.
 *
 *   The value is the store's own per-user verdict, not a local timer and
 *   not a hard-coded constant: Play only returns the introductory-offer
 *   phase to a user who may redeem it, and StoreKit only populates
 *   `Product.introductoryOffer` for an eligible user. See
 *   [com.kornerz.core.model.SubscriptionConstants.TRIAL_PERIOD_DAYS] for
 *   why the 7-day figure is deliberately inert.
 * @param billingNotice a non-fatal message for
 *   `BillingConnectionState.Unavailable` — the `dev` flavor's unregistered
 *   application id lands here. It renders *alongside* the screen chrome, not
 *   in place of it.
 * @param error a terminal failure message, or `null`.
 *   [com.kornerz.feature.subscription.data.SubscriptionError.UserCancelled]
 *   deliberately leaves this `null`: cancelling the Play sheet is a normal
 *   return path, not something to apologise for.
 * @param showWrongAccountSheet whether the "subscription belongs to a
 *   different account" sheet is open.
 *
 *   This failure is a **dead end**: the purchase lives on the device's
 *   Play account and is attributed to a different Kornerz account, so
 *   neither subscribing nor restoring can succeed from this session. The
 *   only two ways out are to sign in to the account that made the
 *   purchase, or to sign out and sign back in as that account — and the
 *   app cannot tell the user *which* Google account to switch to, because
 *   Play Billing never exposes the Play account's identity.
 *
 *   The message therefore ships in a modal sheet rather than as an inline
 *   line of text: an inline message leaves the user on a paywall they
 *   cannot pass, with a Subscribe button that fails every time it is
 *   tapped. The sheet makes the one actionable step — logging out — the
 *   primary button.
 */
data class SubscriptionState(
    val products: AsyncValue<List<SubscriptionProduct>> = AsyncValue.Loading,
    val selected: SubscriptionProductType = SubscriptionProductType.MONTHLY,
    val isTrialEligible: Boolean = false,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val billingNotice: String? = null,
    val error: String? = null,
    val showWrongAccountSheet: Boolean = false,
) {
    /** The selected product once the catalog has resolved, else `null`. */
    val selectedProduct: SubscriptionProduct?
        get() = (products as? AsyncValue.Success)
            ?.data
            ?.firstOrNull { it.playProductId == selected.playProductId }

    /**
     * The price substituted into the "1 week free, then {price}" caption.
     *
     * Formatted as iOS formats it — `"<price>/<periodUnit>"`, e.g.
     * `"$10.99/month"` — because iOS passes `SubscriptionViewModel.selectedPrice`
     * into `select_payment_plan_message` and that value already carries
     * the period unit (`SubscriptionViewModel.swift:93-110`).
     *
     * The "1 week free, then " prefix is **not** part of this value: on
     * iOS it lives inside the localized `select_payment_plan_message`
     * string and is applied unconditionally
     * (`SubscriptionScreen.swift:172-178`), independent of trial
     * availability.
     */
    val selectedPriceLabel: String?
        get() = selectedProduct?.displayPrice
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

    /**
     * The user asked to sign out from the wrong-account sheet.
     *
     * `feature/subscription` may not depend on `feature/shell`
     * (`docs/architecture.md` §2.5 — only `app` imports the feature
     * modules), so the screen cannot tear the session down itself. The
     * event crosses the boundary and the shell performs the logout.
     */
    data object Logout : SubscriptionNavEvent
}