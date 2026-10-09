# home-recent-loops Specification

## Purpose
TBD - created by archiving change phase-2-home-tab-shell. Update Purpose after archive.
## Requirements
### Requirement: Recent loops renders a horizontal carousel of recent loops
The Recent loops section SHALL render a horizontal carousel of the signed-in user's most recent loops (limit 10, sorted by `createdAt` descending), mirroring `SwiftUI/Dashboard/HomeTab/Sections/RecentLoops/RecentLoopsView.swift`. Each tile SHALL render the loop thumbnail (Coil-loaded), the loop title in Satoshi Bold 14sp, the loop color accent (from the iOS `KornerzCardPalette`), and a small play indicator.

#### Scenario: Carousel renders recent loops
- **WHEN** the recent loops fetch returns 5 loops
- **THEN** the section renders 5 tiles in a horizontal scrollable row
- **AND** each tile is tappable and emits `HomeNavEvent.NavigateToLoopDetail(loopId)`

#### Scenario: Empty recent loops hides the section
- **WHEN** the recent loops fetch returns zero items
- **THEN** the section is hidden
- **AND** no empty-state placeholder is shown

### Requirement: Recent loops uses LazyRow with peek effect
The carousel SHALL use Compose `LazyRow` with content padding equal to half the tile width, so the next tile peeks by ~16dp on the right edge — matches the iOS horizontal carousel behavior (`RecentLoopsView.swift`).

#### Scenario: Peek behavior matches iOS
- **WHEN** the LazyRow renders
- **THEN** the first tile aligns to the leading edge with 16dp padding
- **AND** the next tile peeks by ~16dp on the trailing edge

