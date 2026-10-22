# android-app-shell delta Specification

## Purpose
TBD - created by archiving change phase-8-premium-iap. Update Purpose after archive.

## MODIFIED Requirements

### Requirement: Five-tab bottom navigation shell

The app SHALL render a bottom navigation bar with exactly 5 tabs in
this left-to-right order: Home, Kornerz, Nooks, Loops, Klouders. Each
tab SHALL be reachable by tapping its icon. The currently selected tab
SHALL be visually distinguished. The bottom bar SHALL remain visible
while navigating within a tab's nested graph. The Nooks tab SHALL host
a nested `NavHost` with the `nooks/list` destination (introduced in
P4, `nooks-tab` capability) as its start destination, `nooks/{nookId}`
(introduced in P4, `nook-detail` capability) as a secondary
destination reachable from any `NookCard` or `NookTile` tap, and
`videocall/{sessionId}` (introduced in P9, `feature-videocall`
capability) as a tertiary destination reachable from the Join button
on a `LiveKornerCard` (introduced in P9, `nooks-tab` capability
amendment). The Loops tab SHALL host a nested `NavHost` with the
`loops/list` destination (introduced in P5, `loops-tab` capability)
as its start destination, `loops/{loopId}` (introduced in P5,
`loop-detail` capability) as a secondary destination reachable from
any `LoopItemView` tap, `loops/{loopId}/comment` (introduced in P5,
`loop-comments` capability) as a `dialog` route reachable from the
loop detail's add-comment row, and `loops/{loopId}/reply/{commentId}`
(introduced in P5, `loop-comments` capability) as a tertiary
destination reachable from any `LoopCommentView` `onReply` affordance.
The Profile tab SHALL host a nested `NavHost` with `profile/view`
(introduced in P6, `user-profile` capability) as its start
destination, and `profile/edit-basic-info`,
`profile/edit-basic-info/{field}` (dialog),
`profile/edit-basic-info/{field}/success`, `profile/privacy-policy`
as secondary destinations reachable from the corresponding menu rows
(introduced in P6, `user-profile` + `profile-edit-basic-info` +
`privacy-policy-pdf` capabilities). The shell SHALL register three
additional top-level, full-screen (bottom-bar-hidden) routes alongside
the shell graph: `community/admin/list` (introduced in P7),
`subscription/plans`, and `subscription/success` (both introduced in
P8, `subscription-plans` capability).

The previous P7 draft also registered a `community/create` route
pushed from the Profile tab. **That route is removed** after the iOS
audit — iOS `UserProfileVC` does not surface a "Create Community"
menu row, and the iOS `CreateCommunityHomeVC` is unreachable dead
code. Android now mirrors iOS exactly and does not register a
`community/create` route.

**P8 addition (bottom-bar-hidden set):** the routes that hide the
bottom bar SHALL include `subscription/plans` and
`subscription/success`. In the shipped `AppShell` the set is a single
`Regex` (`:111-113`), so P8 extends that pattern from
`videocall/.*|notifications/list|profile/.*|community/admin/.*` to
also match `subscription/.*`. Both subscription routes SHALL also
render behind the system status bar (`#68B6CC` extends to the top
edge, matching iOS `SubscriptionScreen.swift:22`
`.ignoresSafeArea(edges: .top)`).

#### Scenario: Five-tab shell renders
- **WHEN** the shell composable is first composed for an authenticated session
- **THEN** the bottom bar renders with exactly 5 tabs in the order Home, Kornerz, Nooks, Loops, Profile

#### Scenario: Nooks tab nested navigation
- **WHEN** user taps the Nooks tab and then taps a `NookCard`
- **THEN** the bottom bar remains visible and the `nooks/{nookId}` destination renders inside the tab's nested NavHost

#### Scenario: Loops tab nested navigation
- **WHEN** user taps the Loops tab and then taps a `LoopItemView`
- **THEN** the bottom bar remains visible and the `loops/{loopId}` destination renders inside the tab's nested NavHost

#### Scenario: Loops dialog route preserves bottom bar
- **WHEN** user opens `loops/{loopId}/comment` and `loops/{loopId}/reply/{commentId}` dialog routes
- **THEN** the bottom bar stays visible behind the dialog scrim

#### Scenario: Profile tab nested navigation
- **WHEN** user taps the Profile tab and then taps Edit Basic Info / Privacy Policy / Change Basic Info (Field)
- **THEN** the bottom bar stays visible and the matching nested profile destination renders inside the Profile tab's nested NavHost

#### Scenario: Admin list route hides bottom bar (full-screen)
- **WHEN** user navigates to `community/admin/list` from Profile
- **THEN** the bottom bar fades out and the admin list renders full-screen (mirrors iOS push from `UserProfileVC.openMyCommunities`)

#### Scenario: Back from admin list returns to Profile
- **WHEN** user taps the back button in the top app bar of `community/admin/list`
- **THEN** shell pops back to the Profile tab

#### Scenario: Account deletion routes back to auth graph
- **WHEN** the shared `userDeletedHisAccount` event bus emits (introduced in P6, `account-deletion` capability)
- **THEN** the root NavHost clears the shell graph and switches to the auth graph, matching the existing logout behavior

#### Scenario: Subscription routes hide the bottom bar
- **WHEN** the premium gate opens and the shell navigates to `subscription/plans`
- **THEN** the bottom bar is hidden and the plans screen renders full-screen behind the status bar

#### Scenario: Subscription success route hides the bottom bar
- **WHEN** a purchase completes and the shell navigates to `subscription/success`
- **THEN** the bottom bar is hidden and the success screen renders full-screen

#### Scenario: Subscription routes are top-level, not tab-nested
- **WHEN** `AppShell` registers `subscription/plans` and `subscription/success`
- **THEN** they are registered on the root `NavHost` outside every tab's nested `NavHost`, so no bottom tab becomes the active tab

## ADDED Requirements

### Requirement: SubscriptionRoute sealed class models the P8 subscription screens
The shell's navigation contract SHALL expose a `SubscriptionRoute`
sealed class with two cases:

| Case | Route string | Screen |
|---|---|---|
| `Plans` | `subscription/plans` | Subscription plans host + view (inventory 11.1 + 11.2) |
| `Success` | `subscription/success` | Subscription success (inventory 11.3) |

The `AppShell` composable SHALL register both as top-level routes
outside any tab's nested `NavHost`, and SHALL include both in the set
of routes that hide the bottom navigation bar.

`ShellViewModel` SHALL own the `PremiumGate` collection and the
subscription navigation wiring:

- When `PremiumGate.shouldPresent` transitions to `true` at either of
  its two call sites (post-login, shell resume), the shell SHALL
  navigate to `SubscriptionRoute.Plans`.
- On `SubscriptionNavEvent.PurchaseSucceeded`, the shell SHALL navigate
  forward to `SubscriptionRoute.Success`.
- On `SubscriptionSuccessNavEvent.Dismissed`, the shell SHALL pop back
  to the pre-gate destination, call `PremiumGate.markSatisfied()`, and
  return the user to where they were.
- On `SubscriptionNavEvent.Dismissed`, the shell SHALL pop back to the
  pre-gate destination and call `PremiumGate.markDismissed()`.
- `SubscriptionNavEvent.OpenPrivacyPolicy` SHALL route to the existing
  P6 `profile/privacy-policy` destination.
- `SubscriptionNavEvent.OpenTermsOfUse` SHALL open
  `https://www.apple.com/legal/internet-services/itunes/dev/stdeula/`
  externally via `Intent.ACTION_VIEW` (iOS
  `SubscriptionViewModel.swift:152-157`).

The pre-gate destination SHALL be captured when the gate opens so the
unwind is deterministic rather than relying on the back stack depth.

#### Scenario: SubscriptionRoute cases are registered
- **WHEN** the shell composable is composed
- **THEN** `subscription/plans` and `subscription/success` are registered as top-level `composable` destinations and both hide the bottom bar

#### Scenario: Gate navigation targets the plans route
- **WHEN** `PremiumGate.requestPresent()` returns `true`
- **THEN** the shell navigates to `SubscriptionRoute.Plans`

#### Scenario: Purchase advances to the success route
- **WHEN** `SubscriptionNavEvent.PurchaseSucceeded` is received while on `subscription/plans`
- **THEN** the shell navigates forward to `SubscriptionRoute.Success`

#### Scenario: Done unwinds to the pre-gate destination
- **WHEN** the user taps "Done" on `subscription/success`
- **THEN** the shell pops to the destination that was current before the gate opened and calls `PremiumGate.markSatisfied()`

#### Scenario: Privacy policy reuses the P6 destination
- **WHEN** `SubscriptionNavEvent.OpenPrivacyPolicy` is received
- **THEN** the shell navigates to the existing `profile/privacy-policy` destination rather than a subscription-local copy

#### Scenario: Terms of use opens externally
- **WHEN** `SubscriptionNavEvent.OpenTermsOfUse` is received
- **THEN** an `Intent.ACTION_VIEW` for the Apple EULA URL is dispatched and the shell's back stack is unchanged

#### Scenario: The gate never fires while signed out
- **WHEN** the session emits `SessionState.Anonymous`
- **THEN** no subscription navigation occurs

## References

- `feature/shell/src/main/kotlin/com/kornerz/feature/shell/AppShell.kt:111-113` — the bottom-bar-hidden `Regex` P8 extends
- `feature/shell/src/main/kotlin/com/kornerz/feature/shell/AppShell.kt:561-612` — `profileGraph`, where the Premium row is threaded
- `kornerz/Kornerz/SwiftUI/TabView/AppTabController.swift:217-220` — iOS presents the plans route modally with `.fullScreen`, which the Android top-level full-screen route mirrors