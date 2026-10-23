# subscription-plans Specification

## Purpose
TBD - created by archiving change phase-8-premium-iap. Update Purpose after archive.
## Requirements
### Requirement: Subscription plans screen reproduces iOS SubscriptionScreen structure
The subscription plans screen (inventory **11.1** host + **11.2** view, `SubscriptionScreen` + `SubscriptionContent` in `feature/subscription/plans/`) SHALL reproduce iOS `SubscriptionScreen.swift` (195 lines) structure-for-structure:

| Region | iOS reference | Android requirement |
|---|---|---|
| Background | `:22-24` `.background(AppColor.C68B6CC.swiftUIColor)` + `.foregroundStyle(.white)` | Full-bleed `#68B6CC` background; all content foreground is white |
| Top safe area | `:22` `.ignoresSafeArea(edges: .top)` | The background extends behind the status bar |
| Top image band | `:27-35` `Image("subscription_bg")`, `scaledToFill`, `frame(height: 320)`, `.clipped()` | A 320-pt-tall full-width image band, top-cropped. See the asset requirement below |
| Welcome message | `:37-41` `Text("welcom_user", value: userName)` | "Welcome {nickname}", 24-pt medium, nickname falling back to the literal `"Klouder"` (iOS `AppHelper.shared.user?.nickName ?? "Klouder"`) |
| Feature blurb | `:43-48` `Text("subscription_feature_msg")` | "Get full access to our community of Klouders, unlimited Nook video calls, and Loops to discuss topics of interest." at 18-pt medium |
| Free-trial line | `:49-57` `Text("start_free_trial")`, shown only when `viewModel.isTrialAvailable == true` | "Start with a 1 week free trial." at 16-pt medium, rendered **only** when the selected product's `hasFreeTrialOffer` is true |
| Plan cards | `:74-133` | Exactly two cards; see the plan-card requirement below |
| Selected-price caption | `:171-178` `Text("select_payment_plan_message", value: viewModel.selectedPrice)` | "1 week free, then {price}" at 16-pt medium, black text on the white content surface |
| Subscribe button | `:156-169` `KornerzButton.capsuleWhiteOnBlack("try_for_free" \| "subscribe", size: .large, width: .infinite)` | Full-width black-capsule button with white text; label is "Try It Free" when a trial offer is present on the selected product, otherwise "Subscribe" |
| Restore | `:180-189` `Button { Text("restore") }`, lato 16 medium white | Plain text button "Restore Purchase", 16-pt medium, white |
| Bottom links | `:135-154` `HStack` with `privacy_policy` (left) + `term_of_use` (right), lato 14 medium white | Two side-by-side text links at 14-pt medium: "Privacy Policy" and "Term of Use" |

The screen SHALL present no top app bar and no title, matching iOS (the host's `configureNavigation` / `customBackButton` are commented out at `SubscriptionController.swift:22-23`). The screen SHALL also present no close affordance: the paywall is mandatory, so it has no way out but subscribing — see the `premium-gate` capability's "The paywall is not dismissible" requirement.

The Privacy Policy tap SHALL open the existing P6 privacy-policy destination (`profile/privacy-policy`). The Term of Use tap SHALL open `https://www.apple.com/legal/internet-services/itunes/dev/stdeula/` (iOS `SubscriptionViewModel.swift:152-157`) in an external browser via an `Intent.ACTION_VIEW`.

#### Scenario: Screen renders with the iOS chrome
- **WHEN** the plans screen is composed with a resolved two-product catalog
- **THEN** it renders a `#68B6CC` full-bleed background, a 320-pt top image band, the welcome line, the blurb, exactly two plan cards, the selected-price caption, a full-width black-capsule subscribe button, a "Restore Purchase" text button, and the two bottom links — with no app bar

#### Scenario: Welcome message uses the nickname with a fallback
- **WHEN** the session user has a non-blank `nickname`
- **THEN** the welcome line reads "Welcome {nickname}"
- **AND WHEN** the session user has a blank `nickname`
- **THEN** the welcome line reads "Welcome Klouder"

#### Scenario: Free-trial line is conditional
- **WHEN** the selected product has a Play free-trial offer
- **THEN** "Start with a 1 week free trial." is visible
- **AND WHEN** the selected product has no free-trial offer
- **THEN** the line is not rendered

### Requirement: Exactly two selectable plan cards
The plans screen SHALL render exactly **two** plan cards — Monthly and Yearly — matching iOS `SubscriptionScreen.swift:74-133`. There SHALL NOT be a third option, a feature-tier selector, or a plan-count variable.

Each card SHALL be:
- A white background with black foreground (iOS `:96` `.background(.white)` + `.foregroundStyle(.black)`).
- A rounded rectangle with a 24-pt corner radius (iOS `RoundedRectangle(cornerRadius: 24)`).
- Stroke-adorned with a **2-pt black** outline when selected, and unstroked when not (iOS `:96-98`).

The **Monthly** card SHALL show, in a single row: the label "Monthly" at 24-pt medium on the left, and `priceLabel` at 24-pt bold on the right (iOS `:74-100`).

The **Yearly** card SHALL show, in a single row: the label "Yearly" at 24-pt medium on the left, and **two** stacked right-aligned lines — `priceLabel` at 24-pt bold, and `pricePerMonthEquivalent` at 20-pt bold in gray (iOS `:102-133`).

The screen SHALL render **no** "save X %" badge and **no** "best value" badge, and the "what's included" unlock list SHALL NOT be rendered — neither exists in the live iOS screen. (`subscription_features.json` is loaded into `SubscriptionViewModel.featureFile` at `:40-50` but never bound to any view, and `SubscriptionScreen.swift:60-72` does not iterate it.)

Card taps SHALL select that plan. Selection is single-select and SHALL update the selected-price caption and the subscribe button's trial label.

#### Scenario: Two cards only
- **WHEN** the plans screen is composed
- **THEN** exactly two plan cards are rendered, one labeled "Monthly" and one labeled "Yearly"

#### Scenario: Unselected card has no stroke
- **WHEN** the monthly plan is selected
- **THEN** the monthly card renders with a 2-pt black stroke and the yearly card renders with no stroke

#### Scenario: Yearly card shows the per-month equivalent line
- **WHEN** the yearly card is rendered
- **THEN** it shows the yearly price at 24-pt bold with a second gray line beneath it at 20-pt bold reading the per-month equivalent plus "/month"

#### Scenario: Monthly card has no per-month equivalent line
- **WHEN** the monthly card is rendered
- **THEN** it shows a single price line and no secondary gray line

#### Scenario: No discount badge is rendered
- **WHEN** the plans screen is composed
- **THEN** no "save", "best value", or discount badge appears on either card, and no feature/unlock list appears above the cards

#### Scenario: Tapping a card selects it
- **WHEN** the user taps the yearly card
- **THEN** the yearly card gains the 2-pt black stroke, the monthly card loses it, and the selected-price caption updates to the yearly price

### Requirement: Plan selection defaults to Monthly
The plans screen's selected plan SHALL default to `SubscriptionProductType.MONTHLY` whenever the screen is first composed for a session with no prior selection, mirroring the iOS `SubscriptionViewModel.selectedSubscription` initial value. The screen is not dismissible, so the only way to leave it and return within the same process is a completed purchase; a fresh screen composition after process start SHALL reset to `MONTHLY`.

#### Scenario: Default selection
- **WHEN** the plans screen is composed for the first time in a process
- **THEN** the monthly card is selected and the caption reads "1 week free, then {monthly price}" or "{monthly price}" per trial availability

### Requirement: Subscription success screen reproduces iOS SubscriptionSuccessScreen
The subscription success screen (inventory **11.3**, `SubscriptionSuccessScreen` + `SubscriptionSuccessContent` in `feature/subscription/success/`) SHALL reproduce iOS `SubscriptionSuccessScreen.swift` (43 lines) exactly:

- A `#68B6CC` background with white foreground, matching the plans screen.
- A 120×120 checkmark-seal glyph centered (iOS `:17-20` `Image(systemName: "checkmark.seal")`; Android uses the Material equivalent `Icons.Filled.Verified` at 120 dp).
- The text "Subscription activated successfully" at 26-pt bold (iOS `:21`).
- A full-width white-capsule button with black text reading "Done" (iOS `:23` `KornerzButton.capsuleBlackOnWhite("done", size: .large, width: .infinity)`).
- No back affordance and no app bar (iOS `:37` `.navigationBarBackButtonHidden(true)`).

Tapping "Done" SHALL emit `SubscriptionSuccessNavEvent.Dismissed`, which marks the paywall gate satisfied for the process and unwinds **past** the plans route, returning the user to the pre-gate destination rather than to the paywall. See the `premium-gate` capability's "Gate navigation and success unwind" requirement for the inclusive pop.

This screen SHALL perform **no** Firestore write of its own; the entitlement write happens upstream in the purchase flow before navigation, mirroring iOS, where `SubscriptionSuccessScreen` never touches the backend and `SubscriptionController.updateSubscription(...)` writes upstream (`:76-79`).

#### Scenario: Success screen renders
- **WHEN** the success screen is composed
- **THEN** it renders the `#68B6CC` background, a 120-dp checkmark-seal glyph, "Subscription activated successfully" at 26-pt bold, and a full-width white-capsule "Done" button

#### Scenario: Done dismisses the flow
- **WHEN** the user taps "Done"
- **THEN** `SubscriptionSuccessNavEvent.Dismissed` is emitted, both subscription routes are popped, the premium gate is marked satisfied, and the user lands on the pre-gate destination

### Requirement: Subscription screen render states
The plans screen SHALL render exactly four states, dispatched on `SubscriptionState.products`:

| State | Condition | Rendered |
|---|---|---|
| Loading | `AsyncValue.Loading` | `KornerzLoadingView` from `core/ui` |
| Error + retry | `AsyncValue.Error`, or an empty catalog | `KornerzErrorState` from `core/ui` with a working `onRetry` that re-invokes `queryProductDetails` |
| Billing unavailable | `BillingConnectionState.Unavailable` | A non-fatal inline notice ("Billing is unavailable in this build") above the plan area; the rest of the screen renders normally and the app does not crash |
| Loaded | `AsyncValue.Success` with a non-empty catalog | The full iOS-structured screen |

The `Unavailable` state SHALL NOT be an error state: it SHALL render alongside the screen chrome, not replace it, so a `dev`-flavor user still sees the pricing, the terms links, and a disabled subscribe button rather than a blank error page.

#### Scenario: Catalog loading
- **WHEN** `SubscriptionState.products` is `AsyncValue.Loading`
- **THEN** `KornerzLoadingView` is rendered and no plan card is shown

#### Scenario: Catalog query fails
- **WHEN** `SubscriptionState.products` is `AsyncValue.Error`
- **THEN** `KornerzErrorState` renders with a retry action that re-issues the product query

#### Scenario: Dev flavor shows a non-fatal notice
- **WHEN** `BillingConnectionState` is `Unavailable(UnsupportedApplicationId)` and the plans screen is reached from the Profile menu
- **THEN** the screen renders its full chrome with a billing-unavailable notice and a disabled subscribe button, and the rest of the app is unaffected

### Requirement: SubscriptionViewModel UDF contract
`SubscriptionViewModel` (`@HiltViewModel`) SHALL expose:

```kotlin
data class SubscriptionState(
    val products: AsyncValue<List<SubscriptionProduct>> = AsyncValue.Loading,
    val selected: SubscriptionProductType = SubscriptionProductType.MONTHLY,
    val isTrialEligible: Boolean = false,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val billingNotice: String? = null,
    val error: String? = null,
)

sealed interface SubscriptionNavEvent {
    data class PurchaseSucceeded(val plan: SubscriptionProductType) : SubscriptionNavEvent
    data class PurchaseFailed(val message: String) : SubscriptionNavEvent
    data class Restored(val plan: SubscriptionProductType) : SubscriptionNavEvent
    data object OpenPrivacyPolicy : SubscriptionNavEvent
    data object OpenTermsOfUse : SubscriptionNavEvent
}
```

`navEvents` SHALL be a `Channel<SubscriptionNavEvent>` exposed as a `receiveAsFlow()`, matching `ProfileViewModel`'s existing contract (`ProfileContract.kt`).

On init the VM SHALL: observe `BillingClientApi.connectionState` (surfacing `billingNotice` for `Unavailable`), then call `queryProductDetails` for both product ids and populate `products`. `isTrialEligible` SHALL be derived from the currently selected product's `hasFreeTrialOffer`, recomputed on every selection change.

On a subscribe tap the VM SHALL set `isPurchasing = true`, launch the purchase, and clear `isPurchasing` on any terminal outcome. On `Restore Purchase` it SHALL set `isRestoring = true`, call `restore()`, and clear it on completion.

`SubscriptionError.UserCancelled` SHALL leave `error` null.

#### Scenario: Init resolves the catalog
- **WHEN** `SubscriptionViewModel` is created
- **THEN** `products` transitions from `Loading` to `Success` with both plans, or to `Error` if the query fails

#### Scenario: Trial eligibility follows selection
- **WHEN** the user selects the yearly plan and only the monthly plan has a Play free-trial offer
- **THEN** `isTrialEligible` is `false` and the subscribe button label reads "Subscribe", not "Try It Free"

#### Scenario: Purchase in flight
- **WHEN** the user taps the subscribe button
- **THEN** `isPurchasing` becomes `true` and the subscribe button is disabled until a terminal `BillingPurchaseResult` arrives

#### Scenario: Cancel leaves no error
- **WHEN** `launchBillingFlow` returns `USER_CANCELED`
- **THEN** `isPurchasing` returns to `false` and `error` remains `null`

### Requirement: Two shared UI components for the subscription chrome
`core/ui` SHALL gain `KornerzCapsuleButton` and `feature/subscription` SHALL gain `SubscriptionPlanCard`.

`KornerzCapsuleButton` SHALL live in `core/ui/components/` because it is a brand-level primitive with two color variants, mirroring iOS `KornerzButton.capsuleWhiteOnBlack` (`KornerzButton.swift:60-84`, `:263-277`) and `.capsuleBlackOnWhite` (`:279-294`). It SHALL accept an explicit style parameter for black-background/white-text versus white-background/black-text, a `text`, an `onClick`, an `enabled` flag defaulting to `true`, and a `fillMaxWidth` behavior matching iOS's `width: .infinite`. It SHALL take its colors from `KornerzColors` and its typography from `KornerzTypography` rather than inline hex values.

`KornerzButton(Filled | Outlined | Text)` SHALL NOT be modified — its 52-dp height, 16-dp shape, and theme-primary fill are used by every P0–P7 screen.

`SubscriptionPlanCard` SHALL live in `feature/subscription/plans/components/` (not `core/ui`) because it embeds subscription-specific vocabulary — the per-month equivalent line and the trial offer tag — that no other screen has. It SHALL take `product: SubscriptionProduct`, `isSelected: Boolean`, and `onClick: () -> Unit`.

#### Scenario: Capsule button renders both variants
- **WHEN** `KornerzCapsuleButton` is composed with the white-on-black style
- **THEN** it renders a fully-rounded black capsule with white text at full width

#### Scenario: Existing button is untouched
- **WHEN** `KornerzButton` is composed by an existing P0–P7 screen
- **THEN** its height, shape, and colors are unchanged by P8

#### Scenario: Plan card reflects selection
- **WHEN** `SubscriptionPlanCard` is composed with `isSelected = true`
- **THEN** it renders a white 24-dp-corner-radius card with a 2-pt black stroke; with `isSelected = false`, the stroke is absent

### Requirement: Top image band asset substitution
The plans screen's 320-pt top image band SHALL be rendered from a shipped Android asset or drawable. iOS references `Image("subscription_bg")` at `SubscriptionScreen.swift:29`, but **`subscription_bg` does not exist in the iOS asset catalog** — the iOS screen renders a blank band at runtime today.

The Android port SHALL ship a working asset (a vector drawable or a solid/gradient band) so the band renders visibly, and SHALL NOT attempt to reproduce the missing iOS bitmap. This substitution is deliberate and documented, not an accidental omission.

#### Scenario: Band renders visibly
- **WHEN** the plans screen is composed
- **THEN** the 320-pt band renders a visible asset rather than an empty region
