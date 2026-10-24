# home-loop-of-day Specification

## Purpose
TBD - created by archiving change phase-2-home-tab-shell. Update Purpose after archive.
## Requirements
### Requirement: Loop of the day renders one featured loop
The Loop of the day section SHALL render a single featured `Loop` card mirroring `SwiftUI/Dashboard/HomeTab/Sections/LoopSection/LoopOfDaySectionView.swift`. The card SHALL show: the loop's thumbnail image (Coil-loaded), the loop title in Satoshi Bold 16sp, the author's avatar (`KornerzDiamondImage` 12dp / radius 4) and display name in Satoshi Regular 12sp, the loop color (from `Loop.color`, mapped to the iOS `KornerzCardPalette` swatches when missing), and a play indicator overlay on the thumbnail.

#### Scenario: Loop of the day renders with one loop
- **WHEN** the loop-of-the-day fetch returns one loop
- **THEN** the section renders one card filling the carousel width
- **AND** the card is tappable and emits `HomeNavEvent.NavigateToLoopDetail(loopId)`, which the Home tab's nested `NavHost` pushes as `loops/{loopId}` with the bottom bar hidden (iOS parity — `HomeTabCoordinator.navigateToLoopDetail` sets `hidesBottomBarWhenPushed = true`)

#### Scenario: Loop of the day hides when no loop is available
- **WHEN** the loop-of-the-day fetch returns zero loops
- **THEN** the section is hidden
- **AND** no empty-state placeholder is shown (mirrors iOS behavior — the iOS section simply does not render when empty)

### Requirement: Loop of the day state models loading and error
The Loop of the day state SHALL expose `loopOfDay: AsyncValue<Loop>` per arch §2.2. Composables render `Loading` / `Success` / `Error` exhaustively. While `AsyncValue.Loading`, the section SHALL render a single-card skeleton (`HomeShimmerPlaceholder` variant for loop cards) matching the iOS shimmer.

#### Scenario: Loading state renders a single-card skeleton
- **WHEN** `loopOfDay` is `AsyncValue.Loading`
- **THEN** the section renders a single card-sized shimmer placeholder

#### Scenario: Error state surfaces a retry CTA
- **WHEN** `loopOfDay` transitions to `AsyncValue.Error`
- **THEN** the section renders `KornerzErrorState` with a Retry button that emits `HomeEvent.LoopOfDayRetry`

