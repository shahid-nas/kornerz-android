# nooks-tab Specification (delta)

## MODIFIED Requirements

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

## ADDED Requirements

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
