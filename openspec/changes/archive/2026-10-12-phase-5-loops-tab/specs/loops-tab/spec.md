# loops-tab Specification

## Purpose
The `loops-tab` capability defines the Loops bottom-nav tab root — a paginated, filterable, searchable feed of loops — plus its filter chip row and search bar, and the single loop item row used inside the feed. This is the Android port of `kornerz/SwiftUI/Dashboard/Loops/LoopsTab/LoopsTabView.swift`, `LoopsTabViewModel.swift`, `LoopsFilterView.swift`, and `LoopItemView.swift`. The capability owns the Loops tab screen (6.1), the Loops filter + search bar (6.2), and the single loop item row (6.3). The shared `LoopItemView` composable is also reused by `feature/home/.../RecentLoopsSection` after the P5 refactor — see [`home-recent-loops`](../../../specs/home-recent-loops/spec.md) `MODIFIED Requirements` for the home side.

## References
- [`docs/architecture.md`](../../../docs/architecture.md) — MVVM + Repository + UDF, `AsyncValue<T>`, `Channel<NavEvent>`, module boundaries, Hilt scopes, `core/ui/components/` conventions.
- [`docs/phases.md`](../../../docs/phases.md) Phase 5 — codename *Loops*.
- [`docs/screen-inventory.md`](../../../docs/screen-inventory.md) §6 — Loops Tab (10 in-scope screens).
- `openspec/changes/phase-5-loops-tab/proposal.md` — capability rationale.
- `openspec/changes/phase-5-loops-tab/design.md` — technical decisions, especially D5 (filter + search debounce) and D6 (pagination cursor).
- `kornerz/Kornerz/SwiftUI/Dashboard/Loops/LoopsTab/*` — iOS source-of-truth.

## ADDED Requirements

### Requirement: Loops tab renders a paginated, filterable feed of loops
The Loops tab root screen SHALL render a vertical list of `LoopItemView` rows, ordered by `creationDate` descending, paginated via a Firestore `startAfterDocument` cursor (mirrors iOS `lastDocument`). The tab SHALL expose a filter chip row (`All` / `Your Loops`) and a search bar (magnifying-glass button → capsule search field with Cancel) — exactly the iOS `LoopsFilterView.swift` behavior. The tab SHALL host a nested `NavHost` with destinations `loops/list` (this screen) and `loops/{loopId}` (the loop detail). The tab SHALL observe `LoopsState` (`feed: AsyncValue<PagingState<LoopCardData>>`, `selectedFilter: LoopsFilter = ALL`, `searchQuery: String = ""`, `isSearchMode: Boolean = false`, `isLoadingMore: Boolean = false`, `hasMoreData: Boolean = true`) and SHALL emit `LoopsNavEvent`s over a `Channel<LoopsNavEvent>(BUFFERED)` per arch §2.4.

#### Scenario: Signed-in user opens the Loops tab for the first time
- **WHEN** the Loops tab is opened for the first time and `LoopsState.feed is AsyncValue.Loading`
- **THEN** `LoopsViewModel` invokes `LoopRepository.observeLoops(filter = ALL, search = "")` (first page)
- **AND** the screen renders shimmer placeholder rows while loading

#### Scenario: Signed-in user scrolls to the bottom of the feed
- **WHEN** the last row in the rendered list enters the viewport
- **THEN** `LoopsViewModel.onEvent(LoopsEvent.LoadMore)` fires
- **AND** `LoopRepository.loadMore(cursor)` is invoked with the next-page cursor
- **AND** the new rows append to the list
- **AND** when `hasMoreData = false`, no further `LoadMore` fires

#### Scenario: Signed-in user pulls down to refresh
- **WHEN** the user triggers a pull-to-refresh gesture
- **THEN** `LoopsViewModel.onEvent(LoopsEvent.Refresh)` fires
- **AND** the cursor resets to `null`
- **AND** the first page is re-fetched
- **AND** the rendered list is replaced end-to-end (not appended)

#### Scenario: Signed-in user switches the filter from All to Your Loops
- **WHEN** the user taps the "Your Loops" chip
- **THEN** `LoopsState.selectedFilter = YOUR_LOOPS`
- **AND** the cursor resets to `null`
- **AND** `LoopRepository.observeLoops(filter = YOUR_LOOPS, search = "")` runs with the new filter
- **AND** if there are zero results, the empty state renders with "No loops available" / "No loops description"

#### Scenario: Signed-in user searches for "brazil"
- **WHEN** the user toggles the magnifying-glass button, types "brazil" into the capsule search field, and waits 1.5 seconds (debounce)
- **THEN** `LoopsState.searchQuery = "brazil"` and `LoopsState.isSearchMode = true`
- **AND** `LoopRepository.searchLoops(query = "brazil", cursor = null)` runs
- **AND** the cursor resets to `null`
- **AND** the rendered list shows only loops whose `title.contains("brazil")` is `true`
- **WHEN** the user submits via the keyboard "Enter" key
- **THEN** the debounce timer is bypassed and the search runs immediately (mirrors iOS `shouldTriggerImmediateSearch = true`)

#### Scenario: Signed-in user cancels the search
- **WHEN** the user taps "Cancel" inside the search field
- **THEN** `LoopsState.searchQuery = ""` and `LoopsState.isSearchMode = false`
- **AND** `LoopRepository.observeLoops(filter = ALL, search = "")` runs again
- **AND** the chip row re-appears with `All` selected

#### Scenario: Tap a loop row navigates to loop detail
- **WHEN** the user taps any `LoopItemView` row
- **THEN** `LoopsViewModel.onEvent(LoopsEvent.LoopClicked(loopId))` fires
- **AND** `Channel<LoopsNavEvent>` emits `LoopsNavEvent.NavigateToLoopDetail(loopId)`
- **AND** the nested NavHost pushes `loops/{loopId}`

### Requirement: Feed handles loading, error, and empty states
The Loops tab SHALL render shimmer placeholder rows while `LoopsState.feed is AsyncValue.Loading`. The Loops tab SHALL render a `KornerzErrorState` with a "Try again" button when `LoopsState.feed is AsyncValue.Error`. The Loops tab SHALL render a `KornerzEmptyState` with the iOS copy "No loops available" / "No loops description" when `LoopsState.feed` is `AsyncValue.Success(empty list)`. A `LoadingMoreView` SHALL render at the bottom of the list while `LoopsState.isLoadingMore = true`. Mirrors iOS `LoopsTabView.swift` lines 15–25 (`isLoading && loops.isEmpty` → shimmer, `error` → errorView, `loops.isEmpty` → noDataView, else → contentView).

#### Scenario: Feed renders shimmer placeholders during the first load
- **WHEN** `LoopsState.feed is AsyncValue.Loading`
- **THEN** the screen renders 4 `LoopItemView` rows with redacted placeholder content (no thumbnail, no title copy)
- **AND** no error or empty state is shown

#### Scenario: Feed renders retry view on fetch failure
- **WHEN** `LoopsState.feed is AsyncValue.Error(error)` and `LoopsState.feed.value is not the result of a `LoadMore`` (i.e., initial load failure)
- **THEN** the screen renders a `KornerzErrorState` with the error message and a "Try again" button
- **AND** tapping "Try again" invokes `LoopsViewModel.refresh()` which re-runs the first-page fetch

#### Scenario: Feed renders empty state when zero loops match
- **WHEN** `LoopsState.feed` resolves to `AsyncValue.Success(PagingState(items = [], cursor = null, hasMore = false))`
- **THEN** the screen renders a `KornerzEmptyState` with "No loops available" as title and "No loops description" as subtitle
- **AND** no rows are rendered

#### Scenario: Feed appends results with a LoadingMoreView while loading the next page
- **WHEN** the user scrolls past the last row and `LoopsState.isLoadingMore = true`
- **THEN** a `LoadingMoreView` renders at the bottom of the list
- **AND** the existing rows remain interactive (taps still navigate to detail)

### Requirement: LoopItemView composable renders the iOS loop row layout
The `LoopItemView` `@Composable` SHALL render a single loop row with the following structure, matching iOS `LoopItemView.swift`: a 100dp × 100dp thumbnail (Coil-loaded with shimmer placeholder) on the leading edge with a 55dp circular publisher-avatar overlay at the bottom-leading corner, a `Column` on the trailing edge with the loop title (Satoshi Bold 12sp, 2-line max), the community name + creation date ("3d ago") subtitle row in Satoshi 12sp, the description (Satoshi 12sp, 3-line max, gray foreground), and a stats icon row with eye / message / heart icons (15dp each) followed by `viewCounts` / `commentsCount` / `likesCount` in Satoshi 10sp gray. The composable SHALL accept `loop: LoopCardData`, `color: Color` (the per-row accent color computed by `RandomColorService.color(for: index)`), and `modifier: Modifier = Modifier` (last, per arch §4). Tap → `onClick(loopId: String)`.

#### Scenario: LoopItemView renders all sections for a fully populated loop
- **WHEN** a `LoopItemView` is composed with a fully populated `LoopCardData`
- **THEN** the thumbnail renders Coil-loaded with shimmer fallback
- **AND** the publisher-avatar overlay renders at the bottom-leading corner of the thumbnail
- **AND** the title renders in Satoshi Bold 12sp with a 2-line max
- **AND** the community + date subtitle renders in Satoshi 12sp
- **AND** the description renders in Satoshi 12sp with a 3-line max
- **AND** the stats row renders eye / message / heart icons with view / comment / like counts in Satoshi 10sp gray

#### Scenario: LoopItemView thumbnail falls back to the accent color while loading
- **WHEN** a `LoopItemView` is composed with `LoopCardData.thumbnailUrl = null`
- **THEN** the thumbnail tile renders with `color` as the background fill
- **AND** a placeholder icon renders centered

#### Scenario: LoopItemView tap navigates to the loop detail
- **WHEN** the user taps anywhere on the `LoopItemView` row
- **THEN** `onClick(loopId)` fires with the matched loop id
- **AND** the parent screen routes into `loops/{loopId}`
