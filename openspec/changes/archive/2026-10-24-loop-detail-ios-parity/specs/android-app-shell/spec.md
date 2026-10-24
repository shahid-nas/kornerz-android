# android-app-shell Specification

## References
- [`docs/architecture.md`](../../../../docs/architecture.md) — shell, bottom-nav, NavHost conventions, module boundaries, `Channel<NavEvent>` navigation.
- `kornerz/Kornerz/SwiftUI/Dashboard/Loops/LoopsTab/LoopsTabCoordinator.swift` — iOS source-of-truth for the loop-detail push from the Loops tab.
- `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/HomeTabCoordinator.swift` — iOS source-of-truth for the loop-detail push from the Home tab, which sets `hidesBottomBarWhenPushed = true`.
- `kornerz/Kornerz/SwiftUI/Dashboard/Korner/KornerDetail/KornerDetailCoordinator.swift` — iOS source-of-truth for the loop-detail push from the Korner detail, which sets `hidesBottomBarWhenPushed = true`.
- [`openspec/changes/loop-detail-ios-parity/proposal.md`](../proposal.md) — capability rationale for the iOS parity work.
- [`openspec/changes/loop-detail-ios-parity/design.md`](../design.md) — the cross-tab navigation seam and the bottom-bar decision.

## MODIFIED Requirements

### Requirement: Loops tab hosts nested NavHost for loops, comment sheet, and reply screen
The Loops tab in `feature/shell` SHALL host a nested `NavHost` with destinations in this order: `loops/list` (the populated Loops tab introduced in P5, `loops-tab` capability) as the start destination; `loops/{loopId}` (the loop detail destination introduced in P5, `loop-detail` capability) reachable from any `LoopItemView` tap inside the Loops feed; `loops/{loopId}/comment` (the create-comment sheet introduced in P5, `loop-comments` capability) registered as a `dialog` route reachable from the loop detail's add-comment bar; `loops/{loopId}/reply/{commentId}` (the reply thread introduced in P5, `loop-comments` capability) reachable from any `LoopCommentView` `onReply` affordance.

The `loops/{loopId}` route SHALL accept a `hideBottomBar` Boolean navigation argument that defaults to `true`. The loop detail is a **full-screen** destination: the bottom navigation bar SHALL be **hidden** across `loops/{loopId}` whichever entry point opened it — the **Loops tab** feed, the **Home** recent-loops carousel and the loop-of-the-day card (which push the detail into the **Home tab's** nested `NavHost`, see the Home-tab requirement below), the **Korner detail**, a **deep link** and a **notification**. The bottom navigation bar SHALL remain visible across `loops/list`.

This is a deliberate divergence from iOS `LoopsTabCoordinator.navigateToLoopDetail`, which does not set `hidesBottomBarWhenPushed` and therefore keeps the tab bar across the detail opened from the Loops feed. The Android build hides it on every path so the loop detail is one full-screen experience regardless of where it was opened.

The bottom navigation bar SHALL be **hidden** across `loops/{loopId}/comment` (the create-comment sheet) and across `loops/{loopId}/reply/{commentId}` (the reply thread) as well: both are reached from the full-screen loop detail and are presented over it, so the tab bar stays hidden for the whole loop-detail flow. The Klouders tab SHALL continue to render its P10 ranking destination.

Because the loop detail lives in a tab's **nested** `NavHost`, the root `NavHost`'s current route cannot express the decision. The shell SHALL therefore lift the nested back-stack entry's `hideBottomBar` argument to the root `Scaffold` — through the same seam the shell already uses for the cross-tab pending id — so the root bottom bar's visibility predicate accounts for it. Only the flag of the **active tab** SHALL count: a flag left behind by a tab the user has left SHALL NOT hide the bar on another tab.

#### Scenario: Loops tab shows populated content in P5+
- **WHEN** a signed-in user opens the Loops tab in P5+
- **THEN** the Loops tab content area displays `LoopsScreen` (the populated Loops tab introduced in P5) instead of the P0 "Coming in Phase 5" placeholder
- **AND** the bottom navigation bar remains visible
- **AND** the Loops tab icon shows selected state
- **AND** the previously selected tab shows unselected state

#### Scenario: Tap a loop row navigates to loop detail
- **WHEN** the user taps any `LoopItemView` row inside the Loops tab feed
- **THEN** the nested NavHost pushes `loops/{loopId}` with `hideBottomBar = true`
- **AND** the bottom navigation bar is hidden
- **AND** the Loops tab remains selected

#### Scenario: Tap a recent-loops row in the Home tab navigates to loop detail inside the Home tab and hides the bottom bar
- **WHEN** the user taps any `LoopItemView` row inside the Home tab's `RecentLoopsView` carousel (P2 `home-recent-loops` capability, refactored in P5)
- **THEN** the Home tab's nested NavHost pushes `loops/{loopId}` with `hideBottomBar = true`
- **AND** the bottom navigation bar is hidden
- **AND** the Home tab remains the active tab
- **AND** pressing system back from `loops/{loopId}` returns to the Home feed

#### Scenario: The Korner detail pushes the loop detail with the bottom bar hidden
- **WHEN** the user taps a loop row inside the Korner detail
- **THEN** the Loops tab's nested NavHost pushes `loops/{loopId}` with `hideBottomBar = true`
- **AND** the bottom navigation bar is hidden

#### Scenario: A loop deep link pushes the loop detail with the bottom bar hidden
- **WHEN** a `newLoop` or `loopComment` deep link resolves to `DeepLinkTarget.LoopDetail`
- **THEN** the Loops tab's nested NavHost pushes `loops/{loopId}` with `hideBottomBar = true`
- **AND** the bottom navigation bar is hidden

#### Scenario: Tap add-comment bar opens the create-comment sheet
- **WHEN** the user taps the bottom add-comment bar inside the loop detail
- **THEN** the nested NavHost presents `loops/{loopId}/comment` as a `dialog`
- **AND** the bottom navigation bar is hidden
- **AND** the Loops tab remains selected
- **WHEN** the user dismisses the dialog (Cancel, system back, or successful submit)
- **THEN** the dialog closes
- **AND** the loop detail remains on screen
- **AND** any newly created comment is appended to the local list

#### Scenario: Tap reply on a comment opens the reply screen
- **WHEN** the user taps the reply affordance on any `LoopCommentView` row inside the loop detail
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
- **WHEN** the user is on `loops/list` and presses the system back button
- **THEN** the app exits to the device home screen (the bottom bar does not trap the back gesture)

#### Scenario: The bottom bar is hidden across the whole loop-detail flow
- **WHEN** the user navigates between any pair of destinations within the Loops tab's nested graph (`loops/list`, `loops/{loopId}`, `loops/{loopId}/comment`, `loops/{loopId}/reply/{commentId}`)
- **THEN** the bottom navigation bar remains visible on `loops/list` and is hidden across `loops/{loopId}`, `loops/{loopId}/comment` and `loops/{loopId}/reply/{commentId}`
- **AND** the Loops tab remains selected

### Requirement: Home tab hosts a nested NavHost for the feed and the loop detail
The Home tab in `feature/shell` SHALL host a nested `NavHost` whose start destination renders `HomeScreen`, and which additionally hosts the loop-detail destinations — `loops/{loopId}`, `loops/{loopId}/comment` and `loops/{loopId}/reply/{commentId}` — exactly as the Loops tab hosts them.

Tapping the loop-of-the-day card (`home-loop-of-day` capability) or a recent-loops tile (`home-recent-loops` capability) SHALL push `loops/{loopId}` with `hideBottomBar = true` on the **Home tab's** nested `NavHost`. The user SHALL stay on the Home tab, the bottom navigation bar SHALL be hidden, and the system back button SHALL pop back to the Home feed. This mirrors iOS `HomeTabCoordinator.navigateToLoopDetail`, which pushes the loop detail on the Home tab's own navigation stack with `hidesBottomBarWhenPushed = true`; the Home tab SHALL NOT switch to the Loops tab.

Because the loop detail lives in a tab's **nested** `NavHost`, the root `NavHost`'s current route cannot express the per-entry-point decision. Each tab that hosts the loop detail SHALL lift its nested back-stack entry's `hideBottomBar` argument up to the root `Scaffold`, and the root bottom bar's visibility predicate SHALL read the flag of the **active tab only** — a flag left behind by a tab the user has left SHALL NOT hide the bar on another tab.

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

## ADDED Requirements

### Requirement: The loop detail's Korner navigation event reaches the Korner detail destination
`feature/shell` SHALL route `LoopDetailNavEvent.NavigateToKornerDetail(communityId)` to the existing Korner detail destination instead of dropping it. The Loops tab's nested `NavHost` SHALL push the Korner detail using the shell's existing cross-tab seam — the same mechanism the shell already uses for the pending Korner id — so the destination renders inside the Kornerz tab's nested graph and the Kornerz tab becomes the selected tab.

Tapping the community name in the loop detail title row SHALL NOT be a no-op. Mirrors iOS `LoopDetailCoordinator.navigateToKornerDetail(_:color:)`, which pushes `KornerDetailController` with `hidesBottomBarWhenPushed = true`.

#### Scenario: Tapping the community name opens the Korner detail
- **WHEN** the loop detail emits `LoopDetailNavEvent.NavigateToKornerDetail("c1")`
- **THEN** the shell routes the request to the Kornerz tab
- **AND** the Korner detail for community `"c1"` renders
- **AND** the Kornerz tab becomes the selected tab
- **AND** the bottom navigation bar is hidden across the Korner detail, matching iOS `hidesBottomBarWhenPushed = true`

#### Scenario: The event is no longer dropped
- **WHEN** the loop detail emits `LoopDetailNavEvent.NavigateToKornerDetail`
- **THEN** no branch of the shell's event handler leaves the navigation event unhandled

