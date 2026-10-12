# nooks-tab Specification

## Purpose
The Nooks tab is the third tab in the bottom navigation bar of the Kornerz Android app. It is a cross-Korner discovery feed that surfaces every nook — live and scheduled — produced by every community the signed-in user can see. It is the Android port of `SwiftUI/Dashboard/Nooks/NookTab/NooksTabView.swift` and ships in Phase 4 of the phased delivery plan (`docs/phases.md`).

## References
- [`docs/architecture.md`](../../../docs/architecture.md) — MVVM + Repository + UDF, `AsyncValue<T>`, `Channel<NavEvent>`, module boundaries, Hilt scopes.
- [`docs/phases.md`](../../../docs/phases.md) Phase 4 — codename *Nooks*.
- [`docs/screen-inventory.md`](../../../docs/screen-inventory.md) §5 — Nooks Tab (3 in-scope screens).
- `openspec/changes/phase-4-nooks-tab/proposal.md` — motivation.
- `openspec/changes/phase-4-nooks-tab/design.md` — technical decisions.

## Requirements

### Requirement: Nooks tab renders a partitioned feed of live and scheduled nooks
The Nooks tab root screen SHALL render a `LazyColumn` partitioned into two sections in this order: a "Live now" section listing every `Nook` with `isLive = true` as `NookCard` rows, followed by an "Upcoming" section listing every `Nook` with `isLive = false` as `ScheduledNookCard` rows. The "Live now" section SHALL be omitted entirely when no live nooks exist. The "Upcoming" section SHALL be omitted entirely when no scheduled nooks exist. Both sections SHALL be rendered via `feature/nooks/.../components/NookCard.kt` and `ScheduledNookCard.kt`. Mirrors iOS `NooksTabView.swift`.

#### Scenario: Signed-in user opens the Nooks tab with 2 live + 3 scheduled nooks
- **WHEN** the signed-in user opens the Nooks tab and `NooksRepository.fetchNooksFeed()` resolves to a `NooksFeed(live = [n1, n2], scheduled = [n3, n4, n5])`
- **THEN** the content area renders a "Live now" section title in Satoshi Bold 18sp above 2 `NookCard` rows
- **AND** an "Upcoming" section title in Satoshi Bold 18sp above 3 `ScheduledNookCard` rows ordered by `startDate` ascending

#### Scenario: Nooks tab with only live nooks
- **WHEN** `NooksRepository.fetchNooksFeed()` resolves to a `NooksFeed(live = [n1, n2], scheduled = [])`
- **THEN** only the "Live now" section is rendered
- **AND** no "Upcoming" section title or rows appear

#### Scenario: Nooks tab with only scheduled nooks
- **WHEN** `NooksRepository.fetchNooksFeed()` resolves to a `NooksFeed(live = [], scheduled = [n1, n2, n3])`
- **THEN** only the "Upcoming" section is rendered
- **AND** no "Live now" section title or rows appear

#### Scenario: Live section is ordered by startDate descending
- **WHEN** the "Live now" section renders for 2 live nooks where `n1.startDate > n2.startDate`
- **THEN** the rows are rendered in the order `[n1, n2]` (most-recently-live first; matches iOS)

#### Scenario: Scheduled section is ordered by startDate ascending
- **WHEN** the "Upcoming" section renders for 3 scheduled nooks where `n1.startDate < n2.startDate < n3.startDate`
- **THEN** the rows are rendered in the order `[n1, n2, n3]` (next-up first; matches iOS)

### Requirement: Nooks tab hosts a nested NavHost with list, detail, and video-call destinations
The Nooks tab `composable` in `feature/shell` SHALL host a nested `NavHost` with exactly three destinations in this order: `nooks/list` (the Nooks tab root), `nooks/{nookId}` (the nook detail destination), and `videocall/{sessionId}` (the video call destination, introduced in P9, `feature-videocall` capability). The nested `NavHost` SHALL preserve the bottom navigation bar visibility rule from `android-app-shell` (the bottom bar remains visible across `nooks/list` and `nooks/{nookId}`; the bottom bar is hidden while `videocall/{sessionId}` is on top, per the P9 amendment to `android-app-shell`). The same `nooks/{nookId}` destination SHALL also be hosted as a nested route inside the Kornerz tab's `NavHost` so the Korner detail's nook tiles can navigate into it without leaving the Kornerz tab (P4 behavior, unchanged in P9).

#### Scenario: Tap a nook card navigates to detail from the Nooks tab
- **WHEN** the user taps a `NookCard` or `ScheduledNookCard` row inside the Nooks tab list
- **THEN** the nested NavHost pushes the `nooks/{nookId}` destination
- **AND** the bottom navigation bar remains visible
- **AND** the Nooks tab remains selected

#### Scenario: Tap a nook card from Korner detail navigates to detail
- **WHEN** the user taps a `NookTile` inside `korner-detail`'s `CurrentNookSection` or `NooksListSection`
- **THEN** the Kornerz tab's nested NavHost pushes the `nooks/{nookId}` destination
- **AND** the bottom navigation bar remains visible
- **AND** the Kornerz tab remains selected

#### Scenario: System back from detail returns to the originating list
- **WHEN** the user is on the `nooks/{nookId}` destination and presses the system back button
- **THEN** the originating tab's nested NavHost pops back to its list destination (`nooks/list` if entered from Nooks tab; `kornerz/{kornerId}` if entered from Korner detail)
- **AND** the originating tab remains selected

#### Scenario: Tap Join on a live card opens the video call
- **WHEN** the user taps the Join button on a `LiveKornerCard` whose underlying `Nook.sessionId` is `"vsess-123"`
- **THEN** the Nooks tab's nested NavHost pushes `videocall/vsess-123`
- **AND** the bottom navigation bar is hidden
- **AND** the Nooks tab remains selected
- **AND** pressing the system back button from the video call returns to `nooks/list`
- **AND** the bottom navigation bar reappears

### Requirement: LiveKornerCard Join button is a tappable region that emits JoinVideoCall
The `LiveKornerCard` `@Composable` in `feature/nooks/.../components/LiveKornerCard.kt` SHALL render the Join button as its own tappable region (separate `Modifier.clickable` from the row-level click). Tapping the Join button SHALL emit `NooksNavEvent.JoinVideoCall(sessionId: String)` where `sessionId` is the underlying `Nook.sessionId`. The row-level click (the entire card) SHALL continue to emit `NooksNavEvent.NavigateToNookDetail(nookId)` so the card body still routes to the nook detail. When the underlying `Nook.sessionId` is `null`, the Join button SHALL be hidden entirely (the nook exists but is not associated with a Vonage room).

#### Scenario: Tapping the Join button on a live card with a sessionId
- **WHEN** the user taps the Join button on a `LiveKornerCard` rendered for a `Nook(nookId = "n1", sessionId = "vsess-123", isLive = true)`
- **THEN** `NooksNavEvent.JoinVideoCall("vsess-123")` is emitted
- **AND** the Nooks tab's nested NavHost routes to `videocall/vsess-123`

#### Scenario: Tapping the card body still routes to nook detail
- **WHEN** the user taps anywhere on the `LiveKornerCard` row outside the Join button
- **THEN** `NooksNavEvent.NavigateToNookDetail("n1")` is emitted
- **AND** the Nooks tab's nested NavHost routes to `nooks/n1`

#### Scenario: Join button is hidden when sessionId is null
- **WHEN** `LiveKornerCard` is rendered for a `Nook(nookId = "n1", sessionId = null, isLive = true)`
- **THEN** the Join button is not rendered
- **AND** tapping the card body still routes to `nooks/n1`
- **AND** tapping where the Join button would have been routes to `nooks/n1` (the row-level click is the only handler in that region)

### Requirement: Nooks tab handles loading, error, and empty states
The Nooks tab SHALL render 6 shimmer placeholder cards while `NooksState.feed is AsyncValue.Loading`. The Nooks tab SHALL render a centered retry view with a "Try again" button when `NooksState.feed is AsyncValue.Error`. The Nooks tab SHALL render a centered "No live nooks" empty state when the "Live now" section would otherwise be empty AND a centered "No upcoming nooks" empty state when the "Upcoming" section would otherwise be empty (the empty-state copy replaces the section title). Mirrors iOS `NooksTabView.swift`'s usage of `NoDataView` per section. Pull-to-refresh SHALL re-invoke `NooksViewModel.refresh()` via `PullToRefreshBox`.

#### Scenario: Loading state renders shimmer placeholders
- **WHEN** `NooksState.feed is AsyncValue.Loading`
- **THEN** the screen renders 6 shimmer placeholder rows that match the live nook card layout (cover area + text rows, no copy)
- **AND** no section titles appear

#### Scenario: Error state renders retry view
- **WHEN** `NooksRepository.fetchNooksFeed()` resolves to an error
- **THEN** the screen renders a retry view with the Kornerz brand mark, error copy, and a "Try again" button
- **AND** tapping "Try again" invokes `NooksViewModel.refresh()` which re-runs the fetch

#### Scenario: Pull-to-refresh re-fetches the feed
- **WHEN** the user triggers a pull-to-refresh gesture on the Nooks tab list
- **THEN** the view model re-invokes `NooksRepository.fetchNooksFeed()`
- **AND** the list re-renders with the new data