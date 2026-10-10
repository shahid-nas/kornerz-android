# nook-detail Specification

## Purpose
The `nooks/{nookId}` destination is reached when a user taps any nook tile — from the Nooks tab, from a Korner detail's `CurrentNookSection`, or from a Korner detail's `NooksListSection`. It is the Android port of iOS `SwiftUI/Dashboard/Nooks/NookDetailView.swift` and ships in Phase 4.

## References
- [`docs/architecture.md`](../../../docs/architecture.md) — MVVM + Repository + UDF, `AsyncValue<T>`, `Channel<NavEvent>`, Hilt scopes, `SavedStateHandle`-keyed ViewModels.
- [`docs/phases.md`](../../../docs/phases.md) Phase 4 — codename *Nooks*.
- `openspec/changes/phase-4-nooks-tab/proposal.md` — capability rationale.
- `openspec/changes/phase-4-nooks-tab/design.md` — nested NavHost decisions.

## Requirements

### Requirement: Nook detail screen renders nook content from SavedStateHandle id
The nook detail screen, hosted at `feature/nooks/.../NookDetailScreen.kt`, SHALL pull the `nookId: String` nav-arg from `SavedStateHandle` via its `@HiltViewModel` constructor, fetch `NooksRepository.fetchNookDetail(nookId)` on `init`, expose the result as `NookDetailState.detail: AsyncValue<NookDetail>` (an aggregate of `Nook` + `Korner`), and render the screen sections based on the loaded aggregate. Mirrors iOS `NookDetailView.swift` + `NookDetailViewModel.swift`.

#### Scenario: User opens nook detail from the Nooks tab
- **WHEN** the user taps a `NookCard` in the Nooks tab and the nested NavHost pushes `nooks/n1`
- **THEN** `NookDetailViewModel` resolves `SavedStateHandle["nookId"] = "n1"`
- **AND** the view model fetches `NooksRepository.fetchNookDetail("n1")`
- **AND** the content area renders the nook cover banner, nook title, nook metadata block, and back-to-Korner row populated from the resolved `NookDetail`

#### Scenario: User opens nook detail from Korner detail
- **WHEN** the user taps a `NookTile` inside `korner-detail`'s `CurrentNookSection` and the Kornerz tab's nested NavHost pushes `nooks/n1`
- **THEN** the same destination renders the same screen content
- **AND** the back affordance returns to `kornerz/{kornerId}` (not `nooks/list`)

### Requirement: Nook detail screen renders four sections
The nook detail screen SHALL render the following sections, mirroring iOS `NookDetailView.swift`:
1. A 16:9 cover banner at the top (Coil-loaded with shimmer placeholder, full-width).
2. A title block containing the nook title in Satoshi Bold 24sp and the localized `startDate` in Satoshi Regular 14sp below.
3. A metadata block containing: a "LIVE" badge (only when `nook.isLive = true`), the parent Korner cover (40dp circular avatar), and the parent Korner name in Satoshi Medium 14sp.
4. A back-to-Korner row at the bottom: a single tappable row showing the parent Korner name in Satoshi Regular 14sp with a trailing chevron. Tapping the row SHALL emit `NookDetailNavEvent.NavigateToKornerDetail(parentKornerId)` which routes to `kornerz/{parentKornerId}` inside the originating tab's nested `NavHost` (matches iOS `NookDetailView.swift`'s back-to-korner affordance).

#### Scenario: Live nook renders the LIVE badge
- **WHEN** the nook detail screen renders for a `NookDetail(nook = Nook(isLive = true, ...), korner = ...)`
- **THEN** the metadata block shows a red "LIVE" badge (10dp diameter circle + Satoshi Bold 11sp "LIVE" label) before the parent Korner name

#### Scenario: Scheduled nook omits the LIVE badge
- **WHEN** the nook detail screen renders for a `NookDetail(nook = Nook(isLive = false, ...), korner = ...)`
- **THEN** no "LIVE" badge appears in the metadata block

#### Scenario: Tap on back-to-Korner row navigates to the parent Korner
- **WHEN** the user taps the back-to-Korner row at the bottom of the nook detail screen
- **THEN** the originating tab's nested NavHost pushes `kornerz/{parentKornerId}`
- **AND** the parent Korner detail screen renders

### Requirement: Nook detail handles loading, error, and empty states
The nook detail screen SHALL render a shimmer placeholder cover + title while `NookDetailState.detail is AsyncValue.Loading`. The screen SHALL render a centered retry view with a "Try again" button when `NookDetailState.detail is AsyncValue.Error`. The screen SHALL render a centered "Nook not found" empty state (with a back button) when the repository resolves to `Result.failure(NotFound)`.

#### Scenario: Loading state renders shimmer placeholders
- **WHEN** `NookDetailState.detail is AsyncValue.Loading`
- **THEN** the cover area renders a `ShimmerPlaceholder`
- **AND** the title block renders a Satoshi Bold 24sp shimmer row + a Satoshi Regular 14sp shimmer row
- **AND** the metadata block renders a 40dp circular shimmer avatar + a Satoshi Regular 14sp shimmer row
- **AND** no content copy is shown

#### Scenario: Error state renders a retry view
- **WHEN** `NooksRepository.fetchNookDetail(nookId)` resolves to an error
- **THEN** the screen renders a retry view with the Kornerz brand mark, error copy, and a "Try again" button
- **AND** tapping "Try again" invokes `NookDetailViewModel.retry()` which re-runs the fetch

#### Scenario: Not-found state renders an empty state
- **WHEN** `NooksRepository.fetchNookDetail(nookId)` resolves to `NotFound`
- **THEN** the screen renders a centered "Nook not found" empty state with a "Back" button
- **AND** tapping "Back" invokes the system back via `NavController.popBackStack()`