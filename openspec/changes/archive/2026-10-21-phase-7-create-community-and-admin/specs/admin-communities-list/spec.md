# admin-communities-list Specification

## Purpose
TBD - created by archiving change phase-7-create-community-and-admin. Update Purpose after archive.

## ADDED Requirements

### Requirement: Admin list shows communities owned by the current user
The admin-owned-communities list (`community/admin/list`, screens 10.1 + 10.2) SHALL render a top app bar (back button + "My Communities" title) above a `LazyColumn` of `CommunityItemRow`s (one per row, screen 10.3) for every community where `createdByUserId == currentUserCode`. The list SHALL be sorted by `createdAtTimestamp DESC`. Reached from Profile → "My Communities" menu row (when `ownedCommunityCount > 0`) or from the Create Community wizard after a successful Submit. Mirrors iOS `AdminCommunityListController.viewDidLoad` lines 38–71.

#### Scenario: Open from Profile My Communities menu row
- **WHEN** user taps "My Communities" on the Profile tab and `ownedCommunityCount > 0`
- **THEN** shell navigates to `community/admin/list`

#### Scenario: Open from Create Community wizard success
- **WHEN** `CreateCommunityNavEvent.SubmitCompleted(communityCode)` fires
- **THEN** shell navigates to `community/admin/list` and the new community appears at the top of the list

#### Scenario: Admin list shows zero rows for a user with no owned communities
- **WHEN** `AdminCommunitiesRepository.fetchOwnedByUser(userCode)` returns an empty list (e.g., this menu row was not visible because the count was 0, but the user navigated via deep link)
- **THEN** the screen renders `KornerzEmptyState` ("You have not created any community yet" copy + a "Create your first community" pill button → `community/create`)

### Requirement: CommunityItemRow renders cover + name + description + counts
Each `CommunityItemRow` (10.3) SHALL render: a 56-pt circular cover thumbnail on the left (Coil with `imageURL` source + `R.drawable.ic_community_placeholder` fallback; if no imageURL exists, render a `Brush.verticalGradient(theme.gradientBrush)` placeholder), a vertically-stacked title block (community `name` — Satoshi Bold 17-pt, color `C353446`, maxLines = 1; community `description` — Satoshi 13-pt, color `C9E9EAE`, maxLines = 1, ellipsized), and a trailing vertical stack of two count pills (Members: N + Nooks: N, each a 24-pt × 60-pt rounded pill on `CF2F4F6` with Satoshi 11-pt label + Satoshi 13-pt number). The whole row is a 73-pt-tall rounded card on `colorScheme.surface` with `colorScheme.outlineVariant` 1-pt stroke. Tap emits `onCommunityClicked(communityCode)`. Mirrors iOS `CommunityItemView.swift` lines 12–88.

#### Scenario: Row with cover image
- **WHEN** the community has `firebaseImageURL != null` and Coil loads successfully
- **THEN** the cover thumbnail displays the image, no gradient placeholder

#### Scenario: Row without cover image renders gradient
- **WHEN** the community has `firebaseImageURL == null`
- **THEN** the cover thumbnail renders `Brush.verticalGradient(theme.gradientBrush)` (gradient selected from `CommunityTheme` enum)

#### Scenario: Tap a row opens community detail placeholder
- **WHEN** user taps any `CommunityItemRow` in this phase
- **THEN** `AdminCommunitiesListNavEvent.OpenCommunityDetail(communityCode)` emits and the shell currently shows a `toast("Coming soon")` because the iOS community-detail-as-admin chain is documented as removed-dead-code in `screen-inventory.md` §"What is NOT in the Android port"

### Requirement: List renders loading, error, and no-data states
The admin list SHALL render `KornerzLoadingView` (shimmer placeholder showing three `CommunityItemRow` skeletons) when `AdminCommunitiesListState.items is AsyncValue.Loading`, `KornerzErrorState` when `AsyncValue.Error(throwable)` (with a Retry pill button → re-calls `AdminCommunitiesRepository.fetchOwnedByUser()`), and `KornerzEmptyState` when `items is AsyncValue.Success(emptyList)`. Mirrors iOS `shimmerView` + `RetryView` + `NoDataView` patterns in `AdminCommunityListScreen.swift` lines 30–65.

#### Scenario: First load shimmer
- **WHEN** `AdminCommunitiesListViewModel.init` and Firestore has not yet returned
- **THEN** the screen renders the shimmer placeholder

#### Scenario: Fetch error with retry
- **WHEN** `AdminCommunitiesRepository.fetchOwnedByUser()` returns a failure
- **THEN** the screen renders `KornerzErrorState` with a Retry button; tapping Retry re-collects the flow

#### Scenario: Empty list state
- **WHEN** the user is not the creator of any community
- **THEN** the screen renders `KornerzEmptyState` with the "Create your first community" CTA

### Requirement: Pull-to-refresh refetches the list and per-row counts
The admin list SHALL support pull-to-refresh via Material 3 `PullToRefreshBox`. On refresh trigger, the view model SHALL re-call `AdminCommunitiesRepository.fetchOwnedByUser(userCode)`, then per-row re-call `CommunitiesRepository.fetchCount(code, MEMBERS)` + `fetchCount(code, NOOKS)`. Mirrors iOS `UIRefreshControl` + `viewModel.refresh()` chain in `AdminCommunityListController.swift` lines 75–105.

#### Scenario: Pull-to-refresh updates list
- **WHEN** user pulls down on the list and releases
- **THEN** the spinner appears, `AdminCommunitiesListState.isRefreshing = true`, the refetch fires, and on success the spinner disappears + the list updates

### Requirement: Per-row member and nook counts surface real Firestore counts
Each `CommunityItemRow` SHALL display the `memberCount` (count of `CommunityMembers` documents where `communityId == communityCode`) and `nookCount` (count of `Nooks` documents where `communityId == communityCode`). The fetch is via `CommunitiesRepository.fetchCount(code, kind)` and SHALL be cached per-row in `AdminCommunitiesListState.rowCounts: Map<communityCode, Pair<Int, Int>>` to avoid refetching on every recomposition. Mirrors iOS `AdminCommunityListScreen.swift` lines 50–82.

#### Scenario: Row shows correct member count
- **WHEN** the community has 42 members in `CommunityMembers` and 7 nooks in `Nooks`
- **THEN** the row pill shows "Members: 42" and "Nooks: 7"

#### Scenario: Count fetch failure falls back to zero
- **WHEN** `CommunitiesRepository.fetchCount()` fails for a specific row (e.g., network blip)
- **THEN** the row displays "Members: 0" and "Nooks: 0" instead of an error toast (matches iOS behavior — single-row failures don't surface to the user)

## References
- [`docs/architecture.md`](../../../docs/architecture.md) — MVVM + UDF + `AsyncValue<T>` + `Channel<NavEvent>` + Hilt scopes
- `kornerz/Kornerz/HomeVC/CommunityDetailAsAdmin/AdminCommunitiesList/AdminCommunityListController.swift` — iOS source-of-truth for the list composition + ordering
- `kornerz/Kornerz/HomeVC/CommunityDetailAsAdmin/AdminCommunitiesList/AdminCommunityListScreen.swift` — iOS source-of-truth for the list view (shimmer / retry / empty / loaded states)
- `kornerz/Kornerz/HomeVC/CommunityDetailAsAdmin/AdminCommunitiesList/CommunityItemView.swift` — iOS source-of-truth for the row layout
- `kornerz/Kornerz/Backend/Repositories/CommunitiesRepository.swift` — iOS source-of-truth for `fetchOwnedByUser` + `fetchCount` + `fetchMemberCount` + `fetchNookCount`
- `docs/phases.md` — Phase 7 deliverables
