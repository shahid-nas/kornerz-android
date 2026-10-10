## Context

Phase 3 finished with the Kornerz tab populated and `KornerDetailNavEvent.NavigateToNookDetail(nookId)` wired but unresolved — tapping any nook tile inside the Korner detail surfaces a `Coming in Phase 4` snackbar because no standalone nook destination exists. The third tab in the bottom navigation bar (`Nooks`) is still the P0 "Coming in Phase 4" empty-state placeholder. Phase 4 closes that gap by implementing the entire Nooks top-level tab (5.1, 5.2, 5.3) and the `nooks/{nookId}` nook detail destination, then rewiring the Korner detail's nook tiles so the snackbar resolves to a real screen. The iOS source-of-truth lives at `kornerz/SwiftUI/Dashboard/Nooks/` and the nook tiles reused from `kornerz/SwiftUI/Dashboard/Korner/Sections/`. The Android port must reach visual + behavioral parity with iOS so screenshots and walkthroughs match.

The architectural baseline is unchanged from P3: MVVM + Repository + UDF, `AsyncValue<T>` per section, `Channel<NavEvent>` for navigation, Hilt scopes (`@HiltViewModel`, `@Singleton` repositories), `SavedStateHandle` for nav-arg ViewModels, `core/model` for `@Serializable` data, Coil for images, and the convention plugin `kornerz.android.feature.compose.hilt` for the new feature module.

The new addition is one feature module (`feature/nooks`) hosting two top-level destinations (`nooks/list`, `nooks/{nookId}`) reachable from three entry points: the Nooks bottom-nav tab, the Korner detail's `CurrentNookSection`, and the Korner detail's `NooksListSection`. The `NookCard` and `ScheduledNookCard` composables become shared between `feature/nooks` and `feature/kornerz` to eliminate duplicated card chrome.

## Goals / Non-Goals

**Goals:**

- Implement the Nooks tab root screen — a cross-Korner feed partitioned into "Live now" and "Upcoming" sections.
- Implement shared `NookCard` and `ScheduledNookCard` composables used by both the Nooks tab list and the Korner detail nook sections.
- Implement a `nooks/{nookId}` nook detail destination reachable from any nook tile.
- Add a `NooksRepository` (one `fetchNooksFeed()` call that reads every `communities` doc and its `events` subcollection, then partitions live vs scheduled in memory).
- Add a `NookDetail` aggregate to `core/model` composing `Nook` + `Korner`.
- Refactor `feature/kornerz`'s `NookTile` and `CurrentNookView` composables to delegate to the new shared cards so the two surfaces stay visually identical.
- Update `feature/shell` so the Nooks tab hosts `NooksScreen` and resolves `KornerDetailNavEvent.NavigateToNookDetail` to the new `nooks/{nookId}` destination instead of a snackbar.
- Pass the per-phase exit checklist (assembleDevDebug, installDevDebug on API 26 + API 35 emulators, no crashes, lint, screenshots, demo walkthrough, `phase-4-end` tag).

**Non-Goals:**

- Loops tab video playback (P5).
- Profile + Notifications (P6), Create Community + Admin (P7), Premium / IAP (P8), Video Calling (P9), Ranking + Deep Links + Polish (P10).
- Nooks tab search / filter chips (iOS header placeholder stays a static element this phase).
- Member profile destination (P6).
- Join / leave Korner actions (not in iOS live flow).
- Nook live-state streaming via Firestore `snapshotFlow` listeners (iOS uses a one-shot fetch — mirrored this phase; can move to a `Flow` snapshot listener in a follow-up).
- Recording or joining a nook video room (P9).
- RTL / pt-BR localization (English only this phase per `project-brief.md` §11.7).
- New third-party dependencies — Coil, Compose BOM, Hilt, Navigation-Compose, kotlinx-datetime already pinned in P0. ExoPlayer is **not** introduced; nook detail shows a still cover like iOS.

## Decisions

### Decision: One feature module, two destinations (`nooks/list`, `nooks/{nookId}`) reachable from multiple entry points

The Nooks bottom-nav tab hosts a nested `NavHost` with `nooks/list` as its start destination and `nooks/{nookId}` as a per-tab nested route. The same `nooks/{nookId}` destination is also hosted as a nested route inside the Kornerz tab's `NavHost` so the Korner detail's nook tiles can push into it without leaving the tab the user is browsing. This mirrors the iOS coordinator model where `AppCoordinator` can route into `NookDetailView` from either `NooksTabController` or `KornerDetailViewController` and the back-stack preserves the originating tab. The nook detail screen's "back" button uses `NavController.popBackStack()` so each tab sees its own back behavior — matches iOS `NookDetailView.swift`'s reliance on the system's per-tab `NavigationStack`.

**Alternatives considered:**

- Single top-level route for the nook detail with bottom-bar visibility logic that knows about both Nooks and Kornerz tab origins. Rejected: requires the shell to encode per-origin back-stack rules; the nested-NavHost approach is cleaner and matches iOS.
- Tab-agnostic nook detail hosted at the shell root. Rejected: breaks bottom-bar persistence rule from `android-app-shell`.

### Decision: `NookDetailViewModel` keyed by `nookId` via Hilt `SavedStateHandle`

Navigation-Compose hands each route its nav-args through `SavedStateHandle`. We expose a single `@HiltViewModel` constructor that pulls `nookId: String` from the handle and injects `NooksRepository`. The view model fetches `NooksRepository.fetchNookDetail(nookId)` once on `init`, observes a `Result<NookDetail>` mapped into `AsyncValue<NookDetail>`, and emits no further navigation (the screen has a single internal back affordance handled by the `NavController`). This is the same pattern used by `KornerDetailViewModel` in P3 — no new wiring needed.

**Alternatives considered:**

- Pass `nook` (the full `Nook` model) as a JSON nav-arg instead of a `nookId` and rely on `SavedStateHandle` to deserialize. Rejected: iOS always re-fetches via the repository to pick up fresh `isLive` / `startDate` state; mirror that.
- ViewModel keyed by `Map<Class<out ViewModel>, ViewModelProvider.Factory>` with a manual `NookId` wrapper. Rejected: more boilerplate, no benefit over `SavedStateHandle`.

### Decision: One repository call fetches all nooks across all visible Korners

The iOS `NooksTabViewModel.fetchData()` issues N+1 reads (`communities` once + `communities/{id}/events` per Korner). The Android repository keeps that exact N+1 shape but exposes a single `fetchNooksFeed(): Flow<NooksFeed>` that hides the fan-out behind `coroutineScope { ... }` + `awaitAll`, then partitions the merged list into `live: List<Nook>` and `scheduled: List<Nook>`:

- `live` = every `Nook` with `isLive = true`, sorted by `startDate` descending (most-recently-live first; matches iOS ordering).
- `scheduled` = every `Nook` with `isLive = false`, sorted by `startDate` ascending (next-up first; matches iOS ordering).

This mirrors iOS without re-shaping the data model or inventing a new "global nooks" collection.

**Alternatives considered:**

- A single flattened `nooks` Firestore collection group query. Rejected: iOS does not use a collection group; introducing one diverges from the iOS contract and forces a backend migration outside P4's scope.
- Firestore `snapshotFlow` per `events` subcollection for live updates. Rejected: scope creep — iOS does a one-shot fetch; revisit in a follow-up if telemetry shows stale data is a problem.

### Decision: Shared `NookCard` / `ScheduledNookCard` composables live in `feature/nooks` and `feature/kornerz` depends on them

The two card variants ship in `feature/nooks/.../components/` because they are first introduced for the Nooks tab list, and `feature/kornerz` already depends on `feature/nooks` (the new module). This eliminates the `NookTile` duplicate that ships in P3 and guarantees the Korner detail's Current Nook + Nooks list sections render identically to the Nooks tab list. The shared composables take a `NookCardData` projection (id, title, coverUrl, startDate, isLive, parentKornerName, parentKornerThemeColor) so they don't leak Firestore-specific types.

**Alternatives considered:**

- Put the shared composables in `core/ui/components/`. Rejected: they carry nook-specific semantics and would force `core/ui` to know about the `Nook` model, which currently lives in `core/model`.
- Keep them inside `feature/nooks` only and have `feature/kornerz` compose its own tile. Rejected: guaranteed visual drift and double the test surface.

### Decision: No new design tokens or shared components

The Nooks tab, nook cards, and nook detail use only tokens and components already shipped from P0/P2/P3: 8pt grid spacing, Satoshi typography, color palette, `KornerzDiamondImage`, `ShimmerPlaceholder`, `NookTile` (refactored to delegate). No new colors, no new shapes, no new font sizes.

### Decision: Back-stack rule — nook detail pops to the originating tab

Pushing the `nooks/{nookId}` destination from the Kornerz tab uses a nested `NavHost` inside that tab, so the system back press pops the nested route back to `kornerz/{kornerId}`. Pushing the same destination from the Nooks tab uses the Nooks tab's nested `NavHost`, so the back press pops back to `nooks/list`. This matches the iOS coordinator behavior — `NookDetailView` is reused across entry points and the parent's `NavigationStack` handles pop semantics.

## Risks / Trade-offs

- [N+1 reads at scale] → Mitigation: the Nooks tab is expected to be a few dozen Korners each with a handful of events. iOS does the same fan-out; no immediate perf concern. If list size balloons we can introduce a `paging3` `PagingSource` in a follow-up without changing screens.
- [Stale `isLive` between pulls] → iOS fetches once on tab open. Mitigation: phase exit checklist includes manual demo with a nook going live mid-session; if demo feels stale, swap the repo to `snapshotFlow` in a follow-up (no screen changes).
- [Card visual drift between surfaces] → Mitigation: `NookCard` / `ScheduledNookCard` are the single source of truth, and `feature/kornerz`'s `NookTile` + `CurrentNookView` are refactored to delegate. Lint rule + a Compose `@Preview` against the same `NookCardData` fixtures catches drift in PR review.
- [Firestore index missing for `communities.orderBy("name")`] → Mitigation: log a one-time warning at repo init; the list renders with the default `__name__` ordering until the index is provisioned. Same fallback as P3.
- [Members list pagination] → Not in P4 scope.
- [Shared module dependency: `feature/kornerz` depends on `feature/nooks` for shared cards] → Trade-off: introduces a one-way edge between two feature modules. Justified because the cards are nook-specific and `feature/kornerz` is the only second consumer; documented in `feature/kornerz/README` as the dependency rationale. Does not violate arch §2.5 (a feature module may depend on another feature module when that module hosts shared composables for the nooks domain).

## Migration Plan

This is a new feature module + a small refactor of `feature/kornerz`'s nook tiles. Rollout steps:

1. Merge `feature/nooks` module behind the existing P3 Korner detail page. Nooks tab still shows P0 "Coming in Phase 4" placeholder. Korner detail `Coming in Phase 4` snackbar still wired.
2. Enable the Nooks tab `composable` to host `NooksScreen` (list only) — no detail route wired yet.
3. Refactor `feature/kornerz`'s `NookTile` + `CurrentNookView` to delegate to `NookCard` / `ScheduledNookCard` from `feature/nooks`. Visual parity verified by screenshot diff.
4. Add `nooks/{nookId}` nested routes to both the Nooks tab and the Kornerz tab `NavHost`s. Replace the `Coming in Phase 4` snackbar in `KornerDetailNavEvent.NavigateToNookDetail` handler with a real navigation call.
5. Run per-phase exit checklist and tag `phase-4-end`.

No data migrations. No schema changes. No backend rollout. Rollback is `git revert` of the merge commit — the P3 Korner detail snackbar is restored and the Nooks tab reverts to the P0 placeholder.

## Open Questions

- **Nook cover aspect ratio** — iOS uses a 16:9 ratio for nook cover banners and a 1:1 ratio inside the Nooks tab list rows. Will resolve by reading iOS asset references in the spec'd `nook-card` task; if ambiguous, prefer 16:9 inside the nook detail and 4:3 inside the card (most common Android pattern).
- **Empty state copy** — iOS uses "No live nooks" / "No upcoming nooks" but those strings need an extract from the .xcassets / .strings file. Will pull from iOS during implementation; if missing, ship English placeholders and file a follow-up.
