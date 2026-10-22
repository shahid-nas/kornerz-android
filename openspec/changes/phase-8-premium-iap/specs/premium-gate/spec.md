# premium-gate Specification

## Purpose
TBD - created by archiving change phase-8-premium-iap. Update Purpose after archive.

## ADDED Requirements

### Requirement: PremiumGate mirrors the iOS four-clause guard
`PremiumGate` (`@Singleton`, `feature/shell/`) SHALL decide whether to present the paywall by evaluating **five** clauses, all of which must pass:

| # | Clause | iOS counterpart |
|---|---|---|
| 1 | The plans route is not already the current destination | `AppTabController.showSubscriptionScreen()` `:212` `guard isSubscriptionOpened() == false` |
| 2 | The session is authenticated | `:213` `guard AppHelper.shared.isUserLoggedIn` |
| 3 | No active Play entitlement exists for this user | `:214` `guard InAppManager.shared.isSubscriptionActive == false` |
| 4 | `User.isUserPremiumNow` is `false` | `:215` `guard AppHelper.shared.isUserPremium == false` |
| 5 | Not dismissed in this process | Android-only latch (`markDismissed()`; no production call site arms it — see "The paywall is not dismissible") |

Clauses 1–4 reproduce `AppTabController.showSubscriptionScreen()` (`kornerz/Kornerz/SwiftUI/TabView/AppTabController.swift:209-229`) and `LoginVC.showSubscriptionScreen()` (`kornerz/Kornerz/User/LoginVC/LoginVC.swift:219-232`), both of which carry an identical four-clause guard.

Clause 4 SHALL use `User.isUserPremiumNow` (the expiry-derived projection) and SHALL NOT test the raw `isPremium` field, per the `premium-entitlement` capability.

`PremiumGate` SHALL expose:

| Member | Signature |
|---|---|
| `shouldPresent` | `StateFlow<Boolean>` |
| `requestPresent()` | `suspend fun requestPresent(): Boolean` |
| `markDismissed()` | `fun markDismissed()` |
| `markSatisfied()` | `fun markSatisfied()` |
| `reset()` | `fun reset()` |

`requestPresent()` SHALL recompute all five clauses against current state and return `true` only when every clause passes.

#### Scenario: Free user passes every clause
- **WHEN** an authenticated user with no Play entitlement and a past-or-absent `subscriptionExpiryDate` has not dismissed the paywall this process and is not already on `subscription/plans`
- **THEN** `requestPresent()` returns `true`

#### Scenario: Subscriber fails clause 4
- **WHEN** the session user's `subscriptionExpiryDate` is in the future
- **THEN** `requestPresent()` returns `false`

#### Scenario: Active entitlement fails clause 3
- **WHEN** `queryPurchases` reports an active, non-consumed purchase for the user
- **THEN** `requestPresent()` returns `false`

#### Scenario: Anonymous fails clause 2
- **WHEN** the session is `SessionState.Anonymous`
- **THEN** `requestPresent()` returns `false`

#### Scenario: Already on the plans route fails clause 1
- **WHEN** the current `NavBackStackEntry` destination is `subscription/plans`
- **THEN** `requestPresent()` returns `false`

### Requirement: The gate fires from exactly two places
The gate SHALL be evaluated from exactly two call sites, each with a direct iOS counterpart. It SHALL NOT be evaluated on `NavHost` destination changes, tab switches, or deep-link arrivals.

| Call site | iOS counterpart |
|---|---|
| Immediately after a successful login in `feature/auth` | `LoginVC.showSubscriptionScreen()` `:185`, `:219-232` |
| On shell resume / entitlement re-emit in `ShellViewModel` | `AppTabController`'s `CombineLatest(purchasedProducts, userPublisher)` sink `:170-189` |

When `requestPresent()` returns `true` at either call site, the caller SHALL navigate to `SubscriptionRoute.Plans`.

The gate SHALL NOT fire from a deep link or a push notification. iOS has no such path — `DeeplinkManager.DeepLink` (`kornerz/Kornerz/DeepLinks/DeeplinkManager.swift:13-54`) enumerates `newEvent`, `newArticle`, `videoCall`, `newForum`, `newComment`, `newLoop`, `newLoopComment` and no subscription case.

#### Scenario: Post-login gate fires
- **WHEN** an authenticated free user completes login
- **THEN** `requestPresent()` is evaluated once and the shell navigates to `subscription/plans` if every clause passes

#### Scenario: Shell resume gate fires
- **WHEN** the app returns to the foreground and the entitlement re-emits
- **THEN** `requestPresent()` is evaluated once and the shell navigates to `subscription/plans` if every clause passes

#### Scenario: No destination-change gate
- **WHEN** the user switches tabs repeatedly while free
- **THEN** the plans screen is not re-presented on each switch; the gate evaluates only at its two call sites

#### Scenario: No deep-link gate
- **WHEN** a push notification or deep link arrives
- **THEN** the subscription screen is not opened

### Requirement: The paywall is not dismissible
The app SHALL NOT be usable without an active subscription, so the plans screen SHALL expose no close affordance and SHALL swallow the system back gesture. The plans route therefore cannot be popped by the user: its only exit is a completed purchase, unwound by the success screen.

This is also the iOS behaviour: `SubscriptionController`'s `configureNavigation` and `customBackButton` are commented out at `:22-23`, so `customBackButton()` (`:55-66`) is never called and iOS ships no close affordance at all. Android previously added one; §19 of `tasks.md` records the reversal.

`PremiumGate.markDismissed()` SHALL remain part of the gate's surface so clause 5 stays enforceable, but no production call site arms it.

`markSatisfied()` SHALL be called when a purchase completes or an active entitlement is detected, and SHALL also clear the dismissal latch, so a user who purchased mid-session is never re-gated.

`reset()` SHALL clear both the dismissal and satisfied latches and SHALL be invoked on logout and on account deletion, so a subsequent fresh login re-evaluates the gate from scratch — matching iOS, where the post-login gate in `LoginVC` fires unconditionally for a free user.

#### Scenario: No close affordance exists
- **WHEN** the plans screen is composed with a resolved catalog
- **THEN** no close affordance is rendered and `SubscriptionContent` takes no close callback

#### Scenario: Back gesture is swallowed
- **WHEN** the user performs the system back gesture on the plans screen
- **THEN** `subscription/plans` remains the current destination and the user stays on the paywall

#### Scenario: Purchase satisfies the gate
- **WHEN** a purchase completes and `markSatisfied()` is called
- **THEN** the gate does not fire again in this process regardless of the dismissal latch

#### Scenario: Logout resets the gate
- **WHEN** the user logs out and then logs back in
- **THEN** both latches are cleared and the post-login gate evaluates from scratch

#### Scenario: Account deletion resets the gate
- **WHEN** the `userDeletedHisAccount` event bus emits (P6, `account-deletion` capability)
- **THEN** the gate latches are reset before the shell switches to the auth graph

### Requirement: Gate navigation and success unwind
When the gate opens, the shell SHALL navigate to `SubscriptionRoute.Plans`. On `SubscriptionNavEvent.PurchaseSucceeded`, the shell SHALL navigate forward to `SubscriptionRoute.Success`. On `SubscriptionSuccessNavEvent.Dismissed`, the shell SHALL call `PremiumGate.markSatisfied()` and pop **both** subscription routes — `subscription/plans` popped inclusively — returning the user to the destination that was current before the gate opened.

The inclusive pop is load-bearing: a plain single-entry `popBackStack()` from the success screen lands the user back on `subscription/plans`, i.e. on the paywall they just paid to leave. There is no dismissal path off the plans screen, so leaving it on the back stack would trap the user there.

#### Scenario: Purchase path navigation
- **WHEN** the gate opens, the user completes a purchase, and then taps "Done" on the success screen
- **THEN** the shell pops `subscription/plans` inclusively, lands on the pre-gate destination with the bottom bar visible, and the gate is marked satisfied

#### Scenario: Success screen is never left on the stack
- **WHEN** the success screen is dismissed
- **THEN** neither `subscription/success` nor `subscription/plans` remains on the back stack

#### Scenario: Dismissed never fires from the plans screen
- **WHEN** the plans screen is composed
- **THEN** `SubscriptionNavEvent` declares no `Dismissed` member and the ViewModel exposes no close handler

## References

- `kornerz/Kornerz/SwiftUI/TabView/AppTabController.swift:170-189` — iOS source-of-truth for the entitlement re-emit sink that triggers the gate
- `kornerz/Kornerz/SwiftUI/TabView/AppTabController.swift:209-229` — iOS source-of-truth for the four-clause guard and `isSubscriptionOpened()`
- `kornerz/Kornerz/User/LoginVC/LoginVC.swift:185`, `:219-232` — iOS source-of-truth for the post-login gate
- `kornerz/Kornerz/Helper/AppHelper.swift:39-45` — iOS source-of-truth for `isUserPremium` derivation
- `kornerz/Kornerz/DeepLinks/DeeplinkManager.swift:13-54` — the negative finding that no deep-link path reaches the paywall
- `kornerz/Kornerz/InAppPurchase/InAppManager.swift:42-44` — iOS source-of-truth for the device-local `isSubscriptionActive`
- `docs/phases.md:250` — "Premium gating works (the iOS `AppTabController.showSubscriptionScreen()` gate fires for free users post-trial)"