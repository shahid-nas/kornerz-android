# android-app-shell Specification

## Purpose
TBD - created by archiving change p0-android-foundation. Update Purpose after archive.
## Requirements
### Requirement: App launches into splash screen
The app SHALL display a branded splash screen as the first screen when launched from a cold start. The splash SHALL render the Kornerz brand mark on a brand background color. After `SessionRepository.data()` emits a non-`Loading` value AND `FirebaseRemoteConfig.fetchAndActivate()` completes, the splash SHALL route to the Login screen if the session is `Anonymous` or to the 5-tab shell if the session is `Authenticated`. The splash SHALL be dismissed within 2 seconds of session state being available.

#### Scenario: Cold launch, signed out
- **WHEN** user launches the app from the device home screen with no prior process running and no persisted session
- **THEN** the splash screen displays the Kornerz brand
- **AND** the splash routes to the Login screen once session state resolves to `Anonymous`

#### Scenario: Cold launch, signed in
- **WHEN** user launches the app from the device home screen with a valid persisted session
- **THEN** the splash screen displays the Kornerz brand
- **AND** the splash routes directly to the Home tab of the 5-tab shell

#### Scenario: Warm launch
- **WHEN** user resumes the app from the device recents
- **THEN** the splash is not shown
- **AND** the app resumes directly to the last selected tab

### Requirement: Auth and shell graphs are mutually exclusive
The app's root `NavHost` SHALL host exactly one of two child graphs at a time, selected by `SessionRepository.data()`: the auth graph (Login, Signup wizard, Forgot Password, OTP Confirm, Change Password) when session is `Anonymous`, and the shell graph (5-tab bottom-nav) when session is `Authenticated`. Navigating from auth to shell SHALL clear the auth back-stack. Navigating from shell to auth (logout, session expiry) SHALL clear the shell back-stack.

#### Scenario: Sign-in transitions to shell
- **WHEN** the user completes login or signup
- **THEN** the root NavHost switches from the auth graph to the shell graph
- **AND** pressing the system back button from the Home tab exits the app instead of returning to Login

#### Scenario: Logout transitions to auth
- **WHEN** the user logs out from the shell
- **THEN** the root NavHost switches from the shell graph to the auth graph
- **AND** the Login screen is shown with an empty back-stack

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
The Profile destination SHALL be registered **directly on the shell's
root `NavHost`** — not inside a nested `NavHost`. `profile/view`
(introduced in P6, `user-profile` capability) is pushed onto the root
graph (not a tab), and `profile/edit-basic-info`,
`profile/edit-basic-info/{field}` (dialog),
`profile/edit-basic-info/{field}/success`, `profile/privacy-policy`
are registered as sibling destinations on the same root graph,
reachable from the corresponding menu rows (introduced in P6,
`user-profile` + `profile-edit-basic-info` + `privacy-policy-pdf`
capabilities).

**Amendment (crash fix):** the Profile destinations were previously
hosted in a nested `NavHost` inside the `profile/view` destination,
driven by a `NavHostController` hoisted to `AppShell` scope. That
arrangement threw `IllegalStateException: ViewModelStore should be set
before setGraph call` on the **second** visit to Profile: `NavHost`
calls `NavController.setViewModelStore()` with the store of the
*current* back-stack entry, so re-entering Profile handed the shared
controller a different `ViewModelStore` while its `backQueue` was
already populated — and `setViewModelStore` requires an empty back
queue. Registering the destinations on the root graph gives them the
root controller's single store, whose lifetime matches the graph.
Because the destinations are pushed on the root graph, the bottom bar
is hidden across all of them (the `profile/.*` pattern in the
bottom-bar-hidden regex). The shell SHALL register three
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

#### Scenario: Profile destinations render on the root graph
- **WHEN** user opens Profile and then taps Edit Basic Info / Privacy Policy / Change Basic Info (Field)
- **THEN** the bottom bar stays hidden and the matching profile destination renders on the shell's root `NavHost`

#### Scenario: Profile can be reopened without crashing
- **WHEN** user opens Profile, navigates back, and opens Profile again
- **THEN** the Profile screen renders without an `IllegalStateException` (the profile destinations share the root controller's `ViewModelStore`)

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

### Requirement: Loops tab hosts nested NavHost for loops, comment sheet, and reply screen
The Loops tab in `feature/shell` SHALL host a nested `NavHost` with destinations in this order: `loops/list` (the populated Loops tab introduced in P5, `loops-tab` capability) as the start destination; `loops/{loopId}` (the loop detail destination introduced in P5, `loop-detail` capability) reachable from any `LoopItemView` tap inside the Loops feed; `loops/{loopId}/comment` (the create-comment sheet introduced in P5, `loop-comments` capability) registered as a `dialog` route reachable from the loop detail's add-comment row; `loops/{loopId}/reply/{commentId}` (the reply thread introduced in P5, `loop-comments` capability) reachable from any `LoopCommentView` `onReply` affordance. The bottom navigation bar SHALL remain visible across `loops/list`. The bottom navigation bar SHALL be **hidden** across `loops/{loopId}` — the loop detail is a full-screen destination, whatever entry point opened it (the Loops feed, the Home carousel, the Korner detail, a deep link, a notification) — and across the `loops/{loopId}/comment` and `loops/{loopId}/reply/{commentId}` destinations reached from it. The Klouders tab SHALL continue to render its P0 empty-state placeholder with the text "Coming in Phase 10" until P10 ships.

#### Scenario: Loops tab shows populated content in P5+
- **WHEN** a signed-in user opens the Loops tab in P5+
- **THEN** the Loops tab content area displays `LoopsScreen` (the populated Loops tab introduced in P5) instead of the P0 "Coming in Phase 5" placeholder
- **AND** the bottom navigation bar remains visible
- **AND** the Loops tab icon shows selected state
- **AND** the previously selected tab shows unselected state

#### Scenario: Tap a loop row navigates to loop detail
- **WHEN** the user taps any `LoopItemView` row inside the Loops tab feed
- **THEN** the nested NavHost pushes `loops/{loopId}`
- **AND** the bottom navigation bar is hidden
- **AND** the Loops tab remains selected

#### Scenario: Tap add-comment row opens the create-comment sheet
- **WHEN** the user taps the safe-area-inset add-comment row inside the loop detail
- **THEN** the nested NavHost presents `loops/{loopId}/comment` as a `dialog`
- **AND** the bottom navigation bar is hidden
- **AND** the Loops tab remains selected
- **WHEN** the user dismisses the dialog (Cancel, system back, or successful submit)
- **THEN** the dialog closes
- **AND** the loop detail remains on screen
- **AND** any newly created comment is appended to the local list

#### Scenario: Tap reply on a comment opens the reply screen
- **WHEN** the user taps the "Reply" affordance on any `LoopCommentView` row inside the loop detail
- **THEN** the nested NavHost pushes `loops/{loopId}/reply/{commentId}`
- **AND** the bottom navigation bar is hidden
- **AND** the Loops tab remains selected
- **WHEN** the user submits a reply successfully
- **THEN** the reply screen pops back to the loop detail via system back
- **AND** the reply is appended to the local replies list (mirrors iOS `LoopReplyCoordinator.popView()`)

#### Scenario: System back from any Loops-tab nested destination returns to the originating list
- **WHEN** the user is on `loops/{loopId}`, `loops/{loopId}/comment`, or `loops/{loopId}/reply/{commentId}` and presses the system back button
- **THEN** the Loops tab's nested NavHost pops back to its parent within the Loops tab (`loops/list` if entered from the Loops tab; `loops/{loopId}` if entered from the reply screen; pop the dialog if entered from the create-comment sheet)
- **AND** the Loops tab remains selected
- **AND** the bottom navigation bar becomes visible again once the popped-to destination is `loops/list`
- **WHEN** the user is on `loops/list` and presses the system back button
- **THEN** the app exits to the device home screen (the bottom bar does not trap the back gesture)

#### Scenario: The bottom bar is hidden across the whole loop-detail flow
- **WHEN** the user navigates between any pair of destinations within the Loops tab's nested graph (`loops/list`, `loops/{loopId}`, `loops/{loopId}/comment`, `loops/{loopId}/reply/{commentId}`)
- **THEN** the bottom navigation bar remains visible on `loops/list` and is hidden across `loops/{loopId}`, `loops/{loopId}/comment` and `loops/{loopId}/reply/{commentId}`
- **AND** the Loops tab remains selected

### Requirement: Home tab hosts a nested NavHost for the feed and the loop detail
The Home tab in `feature/shell` SHALL host a nested `NavHost` whose start destination renders `HomeScreen`, and which additionally hosts the loop-detail destinations — `loops/{loopId}`, `loops/{loopId}/comment` and `loops/{loopId}/reply/{commentId}` — exactly as the Loops tab hosts them.

Tapping the loop-of-the-day card (`home-loop-of-day` capability) or a recent-loops tile (`home-recent-loops` capability) SHALL push `loops/{loopId}` with `hideBottomBar = true` on the **Home tab's** nested `NavHost`. The user SHALL stay on the Home tab, the bottom navigation bar SHALL be hidden, and the system back button SHALL pop back to the Home feed. This mirrors iOS `HomeTabCoordinator.navigateToLoopDetail`, which pushes the loop detail on the Home tab's own navigation stack with `hidesBottomBarWhenPushed = true`; the Home tab SHALL NOT switch to the Loops tab.

Because the loop detail lives in a tab's **nested** `NavHost`, the root `NavHost`'s current route cannot express the decision. Each tab that hosts the loop detail SHALL lift its nested back-stack entry's `hideBottomBar` argument up to the root `Scaffold`, and the root bottom bar's visibility predicate SHALL read the flag of the **active tab only** — a flag left behind by a tab the user has left SHALL NOT hide the bar on another tab.

#### Scenario: Tap the loop-of-the-day card opens the loop detail inside the Home tab
- **WHEN** the user taps the loop-of-the-day card in the Home tab
- **THEN** the Home tab's nested NavHost pushes `loops/{loopId}` with `hideBottomBar = true`
- **AND** the bottom navigation bar is hidden
- **AND** the Home tab remains the active tab (the Loops tab is not selected)

#### Scenario: Tap a recent-loops tile opens the loop detail inside the Home tab
- **WHEN** the user taps any `LoopItemView` row inside the Home tab's `RecentLoopsView` carousel
- **THEN** the Home tab's nested NavHost pushes `loops/{loopId}` with `hideBottomBar = true`
- **AND** the bottom navigation bar is hidden
- **AND** the Home tab remains the active tab

#### Scenario: System back from the Home tab's loop detail returns to the Home feed
- **WHEN** the user is on `loops/{loopId}` pushed from the Home tab and presses the system back button
- **THEN** the Home tab's nested NavHost pops back to the Home feed
- **AND** the bottom navigation bar becomes visible again

#### Scenario: The comment and reply destinations work inside the Home tab
- **WHEN** the user taps the add-comment bar of a loop detail opened from the Home tab
- **THEN** the Home tab's nested NavHost pushes `loops/{loopId}/comment`
- **WHEN** the user taps the reply affordance on a comment row
- **THEN** the Home tab's nested NavHost pushes `loops/{loopId}/reply/{commentId}`

#### Scenario: A stale tab flag does not hide the bar on another tab
- **WHEN** the user leaves the Loops tab while its loop detail had hidden the bottom bar, and opens the Home tab
- **THEN** the bottom navigation bar is visible on the Home tab

### Requirement: Each tab renders a populated screen or empty-state placeholder
The Home tab in `feature/shell` SHALL render `HomeScreen` (the populated Home tab introduced in P2) instead of the P0 placeholder, hosted on the Home tab's nested `NavHost` together with the loop-detail destinations. The Kornerz tab in `feature/shell` SHALL render `KornerzScreen` (the populated Kornerz tab introduced in P3) and SHALL continue to host its nested `NavHost` with the list and detail destinations. The Nooks tab in `feature/shell` SHALL render `NooksScreen` (the populated Nooks tab introduced in P4) instead of the P0 "Coming in Phase 4" placeholder; the Nooks tab hosts a nested `NavHost` with the list and detail destinations. The Loops tab in `feature/shell` SHALL render `LoopsScreen` (the populated Loops tab introduced in P5) instead of the P0 "Coming in Phase 5" placeholder; the Loops tab hosts a nested `NavHost` with the list, detail, comment sheet, and reply destinations. The **Profile** destination in `feature/shell` SHALL render `ProfileScreen` (the populated Profile screen introduced in P6); its destinations (`profile/view`, edit-basic-info, change-field modal, success modal, privacy-policy) are registered on the shell's root `NavHost` rather than a nested `NavHost` (see the crash-fix amendment above). Until P10 ships its full destination, the Klouders tab SHALL continue to render its P0 empty-state placeholder with the text "Coming in Phase 10".

(modified to add the Profile tab and clarify Klouders)

#### Scenario: Profile tab shows populated content in P6+
- **WHEN** a signed-in user opens the Profile tab in P6+
- **THEN** the Profile tab content area displays `ProfileScreen` with the user header (avatar + name + nickName + Calls/Communities counts) and the menu rows (Notifications, My Data, Privacy Policy, conditionally My Communities)
- **AND** the bottom navigation bar stays hidden across `profile/edit-basic-info` and `profile/privacy-policy` (the Profile destinations are pushed on the root graph)

#### Scenario: Empty state in non-populated tabs until their phase ships
- **WHEN** user opens any tab other than Home, Kornerz, Nooks, Loops, and Profile before that tab's phase ships
- **THEN** the tab content shows the placeholder empty state with the correct phase label

### Requirement: Shell theming follows system dark/light mode
The app SHALL observe the system dark/light mode setting and apply the matching Material 3 ColorScheme. The user SHALL NOT have a manual theme toggle in P0.

#### Scenario: System in light mode
- **WHEN** device system theme is set to light
- **THEN** the app renders with the Kornerz light color scheme

#### Scenario: System in dark mode
- **WHEN** device system theme is set to dark
- **THEN** the app renders with the Kornerz dark color scheme

### Requirement: No anonymous Firebase user at launch in P1
The app SHALL NOT call `FirebaseAuth.signInAnonymously()` during `Application.onCreate()`. Authentication is handled exclusively by the custom-auth Cloud Function client introduced in P1; no implicit Firebase Auth state is created at app launch.

#### Scenario: Fresh install does not provision an anonymous user
- **WHEN** the app is launched on a fresh install
- **THEN** no `FirebaseAuth.signInAnonymously()` call is made
- **AND** the user is unauthenticated in Firebase Auth until they complete signup or login

### Requirement: Shell composables may inject repositories
The shell SHALL be allowed to inject repositories (specifically `SessionRepository` and the FCM registrar) because P1 introduces session-gated routing. Shell composables SHALL NOT yet inject content repositories (Home, Loops, Kornerz, Nooks, Profile) — those are introduced by their respective phases.

#### Scenario: Session-aware shell
- **WHEN** the shell composable is composed at runtime
- **THEN** `SessionRepository` MAY be injected
- **AND** content repositories (HomeRepository, LoopRepository, etc.) SHALL NOT be injected

### Requirement: Shell respects system insets
The 5-tab shell SHALL respect system status bar, navigation bar, and display cutout insets. Content SHALL NOT be obscured by system bars or notches in any orientation.

#### Scenario: Portrait, notch present
- **WHEN** user holds the device in portrait with a notch
- **THEN** the bottom bar sits above the gesture navigation indicator
- **AND** the tab content does not overlap the notch

#### Scenario: Landscape
- **WHEN** user rotates to landscape
- **THEN** the shell relayouts correctly
- **AND** no content is clipped

### Requirement: Account-deletion event routes shell back to auth flow
The app's root `NavHost` SHALL observe the `userDeletedHisAccount` event bus. When the event is emitted (by `AccountDeletionViewModel` after a successful `deleteUserAccount` Cloud Function call), the root `NavHost` SHALL transition from the shell graph to the auth graph, clear the shell back-stack, and show the Login screen. Mirrors iOS `NotificationCenter.default.post(name: .userDeletedHisAccount, object: nil)` + `scene.setRootViewToWindow()` (iOS `UserProfileVC.swift` lines 171–175).

(new requirement added in P6, mirrors iOS `UserProfileVC.deleteAccount` lines 166–175)

#### Scenario: Successful account deletion routes to Login
- **WHEN** `AccountDeletionViewModel` emits `userDeletedHisAccount` after a successful `deleteUserAccount` Cloud Function call
- **THEN** the root `NavHost` switches from the shell graph to the auth graph
- **AND** the Login screen is shown with an empty back-stack
- **AND** pressing the system back button from the Login screen exits the app

#### Scenario: Failed account deletion does not route away
- **WHEN** `deleteUserAccount` Cloud Function returns an error
- **THEN** `AccountDeletionViewModel` exposes the error and the shell graph remains on the Profile tab

### Requirement: CommunityRoute sealed class models the P7 admin list
The shell's navigation contract SHALL expose a `CommunityRoute` sealed
class with one case: `AdminList` (maps to `community/admin/list`).
The `AppShell` composable SHALL register this route as a top-level
route outside any tab's nested NavHost, and SHALL include it in the
set of routes that hide the bottom navigation bar.

The previous P7 draft added a `Create` case. **Cancelled after iOS
audit.**

#### Scenario: CommunityRoute.AdminList is registered
- **WHEN** the shell composable is composed
- **THEN** the route `community/admin/list` is registered as a `composable("community/admin/list")` top-level destination and hides the bottom bar

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
