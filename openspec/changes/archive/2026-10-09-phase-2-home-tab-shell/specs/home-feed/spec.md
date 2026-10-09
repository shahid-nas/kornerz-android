## ADDED Requirements

### Requirement: Home tab shows five populated sections for signed-in users
The Home tab SHALL render, in order, exactly five sections: (1) Home header, (2) Loop of the day, (3) Upcoming nooks, (4) Your Kornerz, (5) Recent loops, when the signed-in user has at least one membership and the recent loops section has data. The order SHALL mirror `SwiftUI/Dashboard/HomeTab/HomeTabView.swift` `carouselsView`. If the user has no memberships, sections 3 and 4 SHALL be hidden (consistent with iOS `updateCellToDisplay` skipping empty carousels) and the Home tab SHALL still render sections 1, 2, and 5.

#### Scenario: Signed-in user with memberships sees all five sections
- **WHEN** user is signed in and is a member of at least one Korner
- **THEN** the Home tab renders Header, Loop of the day, Upcoming nooks, Your Kornerz, Recent loops in that order
- **AND** each section occupies the same vertical order as the iOS Home tab

#### Scenario: Signed-in user with no memberships
- **WHEN** user is signed in but is not a member of any Korner
- **THEN** sections Upcoming nooks and Your Kornerz are hidden
- **AND** Header, Loop of the day, and Recent loops still render

#### Scenario: Recent articles section is rendered below Recent loops
- **WHEN** the merged recent articles feed returns at least one item
- **THEN** the Recent articles section renders below Recent loops
- **AND** if the feed returns zero items, the section is hidden (mirrors iOS `updateCellToDisplay` skipping empty carousels)

### Requirement: Home tab root state models each section as AsyncValue
The Home tab root `HomeState` SHALL expose one `AsyncValue` per section: `header: AsyncValue<HomeHeaderData>`, `loopOfDay: AsyncValue<Loop>`, `upcomingNooks: AsyncValue<List<Nook>>`, `yourKornerz: AsyncValue<List<Community>>`, `recentLoops: AsyncValue<List<Loop>>`, `recentArticles: AsyncValue<List<RecentItem>>`. Composables SHALL render `Loading` / `Success` / `Error` exhaustively per arch §2.2. **No** `isLoading = true` booleans or `error: String?` are exposed to Composables.

#### Scenario: Each section tracks loading independently
- **WHEN** the user opens the Home tab
- **THEN** every section begins in `AsyncValue.Loading`
- **AND** each section transitions to `AsyncValue.Success` or `AsyncValue.Error` independently as its fetch completes

#### Scenario: One section error does not block the others
- **WHEN** the Recent loops fetch returns an error
- **THEN** the Recent loops section renders its `Error` layout with a Retry CTA
- **AND** the other sections still render their fetched data normally

### Requirement: Home tab supports pull-to-refresh
The Home tab SHALL expose a pull-to-refresh gesture (mirrors `HomeTabView.swift` `.refreshable`). A pull-to-refresh SHALL emit `HomeEvent.Refresh` to the `HomeViewModel`, which re-invokes every section's fetch in parallel and updates each section's `AsyncValue` independently.

#### Scenario: Pull-to-refresh re-runs every section fetch
- **WHEN** the user pulls down on the Home tab and releases
- **THEN** `HomeViewModel.refresh()` is called
- **AND** every section's underlying Firestore query is re-invoked
- **AND** each section transitions to `AsyncValue.Loading` while refetching

#### Scenario: Pull-to-refresh dismisses when all sections settle
- **WHEN** every section has settled back to `AsyncValue.Success` or `AsyncValue.Error`
- **THEN** the refresh indicator is dismissed

### Requirement: Home tab navigation events go through Channel
The Home tab SHALL emit every navigation action (open Profile, open Loop detail, open Event detail, open Korner detail, open recent item) through `Channel<HomeNavEvent>` per arch §2.4. The `HomeViewModel` SHALL NOT reference `NavController` directly. The `HomeScreen` Composable SHALL collect `navEvents` and route via `NavController.navigate(...)` with `popUpTo(...)` clearing as required.

#### Scenario: Tap on header avatar emits NavigateToProfile
- **WHEN** user taps the Home header avatar
- **THEN** `HomeViewModel.onUserTap()` emits `HomeNavEvent.NavigateToProfile`
- **AND** `HomeScreen` collects the event and routes to the Profile destination

#### Scenario: Tap on a recent loop emits NavigateToLoopDetail
- **WHEN** user taps a tile in the Recent loops carousel
- **THEN** `HomeViewModel.onLoopTap(loopId)` emits `HomeNavEvent.NavigateToLoopDetail(loopId)`
- **AND** `HomeScreen` routes to the Loop detail destination

#### Scenario: Tap on an upcoming nook emits NavigateToEventDetail
- **WHEN** user taps a tile in the Upcoming nooks carousel
- **THEN** `HomeViewModel.onNookTap(nookId)` emits `HomeNavEvent.NavigateToEventDetail(nookId)`

#### Scenario: Tap on a Korner tile emits NavigateToKornerDetail
- **WHEN** user taps a tile in the Your Kornerz carousel
- **THEN** `HomeViewModel.onKornerTap(kornerId)` emits `HomeNavEvent.NavigateToKornerDetail(kornerId)`

#### Scenario: Tap on a recent article tile emits NavigateToRecentItemDetail
- **WHEN** user taps a tile in the Recent articles section
- **THEN** `HomeViewModel.onRecentItemTap(itemId)` emits `HomeNavEvent.NavigateToRecentItemDetail(itemId)`

### Requirement: Home tab destinations resolve in P2 without crashing
Until P3 / P5 / P6 ship their full destinations, every Home tab navigation event SHALL resolve to a non-fatal placeholder: a `KornerzSnackbar` with the message "Coming in Phase N" where N is the destination's phase, surfaced through `Channel<HomeUiEvent>` and rendered by `HomeScreen` without navigating the back-stack. Once the owning phase ships, the same `HomeNavEvent` resolves to the real destination.

#### Scenario: NavigateToLoopDetail before P5 ships
- **WHEN** user taps a recent loop tile in P2
- **THEN** `HomeScreen` displays a "Coming in Phase 5" snackbar
- **AND** the back-stack is unchanged

### Requirement: Home tab matches iOS design 1:1
Every Home tab section Composable SHALL visually match its iOS counterpart (`SwiftUI/Dashboard/HomeTab/Sections/*`) in spacing, typography, color, and gesture behavior. The shared `KornerzDiamondImage` (1:1 port of iOS `DiamondImageView`) SHALL be used for the avatar and community tiles; the iOS Home header layout (52dp diamond / radius 10, horizontal padding 16dp, points icon + Satoshi Bold 14sp) SHALL be preserved; the iOS Home feed composer (`LazyVStack(spacing: .zero)`) SHALL be ported as a `LazyColumn` with `Arrangement.spacedBy(0.dp)`.

#### Scenario: Home header matches iOS avatar
- **WHEN** `HomeHeaderView` renders the user's avatar
- **THEN** it uses `KornerzDiamondImage` at 52dp / radius 10
- **AND** the avatar is tappable and invokes `onUserTap`

#### Scenario: Home feed composer matches iOS vertical layout
- **WHEN** the Home tab root composes its `LazyColumn`
- **THEN** the item spacing is 0dp
- **AND** each section renders inside its own `item` block
- **AND** pull-to-refresh wraps the `LazyColumn`

### Requirement: Home tab reuses design-system components
Every Home tab section Composable SHALL consume components from `core/ui/components/` (arch §5) — `KornerzAvatar`, `KornerzLoadingView`, `KornerzErrorState`, `KornerzDiamondImage`, `HomeShimmerPlaceholder`. Sections SHALL NOT re-implement loading, error, or avatar rendering.

#### Scenario: No reinvented loading state
- **WHEN** a section transitions to `AsyncValue.Loading`
- **THEN** the section renders `KornerzLoadingView` (or `HomeShimmerPlaceholder` for the header)
- **AND** does not implement a custom spinner

#### Scenario: No reinvented error state
- **WHEN** a section transitions to `AsyncValue.Error`
- **THEN** the section renders `KornerzErrorState` with a Retry CTA
- **AND** does not implement a custom error card

## References

- [`docs/architecture.md`](../../../docs/architecture.md) — MVVM + Repository + UDF (§1), `AsyncValue<T>` (§2.2), `Channel<NavEvent>` (§2.4), module boundaries (§2.5), shared components (§5).
- `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/HomeTabView.swift` — iOS source-of-truth for section ordering and pull-to-refresh.
- `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/HomeTabViewModel.swift` — iOS source-of-truth for the per-section `HomeTabCarouselType` enum.
- `docs/phases.md` §"Phase 2 — Home Tab & Tab Shell" — 16 screens and the "First Impression" demoable claim.
- `docs/screen-inventory.md` — `3.1` Home tab root, `3.2` Home header, `3.3` Home feed section composer, `3.4` Loop of the day, `3.5` Recent articles, `3.6` Recent loops, `3.7` Upcoming nooks, `3.8` Your Kornerz, `3.9` Kornerz carousel.