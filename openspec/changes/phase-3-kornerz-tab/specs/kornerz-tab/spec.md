# kornerz-tab Specification

## Purpose
TBD - created by archiving change phase-3-kornerz-tab. Update Purpose after archive.
## Requirements
### Requirement: Kornerz tab renders a browseable list of Kornerz
The Kornerz tab root screen SHALL render all `Korner` items the signed-in user can see in a vertical `LazyColumn`, mirroring `SwiftUI/Dashboard/Korner/KornerzView.swift`. Each row SHALL render the Korner cover image (Coil-loaded with shimmer placeholder), the Korner name in Satoshi Bold 16sp, the Korner's theme color as a left-edge accent strip (4dp wide), and a trailing chevron indicating "tappable". The list SHALL be sorted alphabetically by Korner name (matches iOS `KornerzViewModel.fetchData()` ordering). A loading state SHALL render 6 shimmer placeholder rows while `AsyncValue.Loading` is active. An error state SHALL render a retry view that re-invokes `KornerzViewModel.refresh()`. An empty state SHALL render a centered illustration with copy "No Kornerz yet" (mirrors iOS `NoDataView` placeholder).

#### Scenario: Signed-in user opens the Kornerz tab with 5 Kornerz
- **WHEN** the signed-in user opens the Kornerz tab and `KornerzRepository.fetchAllKornerz()` resolves to a list of 5 `Korner` items
- **THEN** the tab content area renders 5 rows in alphabetical order
- **AND** each row is tappable and emits `KornerzNavEvent.NavigateToKornerDetail(kornerId)`

#### Scenario: Loading state renders shimmer placeholders
- **WHEN** the signed-in user opens the Kornerz tab and `AsyncValue.Loading` is active
- **THEN** the screen renders 6 shimmer placeholder rows that match the Korner row layout (no text, no images, accent strip area visible)
- **AND** no "Coming in Phase 3" text is shown

#### Scenario: Error state renders retry view
- **WHEN** `KornerzRepository.fetchAllKornerz()` resolves to an error
- **THEN** the screen renders a retry view with the Kornerz brand mark, error copy, and a "Try again" button
- **AND** tapping "Try again" invokes `KornerzViewModel.refresh()` which re-runs the fetch

#### Scenario: Empty list renders no-data illustration
- **WHEN** `KornerzRepository.fetchAllKornerz()` resolves to an empty list
- **THEN** the screen renders a centered illustration with copy "No Kornerz yet"

### Requirement: Kornerz tab hosts a nested NavHost with list and detail destinations
The Kornerz tab `composable` in `feature/shell` SHALL host a nested `NavHost` with exactly two destinations in this order: `kornerz/list` (the Kornerz tab root list) and `kornerz/{kornerId}` (the Korner detail). The nested `NavHost` SHALL preserve the bottom navigation bar visibility rule from `android-app-shell` (the bottom bar remains visible across both destinations).

#### Scenario: Tap a Korner row navigates to detail
- **WHEN** the user taps a row in the Kornerz tab list
- **THEN** the nested NavHost pushes the `kornerz/{kornerId}` destination
- **AND** the bottom navigation bar remains visible

#### Scenario: System back from detail returns to list
- **WHEN** the user is on the Korner detail destination and presses the system back button
- **THEN** the nested NavHost pops to the `kornerz/list` destination
- **AND** the Kornerz tab remains selected

### Requirement: Kornerz tab includes a shared header composable
The Kornerz tab SHALL include a `KornerzHeaderView` composable at the top of the list, mirroring `SwiftUI/Dashboard/Korner/KornerzHeaderView.swift`. The header SHALL display the title "Kornerz" in Satoshi Bold 24sp, with a filter chips placeholder area below the title (static this phase, no chips rendered — mirrors iOS placeholder). The header SHALL be a `core/ui/components/` shared composable so future screens (P4 Nooks tab, P7 Create Community) can reuse the same look.

#### Scenario: Header is present above the list
- **WHEN** the Kornerz tab renders
- **THEN** the top of the `LazyColumn` displays the `KornerzHeaderView` above all rows
- **AND** the header title reads "Kornerz"

#### Scenario: Header chips area is a static placeholder
- **WHEN** the Kornerz tab renders
- **THEN** the header's filter chips area renders an empty `Row` (no chips) with reserved vertical space of 40dp

### Requirement: Kornerz tab view model exposes AsyncValue state and navigation events
`KornerzViewModel` SHALL expose `state: StateFlow<KornerzState>` where `KornerzState` contains a single `AsyncValue<List<Korner>>` for the list, and `events: Channel<KornerzNavEvent>` for navigation. `KornerzNavEvent.NavigateToKornerDetail(kornerId: String)` SHALL be emitted on row tap. `KornerzViewModel.refresh()` SHALL re-run the fetch and update state.

#### Scenario: Initial composition triggers fetch
- **WHEN** the Kornerz tab composable first enters the composition
- **THEN** `KornerzViewModel` initiates `fetchAllKornerz()`
- **AND** `state` transitions from `AsyncValue.Uninitialized` to `AsyncValue.Loading`

#### Scenario: Refresh re-runs the fetch
- **WHEN** the user triggers pull-to-refresh on the Kornerz tab
- **THEN** `KornerzViewModel.refresh()` re-runs `fetchAllKornerz()`
- **AND** `state` transitions through `AsyncValue.Loading` to a new `AsyncValue.Success` or `AsyncValue.Error`