# home-recent-loops Specification

## Purpose
TBD - created by archiving change phase-2-home-tab-shell. Update Purpose after archive.
## Requirements
### Requirement: Recent loops renders a horizontal carousel of recent loops
The Recent loops section SHALL render a horizontal carousel of the signed-in user's most recent loops (limit 10, sorted by `createdAt` descending), mirroring `SwiftUI/Dashboard/HomeTab/Sections/RecentLoops/RecentLoopsView.swift`. Each tile SHALL compose the `LoopItemView(data: LoopCardData, color: Color, modifier: Modifier = Modifier)` composable from `feature/loops` (introduced in P5, `loops-tab` capability) so the home carousel and the Loops tab feed share a single row renderer. The `LoopItemView` SHALL render the loop thumbnail (Coil-loaded with shimmer placeholder), the loop title in Satoshi Bold 12sp (2-line max), the community name + creation date subtitle row in Satoshi 12sp, the description in Satoshi 12sp (3-line max, gray foreground), the publisher-avatar overlay at the bottom-leading corner of the thumbnail, and the stats row (eye / comments / likes) in Satoshi 10sp gray. Each tile is tappable and emits `HomeNavEvent.NavigateToLoopDetail(loopId)` which the shell routes into `loops/{loopId}` (introduced in P5, `loop-detail` capability).

#### Scenario: Carousel renders recent loops
- **WHEN** the recent loops fetch returns 5 loops
- **THEN** the section renders 5 tiles in a horizontal scrollable row
- **AND** each tile composes `LoopItemView` from `feature/loops` with the matching `LoopCardData` and per-tile accent color
- **AND** each tile is tappable and emits `HomeNavEvent.NavigateToLoopDetail(loopId)` which the shell routes into the Loops tab's nested NavHost at `loops/{loopId}`

#### Scenario: Recent loops tile routes to loop detail (crosses tab boundary)
- **WHEN** the user taps a `LoopItemView` row in the Home tab's recent-loops carousel
- **THEN** the app switches the bottom navigation bar selection to the Loops tab
- **AND** the Loops tab's nested NavHost pushes `loops/{loopId}`
- **AND** the bottom navigation bar remains visible
- **WHEN** the user presses the system back button from `loops/{loopId}`
- **THEN** the Loops tab's nested NavHost pops back to `loops/list`
- **AND** the Loops tab remains selected (matching iOS `LoopDetailCoordinator.popView()` semantics)

#### Scenario: Empty recent loops hides the section
- **WHEN** the recent loops fetch returns zero items
- **THEN** the section is hidden
- **AND** no empty-state placeholder is shown

### Requirement: Recent loops uses LazyRow with peek effect
The carousel SHALL use Compose `LazyRow` with content padding equal to half the tile width, so the next tile peeks by ~16dp on the right edge — matches the iOS horizontal carousel behavior (`RecentLoopsView.swift`). The P2 inline row composable SHALL be deleted; `LoopItemView` (introduced in P5, `loops-tab` capability) is the single row renderer for both the home carousel and the Loops tab feed.

#### Scenario: Peek behavior matches iOS
- **WHEN** the LazyRow renders
- **THEN** the first tile aligns to the leading edge with 16dp padding
- **AND** the next tile peeks by ~16dp on the trailing edge
- **AND** each tile uses `Modifier.padding(horizontal = 4.dp)` so adjacent tiles render the iOS-equivalent gap