## Context

Phase 2 finished with the Home tab populated and a working `HomeNavEvent.KornerDetail(kornerId)` navigation hook that currently surfaces a `Coming in Phase 3` snackbar because no destination exists. Phase 3 implements that destination plus the entire Kornerz tab — the discovery surface where a signed-in user browses all communities they can see, drills into one, and inspects its cover, about copy, current live nook, scheduled nooks list, and members list. The iOS source-of-truth lives at `kornerz/SwiftUI/Dashboard/Korner/` and `kornerz/Model/Community.swift`. The Android port must reach visual + behavioral parity with iOS so screenshots and walkthroughs match.

The architectural baseline is unchanged from P2: MVVM + Repository + UDF, `AsyncValue<T>` per section, `Channel<NavEvent>` for navigation, Hilt scopes (`@HiltViewModel`, `@Singleton` repositories), `SavedStateHandle` for nav-arg ViewModels, `core/model` for `@Serializable` data, Coil for images, and the convention plugin `kornerz.android.feature.compose.hilt` for the new feature module.

Two screens (Kornerz tab list, Korner detail) with a nested back-stack inside the Kornerz tab is the new addition. The Home carousel nav wiring is a small touch-up but not a refactor.

## Goals / Non-Goals

**Goals:**

- Implement the Kornerz tab root screen (browse all `Korner` items in a `LazyColumn`).
- Implement the Korner detail screen (cover + about + current nook + nooks list + members).
- Add a `KornerzRepository` that reads from the same Firestore paths as iOS.
- Add `Korner` + `KornerDetail` data models in `core/model`.
- Wire the Home tab's `HomeNavEvent.KornerDetail` and the shared `KornerzCarousel.onItemClick` to the new `kornerz/{id}` destination.
- Replace the P0 Kornerz tab placeholder in `feature/shell` with the populated screen.
- Pass the per-phase exit checklist (assembleDevDebug, installDevDebug on API 26 + API 35 emulators, no crashes, lint, screenshots, demo walkthrough, `phase-3-end` tag).

**Non-Goals:**

- Video calling from a Korner (P9).
- Create-community wizard and admin controls (P7).
- Search / filter chips on the Kornerz tab (iOS header placeholder stays a static element this phase).
- Nook detail destination (P4).
- Member profile destination (P6).
- Join / leave Korner actions (not in iOS live flow).
- RTL / pt-BR localization (English only this phase per `project-brief.md` §11.7).
- New third-party dependencies — Coil, Compose BOM, Hilt, Navigation-Compose, kotlinx-datetime already pinned in P0.

## Decisions

### Decision: One feature module, one nested NavHost inside the Kornerz tab

The Kornerz tab hosts two destinations (`kornerz/list` and `kornerz/{id}`). Following arch §2.5 the tab's `composable` block in the shell's `NavHost` becomes a nested `NavHost` rather than a flat top-level route. This keeps the bottom-bar visibility rule (bottom bar persists within a tab) satisfied without any custom logic.

**Alternatives considered:**

- Flat top-level routes for `kornerz` and `kornerz/{id}`. Rejected: would require bottom-bar visibility logic to know about Korner routes.
- Single Composable that renders both list and detail with internal state. Rejected: doesn't match the iOS coordinator model and breaks `SavedStateHandle["kornerId"]` deep-link plumbing.

### Decision: `KornerDetailViewModel` keyed by `kornerId` via Hilt `SavedStateHandle`

Navigation-Compose hands each route its nav-args through `SavedStateHandle`. We expose a single `@HiltViewModel` constructor that pulls `kornerId: String` from the handle and injects `KornerzRepository`. This mirrors iOS's coordinator pattern (`AppCoordinator.selectedKorner` is the analog) and gives us free deep-link support for P10.

**Alternatives considered:**

- ViewModel keyed by `Map<Class<out ViewModel>, ViewModelProvider.Factory>` with a manual `KornerId` wrapper. Rejected: more boilerplate, no benefit over `SavedStateHandle`.
- Singleton `KornerzRepository.observeSelectedKorner()` with a `StateFlow`. Rejected: doesn't match iOS model and forces global mutable state.

### Decision: Reuse the shared `KornerzCarousel` and `KornerzDiamondImage` composables

`home-kornerz-carousel` ships `KornerzCarousel` (P2), and `design-system` ships `KornerzDiamondImage` (P2). The Kornerz tab does not introduce a competing carousel component. If the tab root needs a vertical list (it does), it composes a `LazyColumn` of `KornerRowTile` instances (new this phase) instead of a horizontal carousel — the iOS source uses a list, not a carousel, for the Kornerz tab root.

**Alternatives considered:**

- Add a `VerticalKornerzCarousel` shared component. Rejected: only one caller; over-abstraction.
- Reuse `LazyRow` for the tab root. Rejected: doesn't match iOS vertical list.

### Decision: Nook + Member detail destinations are stubbed snackbars

Following the P2 pattern, `korner-detail` composables emit `KornerDetailNavEvent.NavigateToNookDetail(nookId)` and `KornerDetailNavEvent.NavigateToMemberProfile(userId)`. These resolve at the shell to snackbars (`Coming in Phase 4` for nooks, `Coming in Phase 6` for members). This matches the iOS flow exactly — `KornerDetailView.swift` already routes these to their respective coordinators which are not in P3's scope.

**Alternatives considered:**

- Implement nook + member screens now. Rejected: not in this phase's exit criteria; would couple P3 to P4 / P6.

### Decision: KornerzRepository reads from the same Firestore paths as iOS

Repository methods:

- `fetchAllKornerz(): Flow<List<Korner>>` — `firestore.collection("communities").orderBy("name").get()`.
- `fetchKornerDetail(kornerId: String): Flow<KornerDetail>` — three parallel reads merged: `communities/{id}`, `communities/{id}/events`, `communities/{id}/members` (or `users/{uid}/memberships` where `uid == userId`, per iOS `KornerDetailViewModel.swift`).

The `membership` filter is intentionally **not** applied here (unlike P2's `home-your-kornerz` which filters to joined communities). The Kornerz tab is a discovery surface and mirrors `KornerzViewModel.fetchData()` which returns the full unfiltered list.

**Alternatives considered:**

- Use a `paging3` PagingSource for both methods. Rejected: list is small (<100 rows) and PagingSource adds significant boilerplate with no current benefit; revisit when list size grows.
- Cache responses in Room. Rejected: not in arch §3 for P3; follow-up issue if perf needs it.

### Decision: No new design tokens

The Kornerz tab and detail use only tokens that already ship in `design-system` from P0: 8pt grid spacing, Satoshi Radius typography, color palette, `KornerzDiamondImage`, `ShimmerPlaceholder`. No new colors, no new shapes.

## Risks / Trade-offs

- [Firestore index missing for `communities.orderBy("name")`] → Mitigation: file a follow-up issue; render the list with the default `__name__` ordering until the index is provisioned, and log a one-time warning.
- [Kornerz tab grows beyond 100 rows] → Mitigation: not a P3 concern; revisit with `paging3` when telemetry shows list size warrants it.
- [iOS `KornerDetailViewModel` runs three reads sequentially with cancellation semantics] → Mitigation: Android implementation runs the three reads concurrently with `coroutineScope { async { ... } }` and `awaitAll`. Behavior is equivalent for the success path; cancellation on screen exit is preserved by `viewModelScope` lifetime.
- [Cover image fetch for a Korner the user has just opened can be slow if the cover is a freshly uploaded Firebase Storage object] → Mitigation: Coil 2.x already uses an in-memory + disk cache; shimmer placeholder covers the loading window. No further work.
- [Members list pagination] → iOS limits to first 50 with a "View all" affordance in `KornerDetailView.swift`. Android mirrors that by capping at 50 in `KornerzRepository.fetchKornerDetail`; a follow-up phase can add real pagination if telemetry demands.

## Migration Plan

This is a new feature module — no migrations needed. Rollout steps:

1. Merge `feature/kornerz` module behind the existing P2 Home tab. Kornerz tab still shows P0 placeholder.
2. Enable the Kornerz tab `composable` to host `KornerzScreen` (list only) — no detail route wired yet. Existing "Your Kornerz" Home carousel still emits `Coming in Phase 3` snackbar.
3. Wire `kornerz/{id}` route + `KornerDetailScreen`. Update `HomeNavEvent.KornerDetail` handler in the shell to navigate to the new route. Verify deep-link from Home to Korner detail.
4. Lint, screenshot every in-scope screen, record demo walkthrough, tag `phase-3-end`.

Rollback: revert the commit that enables step 3; the Kornerz tab reverts to P0 placeholder and the Home carousel reverts to `Coming in Phase 3` snackbar. No data migration, no schema change.

## Open Questions

1. Should the Kornerz tab root show a search affordance now (placeholder) so design parity with a future search feature is preserved, or is "header placeholder stays static" the right call? Decision: stay static this phase, follow-up issue.
2. Are members capped at 50 in iOS, or is there pagination logic we missed? Decision: cap at 50 with a follow-up issue for real pagination.
3. Should we pre-warm Coil with the top-3 Kornerz cover URLs when the Kornerz tab is first selected? Decision: no pre-warm this phase — iOS does not pre-warm and Coil's request dispatcher already prioritizes visible tiles.