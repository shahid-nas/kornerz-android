# android-app-shell Specification

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
`privacy-policy-pdf` capabilities). The shell SHALL register four
additional top-level, full-screen (bottom-bar-hidden) routes alongside
the shell graph: `community/admin/list` (introduced in P7),
`subscription/plans`, and `subscription/success` (both introduced in
P8, `subscription-plans` capability), and `deeplink/resolving`
(introduced in P10, `deep-link-routing` capability).

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
`.ignoresSafeArea(edges = .top)`).

**P10 addition (bottom-bar-hidden set):** the same `Regex` SHALL also
match `deeplink/.*`, so the deep-link interstitial (13.1) renders
full-screen. The Klouders tab SHALL additionally host the populated
ranking destination (introduced in P10, `ranking-leaderboard`
capability) in place of the P0 placeholder.

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

#### Scenario: The deep-link interstitial hides the bottom bar
- **WHEN** a deep link is routed and the shell navigates to `deeplink/resolving`
- **THEN** the bottom bar is hidden and the interstitial renders full-screen

#### Scenario: The deep-link interstitial is top-level, not tab-nested
- **WHEN** `AppShell` registers `deeplink/resolving`
- **THEN** it is registered on the root `NavHost` outside every tab's nested `NavHost`

### Requirement: Loops tab hosts nested NavHost for loops, comment sheet, and reply screen
The Loops tab in `feature/shell` SHALL host a nested `NavHost` with destinations in this order: `loops/list` (the populated Loops tab introduced in P5, `loops-tab` capability) as the start destination; `loops/{loopId}` (the loop detail destination introduced in P5, `loop-detail` capability) reachable from any `LoopItemView` tap inside the Loops feed or the Home recent loops carousel (P2 `home-recent-loops` capability amendment in P5); `loops/{loopId}/comment` (the create-comment sheet introduced in P5, `loop-comments` capability) registered as a `dialog` route reachable from the loop detail's add-comment row; `loops/{loopId}/reply/{commentId}` (the reply thread introduced in P5, `loop-comments` capability) reachable from any `LoopCommentView` `onReply` affordance. The bottom navigation bar SHALL remain visible across `loops/list` and `loops/{loopId}`. The bottom navigation bar SHALL remain visible across `loops/{loopId}/comment` (iOS parity — the create-comment modal is centered, not full-screen). The bottom navigation bar SHALL remain visible across `loops/{loopId}/reply/{commentId}` (iOS parity — the reply screen is a regular destination, not full-screen). The Klouders tab SHALL render the populated ranking screen (introduced in P10, `ranking-leaderboard` capability) in place of the P0 empty-state placeholder.

#### Scenario: Loops tab renders its populated feed
- **WHEN** user taps the Loops tab
- **THEN** the `loops/list` destination renders inside the Loops tab's nested `NavHost`

#### Scenario: Bottom bar stays visible across Loops destinations
- **WHEN** the user navigates between any pair of destinations within the Loops tab's nested graph (`loops/list`, `loops/{loopId}`, `loops/{loopId}/comment`, `loops/{loopId}/reply/{commentId}`)
- **THEN** the bottom navigation bar remains visible
- **AND** the Loops tab remains selected

#### Scenario: The Klouders tab is populated in P10
- **WHEN** user taps the Klouders tab
- **THEN** the ranking screen renders instead of an empty-state placeholder

### Requirement: Each tab renders a populated screen or empty-state placeholder
The Home tab in `feature/shell` SHALL render `HomeScreen` (the populated Home tab introduced in P2) instead of the P0 placeholder. The Kornerz tab in `feature/shell` SHALL render `KornerzScreen` (the populated Kornerz tab introduced in P3) and SHALL continue to host its nested `NavHost` with the list and detail destinations. The Nooks tab in `feature/shell` SHALL render `NooksScreen` (the populated Nooks tab introduced in P4) instead of the P0 "Coming in Phase 4" placeholder; the Nooks tab hosts a nested `NavHost` with the list and detail destinations. The Loops tab in `feature/shell` SHALL render `LoopsScreen` (the populated Loops tab introduced in P5) instead of the P0 "Coming in Phase 5" placeholder; the Loops tab hosts a nested `NavHost` with the list, detail, comment sheet, and reply destinations. The **Profile** tab in `feature/shell` SHALL render `ProfileScreen` (the populated Profile tab introduced in P6) instead of the P0 "Coming in Phase 6" placeholder; the Profile tab hosts a nested `NavHost` with the view, edit-basic-info, change-field modal, success modal, and privacy-policy destinations. The **Klouders** tab in `feature/shell` SHALL render `RankingScreen` (the populated Klouders tab introduced in P10, `ranking-leaderboard` capability) instead of the P0 "Coming in Phase 10" placeholder.

(modified to add the Profile tab and clarify Klouders; P10 replaces the Klouders placeholder with the populated ranking screen)

#### Scenario: Profile tab shows populated content in P6+
- **WHEN** a signed-in user opens the Profile tab in P6+
- **THEN** the Profile tab content area displays `ProfileScreen` with the user header (avatar + name + nickName + Calls/Communities counts) and the menu rows (Notifications, My Data, Privacy Policy, conditionally My Communities)
- **AND** the bottom navigation bar remains visible across `profile/edit-basic-info` and `profile/privacy-policy`

#### Scenario: Empty state in non-populated tabs before their phase ships
- **WHEN** user opens any tab other than Home, Kornerz, Nooks, Loops, Profile, and Klouders before that tab's phase ships
- **THEN** the tab content shows the placeholder empty state with the correct phase label

#### Scenario: The Klouders tab shows populated content in P10+
- **WHEN** a signed-in user opens the Klouders tab in P10+
- **THEN** the tab content area displays the leaderboard with its header row and ranked entries
- **AND** the text "Coming in Phase 10" is no longer rendered

## ADDED Requirements

### Requirement: The shell owns the deep-link routing lifecycle
The shell SHALL own the `DeepLinkManager`'s lifecycle: it SHALL call `notifyAppLaunch()` once its `NavHost` is composed, so a link that arrived during a cold start is released only when the graph can route it.

The shell SHALL collect the manager's released links and SHALL push the `deeplink/resolving` route once per released link, in release order.

The shell SHALL execute the resolved target using its existing cross-tab navigation mechanism — the `pendingLoopId` / `pendingKornerId` handoff plus the tab switch — because the loop detail and Korner detail destinations live in the Loops and Kornerz tabs' nested `NavHost`s and cannot be pushed on the root controller directly.

#### Scenario: The app-launch notification happens after the graph is composed
- **WHEN** the shell's `NavHost` is composed
- **THEN** `DeepLinkManager.notifyAppLaunch()` is called exactly once

#### Scenario: A released link pushes the interstitial
- **WHEN** the manager releases a link
- **THEN** the shell navigates to `deeplink/resolving`

#### Scenario: A loop target reaches the Loops tab
- **WHEN** the resolver returns `LoopDetail("loop-1")`
- **THEN** the shell switches to the Loops tab and the tab's nested graph opens `loops/loop-1`

#### Scenario: A Korner target reaches the Kornerz tab
- **WHEN** the resolver returns `KornerDetail("doc-1")`
- **THEN** the shell switches to the Kornerz tab and the tab's nested graph opens `kornerz/doc-1`
