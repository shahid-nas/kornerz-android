# android-app-shell Specification

## Purpose
TBD - created by archiving change p0-android-foundation. Update Purpose after archive.

## ADDED Requirements

### Requirement: Loops tab hosts nested NavHost for loops, comment sheet, and reply screen
The Loops tab in `feature/shell` SHALL host a nested `NavHost` with destinations in this order: `loops/list` (the populated Loops tab introduced in P5, `loops-tab` capability) as the start destination; `loops/{loopId}` (the loop detail destination introduced in P5, `loop-detail` capability) reachable from any `LoopItemView` tap inside the Loops feed or the Home recent loops carousel (P2 `home-recent-loops` capability amendment in P5); `loops/{loopId}/comment` (the create-comment sheet introduced in P5, `loop-comments` capability) registered as a `dialog` route reachable from the loop detail's add-comment row; `loops/{loopId}/reply/{commentId}` (the reply thread introduced in P5, `loop-comments` capability) reachable from any `LoopCommentView` `onReply` affordance. The bottom navigation bar SHALL remain visible across `loops/list` and `loops/{loopId}`. The bottom navigation bar SHALL remain visible across `loops/{loopId}/comment` (iOS parity — the create-comment modal is centered, not full-screen). The bottom navigation bar SHALL remain visible across `loops/{loopId}/reply/{commentId}` (iOS parity — the reply screen is a regular destination, not full-screen). The Klouders tab SHALL continue to render its P0 empty-state placeholder with the text "Coming in Phase 10" until P10 ships.

#### Scenario: Loops tab shows populated content in P5+
- **WHEN** a signed-in user opens the Loops tab in P5+
- **THEN** the Loops tab content area displays `LoopsScreen` (the populated Loops tab introduced in P5) instead of the P0 "Coming in Phase 5" placeholder
- **AND** the bottom navigation bar remains visible
- **AND** the Loops tab icon shows selected state
- **AND** the previously selected tab shows unselected state

#### Scenario: Tap a loop row navigates to loop detail
- **WHEN** the user taps any `LoopItemView` row inside the Loops tab feed
- **THEN** the nested NavHost pushes `loops/{loopId}`
- **AND** the bottom navigation bar remains visible
- **AND** the Loops tab remains selected

#### Scenario: Tap a recent-loops row in the Home tab navigates to loop detail
- **WHEN** the user taps any `LoopItemView` row inside the Home tab's `RecentLoopsView` carousel (P2 `home-recent-loops` capability, refactored in P5)
- **THEN** the app-level NavHost routes through the shell to the Loops tab's nested NavHost and pushes `loops/{loopId}`
- **AND** the bottom navigation bar remains visible
- **AND** the Loops tab becomes selected (the recent-loops carousel entry crosses tab boundaries — same as P3/P4 iOS `LoopDetailCoordinator` behavior)
- **AND** pressing system back from `loops/{loopId}` returns to the Loops tab's `loops/list` destination

#### Scenario: Tap add-comment row opens the create-comment sheet
- **WHEN** the user taps the safe-area-inset add-comment row inside the loop detail
- **THEN** the nested NavHost presents `loops/{loopId}/comment` as a `dialog`
- **AND** the bottom navigation bar remains visible
- **AND** the Loops tab remains selected
- **WHEN** the user dismisses the dialog (Cancel, system back, or successful submit)
- **THEN** the dialog closes
- **AND** the loop detail remains on screen
- **AND** any newly created comment is appended to the local list

#### Scenario: Tap reply on a comment opens the reply screen
- **WHEN** the user taps the "Reply" affordance on any `LoopCommentView` row inside the loop detail
- **THEN** the nested NavHost pushes `loops/{loopId}/reply/{commentId}`
- **AND** the bottom navigation bar remains visible
- **AND** the Loops tab remains selected
- **WHEN** the user submits a reply successfully
- **THEN** the reply screen pops back to the loop detail via system back
- **AND** the reply is appended to the local replies list (mirrors iOS `LoopReplyCoordinator.popView()`)

#### Scenario: System back from any Loops-tab nested destination returns to the originating list
- **WHEN** the user is on `loops/{loopId}`, `loops/{loopId}/comment`, or `loops/{loopId}/reply/{commentId}` and presses the system back button
- **THEN** the Loops tab's nested NavHost pops back to its parent within the Loops tab (`loops/list` if entered from the Loops tab; `loops/{loopId}` if entered from the reply screen; pop the dialog if entered from the create-comment sheet)
- **AND** the Loops tab remains selected
- **AND** the bottom navigation bar remains visible
- **WHEN** the user is on `loops/list` and presses the system back button
- **THEN** the app exits to the device home screen (the bottom bar does not trap the back gesture)

#### Scenario: Bottom bar persists for all Loops-tab destinations
- **WHEN** the user navigates between any pair of destinations within the Loops tab's nested graph (`loops/list`, `loops/{loopId}`, `loops/{loopId}/comment`, `loops/{loopId}/reply/{commentId}`)
- **THEN** the bottom navigation bar remains visible
- **AND** the Loops tab remains selected

## MODIFIED Requirements

### Requirement: Five-tab bottom navigation shell

The app SHALL render a bottom navigation bar with exactly 5 tabs in this left-to-right order: Home, Kornerz, Nooks, Loops, Klouders. Each tab SHALL be reachable by tapping its icon. The currently selected tab SHALL be visually distinguished. The bottom bar SHALL remain visible while navigating within a tab's nested graph. The Nooks tab SHALL host a nested `NavHost` with the `nooks/list` destination (introduced in P4, `nooks-tab` capability) as its start destination, `nooks/{nookId}` (introduced in P4, `nook-detail` capability) as a secondary destination reachable from any `NookCard` or `NookTile` tap, and `videocall/{sessionId}` (introduced in P9, `feature-videocall` capability) as a tertiary destination reachable from the Join button on a `LiveKornerCard` (introduced in P9, `nooks-tab` capability amendment). The Loops tab SHALL host a nested `NavHost` with the `loops/list` destination (introduced in P5, `loops-tab` capability) as its start destination, `loops/{loopId}` (introduced in P5, `loop-detail` capability) as a secondary destination reachable from any `LoopItemView` tap, `loops/{loopId}/comment` (introduced in P5, `loop-comments` capability) as a `dialog` route reachable from the loop detail's add-comment row, and `loops/{loopId}/reply/{commentId}` (introduced in P5, `loop-comments` capability) as a tertiary destination reachable from any `LoopCommentView` `onReply` affordance. The shell-level tab order, selection, insets, and theming requirements are otherwise unchanged.

#### Scenario: All four nested-graph destinations across Nooks, Kornerz, and Loops remain reachable from the bottom bar
- **WHEN** the shell is composed at runtime
- **THEN** the Nooks tab's nested NavHost hosts `nooks/list`, `nooks/{nookId}`, and `videocall/{sessionId}`
- **AND** the Kornerz tab's nested NavHost hosts `kornerz/{id}` and `nooks/{nookId}` (cross-graph entry from P4)
- **AND** the Loops tab's nested NavHost hosts `loops/list`, `loops/{loopId}`, `loops/{loopId}/comment` (dialog), and `loops/{loopId}/reply/{commentId}`
- **AND** all of these are reachable via the bottom navigation bar
- **AND** the bottom bar remains visible across every nested destination

### Requirement: Each tab renders a populated screen or empty-state placeholder

The Home tab in `feature/shell` SHALL render `HomeScreen` (the populated Home tab introduced in P2) instead of the P0 placeholder. The Kornerz tab in `feature/shell` SHALL render `KornerzScreen` (the populated Kornerz tab introduced in P3) and SHALL continue to host its nested `NavHost` with the list and detail destinations. The Nooks tab in `feature/shell` SHALL render `NooksScreen` (the populated Nooks tab introduced in P4) instead of the P0 "Coming in Phase 4" placeholder; the Nooks tab hosts a nested `NavHost` with the list and detail destinations. The Loops tab in `feature/shell` SHALL render `LoopsScreen` (the populated Loops tab introduced in P5) instead of the P0 "Coming in Phase 5" placeholder; the Loops tab hosts a nested `NavHost` with the list, detail, comment sheet, and reply destinations. Until P10 ships its full destination, the Klouders tab SHALL continue to render its P0 empty-state placeholder with the text "Coming in Phase 10".

#### Scenario: Loops tab shows populated content in P5+
- **WHEN** a signed-in user opens the Loops tab in P5+
- **THEN** the Loops tab content area displays `LoopsScreen` instead of the P0 "Coming in Phase 5" placeholder
- **AND** the Loops tab hosts a nested NavHost with `loops/list` as the start destination, `loops/{loopId}` as the loop detail, `loops/{loopId}/comment` as a dialog, and `loops/{loopId}/reply/{commentId}` as the reply thread

#### Scenario: Klouders tab continues to show the P0 placeholder
- **WHEN** a signed-in user opens the Klouders tab in P5+
- **THEN** the Klouders tab content area displays the P0 empty-state placeholder with the text "Coming in Phase 10"
