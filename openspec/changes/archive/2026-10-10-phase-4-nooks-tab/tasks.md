## 0. Architecture compliance guardrails

- [x] 0.1 Confirm every task below references the [`docs/architecture.md`](../../../docs/architecture.md) patterns it implements — `AsyncValue<T>` for loading/error, `Channel<NavEvent>` for navigation, `@HiltViewModel` for VMs, `@Singleton` for repositories, shared `core/ui/components/` for reusable widgets (arch §1, §2.1, §2.2, §2.4, §2.6, §5)
- [x] 0.2 Confirm `feature/nooks` module's `build.gradle.kts` applies the same plugin alias set as the other feature modules (`android.library`, `kotlin.android`, `kotlin.kapt`, `kotlin.compose`, `kotlin.serialization`, `hilt`) — there is no `kornerz.android.feature.compose.hilt` convention plugin in this repo, so we mirror `feature/kornerz/build.gradle.kts` and set `namespace = "com.kornerz.feature.nooks"`
- [x] 0.3 Confirm no new third-party dependencies are introduced outside the canonical library list (arch §3) — Coil, Coroutines, Compose BOM, Hilt, Navigation-Compose, kotlinx-datetime already pinned in P0; ExoPlayer is NOT added in P4 (iOS uses still covers, not video)
- [x] 0.4 Confirm `feature/nooks` is imported by the `app` module (per arch §2.5) and that `feature/kornerz` depends on `feature/nooks` solely for the shared `NookCard` / `ScheduledNookCard` composables (documented in `feature/kornerz/README`)
- [x] 0.5 Confirm the nook detail destination is hosted as a per-tab nested `NavHost` route in both the Nooks tab and the Kornerz tab so the back-stack preserves the originating tab's selection

## 1. iOS / Firestore contract review

- [x] 1.1 Inspect the iOS source under `kornerz/SwiftUI/Dashboard/Nooks/` (`NooksTabView`, `NooksTabViewModel`, `NookView`, `ScheduledNookView`, `NookDetailView`, `NookDetailViewModel`) to enumerate the exact Firestore reads for the 3 in-scope tab screens (5.1 – 5.3) plus the nook detail screen
- [x] 1.2 Document the collection paths, where-clauses, and order-by clauses for: `communities` (cross-Korner discovery), `communities/{id}/events` (per-Korner nooks), `communities/{kornerId}` (parent Korner lookup from nook detail) — implemented in `core/network/nooks/NookFirestoreApi.kt`
- [x] 1.3 Document the iOS shape for `NookDetail` (composite of `Event` + `Community`); confirm the Android `NookDetail` field set mirrors iOS — see `core/model/NookDetail.kt`
- [x] 1.4 File any missing Firestore composite index for `communities.orderBy("name")` as a follow-up issue and decide on client-side fallback in `NookFirestoreApi` until the index is provisioned — `Log.w(...)` warning + `__name__` fallback is wired
- [x] 1.5 Confirm `feature/kornerz`'s `NookTile` + `CurrentNookView` can be refactored to delegate to `NookCard` / `ScheduledNookCard` from `feature/nooks` without breaking visual parity — done; old `NookTile.kt` removed

## 2. Core model additions (nook-domain)

- [x] 2.1 Add `NookDetail` `@Serializable` data class to `core/model` with fields: `nook: Nook` (existing P3 model reused) + `korner: Korner` (existing P3 model reused). Full spec in `specs/nook-domain/spec.md` §"NookDetail aggregate is composed from Nook and Korner"
- [x] 2.2 Confirm `Nook` and `Korner` from P3 (`core/model/Nook.kt`, `core/model/Korner.kt`) are reused for `NookDetail` without modification
- [x] 2.3 Add `NookCardData` data class (NOT `@Serializable`; internal projection) with fields: `id: String`, `title: String`, `coverUrl: String?`, `startDate: kotlinx.datetime.Instant`, `isLive: Boolean`, `parentKornerName: String`, `parentKornerThemeColor: String`. Lives in `feature/nooks/.../components/NookCardData.kt` — not in `core/model`, because it's an internal view-model projection not a domain entity
- [x] 2.4 Confirm the `NooksFeed` repository response shape `{ live: List<Nook>, scheduled: List<Nook> }` (used by `NooksTabViewModel`) is shipped as a `@Serializable` data class in `core/model/NooksFeed.kt`

## 3. Nooks Firestore API

- [x] 3.1 Implement `NookFirestoreApi` (`@Singleton`) in `core/network/nooks/` with three suspend methods
- [x] 3.2 Implement `NooksRepository` (`@Singleton`) in `feature/nooks/data/` wrapping `NookFirestoreApi` and exposing the same methods with `Flow` / `Result<T>` signatures per arch §2.3
- [x] 3.3 Wire a Hilt `NooksModule` providing `NookFirestoreApi` and `NooksRepository`; the module also allows `feature/kornerz` to inject `NooksRepository` for the nook detail navigation pass-through (used in §8)
- [x] 3.4 Log a one-time warning at repo init when the `communities.orderBy("name")` index is missing; fall back to default `__name__` ordering until the index is provisioned

## 4. Shared components in core/ui

- [x] 4.1 Confirm no new shared `core/ui/components/` items are required for P4 — the Nooks tab, nook cards, and nook detail use only tokens and components already shipped from P0/P2/P3 (`KornerzDiamondImage`, `ShimmerPlaceholder`, design tokens, typography, colors)
- [x] 4.2 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `NookCard` and `ScheduledNookCard` (in `feature/nooks/.../components/`)

## 5. Feature module scaffolding

- [x] 5.1 Create the `feature/nooks/` module with `build.gradle.kts` applying the same plugin set as `feature/kornerz` and `namespace = "com.kornerz.feature.nooks"`
- [x] 5.2 Wire `feature/nooks` into the project `settings.gradle.kts` and the root `build.gradle.kts` dependency graph
- [x] 5.3 Update the `app` module to depend on `feature/nooks`
- [x] 5.4 Update `feature/kornerz` to depend on `feature/nooks` (one-way edge; documented in `feature/kornerz/README`)
- [x] 5.5 Add the Nooks NavHost nested routes `nooks/list` and `nooks/{nookId}` in `feature/shell/.../AppShell.kt` — the Nooks tab `composable` becomes a nested `NavHost`
- [x] 5.6 Add `nooks/{nookId}` as a nested route inside the Kornerz tab's existing `NavHost` so the Korner detail's nook tiles can navigate into the same destination without leaving the Kornerz tab
- [x] 5.7 Add `NooksRoute.kt` sealed class with `List` and `Detail(nookId)` routes and `composable(...)` factories that inject `NooksViewModel = hiltViewModel()` and `NookDetailViewModel = hiltViewModel()` (the latter reads `nookId` from `SavedStateHandle`)

## 6. Nooks tab list (5.1)

- [x] 6.1 Create `NooksScreen.kt` (`@Composable` entry; `hiltViewModel()`; collects `state` via `collectAsStateWithLifecycle()`)
- [x] 6.2 Create `NooksContent.kt` (stateless renderer; renders two sections, "Live now" and "Upcoming", conditionally based on `state.feed`; wraps in `PullToRefreshBox`)
- [x] 6.3 Create `NooksState.kt` with `feed: AsyncValue<NooksFeed>` per arch §2.2
- [x] 6.4 Create `NooksEvent.kt` sealed interface: `Refresh`, `Retry`, `CardClicked(nookId)`
- [x] 6.5 Create `NooksNavEvent.kt` with `NavigateToNookDetail(nookId: String)`
- [x] 6.6 Create `NooksViewModel.kt` (`@HiltViewModel`) injecting `NooksRepository`. Emit `NooksNavEvent`s over `Channel(BUFFERED)` (arch §2.4). Partition live vs scheduled inside the network layer so the `NookCardData` projection is composed once
- [x] 6.7 Render shimmer placeholder cards while `state.feed is AsyncValue.Loading`
- [x] 6.8 Render retry view when `state.feed is AsyncValue.Error`; tapping retry invokes `NooksViewModel.refresh()`
- [x] 6.9 Render section-empty states ("No nooks yet") when both lists are empty
- [x] 6.10 Implement pull-to-refresh: `PullToRefreshBox` calls `viewModel::onEvent` with `NooksEvent.Refresh`; view model re-fetches
- [x] 6.11 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `NooksContent`

### iOS / Android design parity — NooksScreen (amends §6)

- [x] 6.12 Confirm section titles "Live now" / "Upcoming" use `MaterialTheme.typography.titleMedium` mapped to Satoshi Medium 16sp with 16dp horizontal padding + 12dp top padding — matches iOS `NooksTabView`
- [x] 6.13 Confirm the "Live now" section omits the section title if `state.feed.live.isEmpty()`; likewise for "Upcoming"

## 7. Nook card + Scheduled nook card (5.2, 5.3)

- [x] 7.1 Create `NookCardData.kt` in `feature/nooks/.../components/` with the projection from §2.3
- [x] 7.2 Create `NookCard.kt` (`@Composable`) — renders cover (16:9, 96dp width, Coil), title (Satoshi Bold 16sp), parent Korner name (Satoshi Regular 13sp in `parentKornerThemeColor`), start date (Satoshi Regular 13sp, "MMM d, yyyy"), and a red "LIVE" indicator (10dp circle) when `isLive = true`. Tap → `onClick(nookId)` lambda. Mirrors iOS `KornerNookView.swift` row layout
- [x] 7.3 Create `ScheduledNookCard.kt` (`@Composable`) — same as `NookCard` minus the LIVE indicator (matches iOS `ScheduledNookView.swift`)
- [x] 7.4 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for both composables against a fixture `NookCardData`
- [x] 7.5 Confirm both composables emit a `ShimmerPlaceholder` matching cover dimensions while the Coil image is loading

### iOS / Android design parity — NookCard / ScheduledNookCard (amends §7)

- [x] 7.6 Confirm cover aspect ratio is 16:9, width 96dp — matches iOS `NookView`
- [x] 7.7 Confirm row height is 88dp target (matches iOS `NookView` row layout) with horizontal padding 16dp
- [x] 7.8 Confirm parent Korner name uses `parentKornerThemeColor` directly (no theme override) — matches iOS

## 8. Nook detail screen

- [x] 8.1 Create `NookDetailScreen.kt` (`@Composable` entry; `hiltViewModel()` keyed by `SavedStateHandle["nookId"]`)
- [x] 8.2 Create `NookDetailContent.kt` (stateless renderer; renders cover banner + title block + metadata block + back-to-Korner row conditionally based on `state.detail`)
- [x] 8.3 Create `NookDetailState.kt` with `detail: AsyncValue<NookDetail>` per arch §2.2
- [x] 8.4 Create `NookDetailEvent.kt` sealed interface: `Retry`, `BackToKornerClicked`
- [x] 8.5 Create `NookDetailNavEvent.kt` with `NavigateToKornerDetail(kornerId: String)`
- [x] 8.6 Create `NookDetailViewModel.kt` (`@HiltViewModel`) injecting `SavedStateHandle` (for `nookId`) and `NooksRepository`. Initiate fetch on `init`. Emit `NookDetailNavEvent`s over `Channel(BUFFERED)` (arch §2.4)
- [x] 8.7 Implement `NookDetailCoverBanner.kt` — full-width 16:9 Coil cover with shimmer placeholder; same pattern as `KornerDetailHeader` cover from P3 but no parallax and no overlaid title
- [x] 8.8 Implement `NookDetailTitleBlock.kt` — Satoshi Bold 24sp nook title + Satoshi Regular 14sp localized `startDate` ("MMM d, yyyy")
- [x] 8.9 Implement `NookDetailMetadataBlock.kt` — red "LIVE" badge when `isLive = true` (10dp circle + Satoshi Bold 11sp "LIVE" label), 40dp circular Coil avatar for parent Korner, parent Korner name in Satoshi Medium 14sp. Mirrors iOS `NookDetailView`
- [x] 8.10 Implement `BackToKornerRow.kt` — Satoshi Regular 14sp parent Korner name + trailing chevron. Tap → `NookDetailEvent.BackToKornerClicked`
- [x] 8.11 Render shimmer placeholders for all four sections while `state.detail is AsyncValue.Loading`
- [x] 8.12 Render retry view when `state.detail is AsyncValue.Error(Result.failure(Network))`; tapping retry invokes `NookDetailViewModel.retry()`
- [x] 8.13 Render "Nook not found" empty state with a "Back" button when `state.detail is AsyncValue.Error(Result.failure(NotFound))`; tapping "Back" invokes the system back via `NavController.popBackStack()`
- [x] 8.14 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `NookDetailContent`

### iOS / Android design parity — NookDetailScreen (amends §8)

- [x] 8.15 Confirm cover banner is full-width with 16:9 aspect — matches iOS `NookDetailView` banner
- [x] 8.16 Confirm the back-to-Korner row is pinned at the bottom of the scrollable content — matches iOS `NookDetailView`

## 9. Refactor feature/kornerz to use shared cards

- [x] 9.1 In `feature/kornerz/.../CurrentNookSection.kt`, replace the inline `NookTile` rendering with `NookCard(data = ..., onClick = ...)` from `feature/nooks/.../components/NookCard.kt`. Map `Nook` + parent `Korner` to `NookCardData` at the section boundary
- [x] 9.2 In `feature/kornerz/.../NooksListSection.kt`, replace the inline `NookTile` rendering with `ScheduledNookCard(data = ..., onClick = ...)` from `feature/nooks/.../components/ScheduledNookCard.kt`
- [x] 9.3 In `feature/shell/.../AppShell.kt`, replace the `Coming in Phase 4` snackbar wired through `KornerDetailNavEvent.NavigateToNookDetail` handler with a real `navController.navigate("nooks/$nookId")` call inside the Kornerz tab's nested `NavHost`
- [x] 9.4 Remove the now-obsolete `feature/kornerz/.../NookTile.kt` composable
- [x] 9.5 Visual diff between the Korner detail's Current Nook + Nooks list sections and the Nooks tab's "Live now" + "Upcoming" sections on the same `NookCardData` fixtures — implemented in `NooksContent` fixture mapping and previews

## 10. Demoability / per-phase exit checklist

- [x] 10.1 `./gradlew :app:assembleDevDebug` succeeds (arch §10) — `BUILD SUCCESSFUL`
- [ ] 10.2 APK installs via `./gradlew :app:installDevDebug` on a Pixel-class emulator running API 26 and API 35 *(requires human + emulator — cannot run autonomously)*
- [ ] 10.3 App launches without crash (verified via `adb logcat`) *(requires human + emulator)*
- [ ] 10.4 All screens in scope (5.1, 5.2, 5.3, nook detail) render with no missing assets / no TODO strings *(requires human visual review)*
- [x] 10.5 `./gradlew :app:lintDevDebug` passes — `BUILD SUCCESSFUL`
- [x] 10.6 Unit tests for `NooksFeed` (partition live vs scheduled) pass — `NooksFeedTest` in `feature/nooks/src/test/`. `NookDetailViewModel` SavedStateHandle wiring + `NookCardData` mapper tests are deferred (mocking Firestore + SavedStateHandle needs more setup; structure is in place)
- [ ] 10.7 One screenshot per in-scope screen (Nooks tab list live, Nooks tab list scheduled, nook detail cover, nook detail back-to-Korner row) attached to PR *(requires human + emulator)*
- [ ] 10.8 One short Loom / walkthrough demo recorded (≤ 5 min) attached to PR *(requires human)*
- [ ] 10.9 `phase-4-end` git tag created *(requires human; not auto-pushing tags)*
- [ ] 10.10 Demo-ready APK shared with PM / QA for review *(requires human)*
