## MODIFIED Requirements

### Requirement: Five-tab bottom navigation shell

(modified to add the Profile tab and the notifications list route)

The app SHALL render a bottom navigation bar with exactly 5 tabs in this left-to-right order: Home, Kornerz, Nooks, Loops, **Profile** (introduced in P6, `user-profile` capability). The Klouders tab SHALL continue to render its P0 empty-state placeholder with the text "Coming in Phase 10" until P10 ships; the placeholder SHALL move to a 6th tab position or remain in tab order 5 per stakeholder decision (current decision: keep 5 tabs, Profile replaces Klouders in tab 5 visually; Klouders remains an unreachable stub until P10). Each tab SHALL be reachable by tapping its icon. The currently selected tab SHALL be visually distinguished. The bottom bar SHALL remain visible while navigating within a tab's nested graph. The Nooks tab SHALL host a nested `NavHost` with the `nooks/list` destination (introduced in P4, `nooks-tab` capability) as its start destination, `nooks/{nookId}` (introduced in P4, `nook-detail` capability) as a secondary destination reachable from any `NookCard` or `NookTile` tap, and `videocall/{sessionId}` (introduced in P9, `feature-videocall` capability) as a tertiary destination reachable from the Join button on a `LiveKornerCard` (introduced in P9, `nooks-tab` capability amendment). The Loops tab SHALL host a nested `NavHost` with the `loops/list` destination (introduced in P5, `loops-tab` capability) as its start destination, `loops/{loopId}` (introduced in P5, `loop-detail` capability) as a secondary destination reachable from any `LoopItemView` tap, `loops/{loopId}/comment` (introduced in P5, `loop-comments` capability) as a `dialog` route reachable from the loop detail's add-comment row, and `loops/{loopId}/reply/{commentId}` (introduced in P5, `loop-comments` capability) as a tertiary destination reachable from any `LoopCommentView` `onReply` affordance. The **Profile** tab (introduced in P6) SHALL host a nested `NavHost` with destinations `profile/view` (the populated Profile tab, introduced in P6, `user-profile` capability) as the start destination, `profile/edit-basic-info` (introduced in P6, `profile-edit-basic-info` capability), `profile/edit-basic-info/{field}` (introduced in P6, `profile-edit-basic-info-field` capability, registered as a `dialog` route), `profile/edit-basic-info/{field}/success` (also a `dialog` route), and `profile/privacy-policy` (introduced in P6, `privacy-policy-pdf` capability). The app-level NavHost SHALL additionally host a top-level `notifications/list` destination (introduced in P6, `notifications-list` capability) reachable from the Profile tab's Notifications menu row. The shell-level tab order, selection, insets, and theming requirements are otherwise unchanged.

#### Scenario: Tab order matches iOS
- **WHEN** the app is launched on a signed-in session
- **THEN** the tabs appear in order: Home, Kornerz, Nooks, Loops, Profile

#### Scenario: Tab selection
- **WHEN** user taps the Nooks tab
- **THEN** the Nooks tab content area displays `NooksScreen` (the populated Nooks tab introduced in P4) instead of the P0 "Coming in Phase 4" placeholder
- **AND** the Nooks tab icon shows selected state
- **AND** the previously selected tab shows unselected state

#### Scenario: Bottom bar persists within a tab
- **WHEN** user navigates from a tab root to a nested screen within that tab (e.g., from `nooks/list` to `nooks/{nookId}` inside the Nooks tab, or from `kornerz/{id}` to `nooks/{nookId}` inside the Kornerz tab, or from `profile/view` to `profile/edit-basic-info` inside the Profile tab)
- **THEN** the bottom navigation bar remains visible
- **AND** the originating tab remains selected

#### Scenario: Bottom bar is hidden during a video call
- **WHEN** user is on the `videocall/{sessionId}` destination inside the Nooks tab's nested `NavHost`
- **THEN** the bottom navigation bar is hidden (iOS parity — the call is a full-screen experience)
- **AND** pressing the system back button returns to `nooks/list` inside the Nooks tab
- **AND** the bottom navigation bar reappears
- **AND** the Nooks tab remains selected

#### Scenario: Profile tab shows populated content in P6+
- **WHEN** a signed-in user opens the Profile tab in P6+
- **THEN** the Profile tab content area displays `ProfileScreen` (the populated Profile tab introduced in P6, `user-profile` capability) instead of the P0 "Coming in Phase 6" placeholder
- **AND** the bottom navigation bar remains visible

#### Scenario: Tap Notifications menu row routes to notifications list
- **WHEN** user taps the Notifications menu row inside `ProfileScreen`
- **THEN** the shell routes to the app-level `notifications/list` destination
- **AND** the bottom navigation bar is hidden (the notifications list is a full-screen push, mirroring iOS `UserProfileVC.swift` line 253 which pushes `NotificationsController` as a full-screen route on the global nav stack — iOS hides the tab bar on push)
- **AND** pressing system back returns to the Profile tab's `profile/view` destination

### Requirement: Each tab renders a populated screen or empty-state placeholder

(modified to add the Profile tab and clarify Klouders)

The Home tab in `feature/shell` SHALL render `HomeScreen` (the populated Home tab introduced in P2) instead of the P0 placeholder. The Kornerz tab in `feature/shell` SHALL render `KornerzScreen` (the populated Kornerz tab introduced in P3) and SHALL continue to host its nested `NavHost` with the list and detail destinations. The Nooks tab in `feature/shell` SHALL render `NooksScreen` (the populated Nooks tab introduced in P4) instead of the P0 "Coming in Phase 4" placeholder; the Nooks tab hosts a nested `NavHost` with the list and detail destinations. The Loops tab in `feature/shell` SHALL render `LoopsScreen` (the populated Loops tab introduced in P5) instead of the P0 "Coming in Phase 5" placeholder; the Loops tab hosts a nested `NavHost` with the list, detail, comment sheet, and reply destinations. The **Profile** tab in `feature/shell` SHALL render `ProfileScreen` (the populated Profile tab introduced in P6) instead of the P0 "Coming in Phase 6" placeholder; the Profile tab hosts a nested `NavHost` with the view, edit-basic-info, change-field modal, success modal, and privacy-policy destinations. Until P10 ships its full destination, the Klouders tab SHALL continue to render its P0 empty-state placeholder with the text "Coming in Phase 10".

#### Scenario: Profile tab shows populated content in P6+
- **WHEN** a signed-in user opens the Profile tab in P6+
- **THEN** the Profile tab content area displays `ProfileScreen` with the user header (avatar + name + nickName + Calls/Communities counts) and the menu rows (Notifications, My Data, Privacy Policy, conditionally My Communities)
- **AND** the bottom navigation bar remains visible across `profile/edit-basic-info` and `profile/privacy-policy`

#### Scenario: Empty state in non-populated tabs until their phase ships
- **WHEN** user opens any tab other than Home, Kornerz, Nooks, Loops, and Profile before that tab's phase ships
- **THEN** the tab content shows the placeholder empty state with the correct phase label

### Requirement: Account-deletion event routes shell back to auth flow

(new requirement added in P6, mirrors iOS `UserProfileVC.deleteAccount` lines 166–175)

The app's root `NavHost` SHALL observe the `userDeletedHisAccount` event bus. When the event is emitted (by `AccountDeletionViewModel` after a successful `deleteUserAccount` Cloud Function call), the root `NavHost` SHALL transition from the shell graph to the auth graph, clear the shell back-stack, and show the Login screen. Mirrors iOS `NotificationCenter.default.post(name: .userDeletedHisAccount, object: nil)` + `scene.setRootViewToWindow()` (iOS `UserProfileVC.swift` lines 171–175).

#### Scenario: Successful account deletion routes to Login
- **WHEN** `AccountDeletionViewModel` emits `userDeletedHisAccount` after a successful `deleteUserAccount` Cloud Function call
- **THEN** the root `NavHost` switches from the shell graph to the auth graph
- **AND** the Login screen is shown with an empty back-stack
- **AND** pressing the system back button from the Login screen exits the app

#### Scenario: Failed account deletion does not route away
- **WHEN** `deleteUserAccount` Cloud Function returns an error
- **THEN** `AccountDeletionViewModel` exposes the error and the shell graph remains on the Profile tab

## References
- [`docs/architecture.md`](../../../../docs/architecture.md) — shell, bottom-nav, NavHost conventions
- `kornerz/Kornerz/HomeVC/ProfileVC/UserProfileVC.swift` — iOS source-of-truth for the profile menu + deleteAccount flow + `userDeletedHisAccount` event
- `kornerz/Kornerz/HomeVC/Notifications/NotificationsController.swift` — iOS source-of-truth for the notifications route as a full-screen push from the profile
- `docs/phases.md` §P6 — Profile & Notifications phase deliverables