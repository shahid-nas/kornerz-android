## Context

Phase 0 delivered a branded Android shell (5-tab `NavHost`, Satoshi font, design tokens, Firebase init). Phase 1 delivered auth (custom-auth Cloud Function client, DataStore session, full 9-step signup wizard, login, password recovery, force-update sheet) so a signed-in user reaches the Home tab — but the Home tab itself is the P0 empty-state placeholder. Every other tab (Kornerz, Nooks, Loops, Klouders) remains an empty placeholder. Phase 2 closes that gap by populating the Home tab with the same five sections the iOS app ships (`SwiftUI/Dashboard/HomeTab/HomeTabView.swift` → `Sections/HomeFeedSection.swift`), making the app presentable to PM/Marketing/Design as the "First Impression" milestone.

The iOS source of truth for every section Composable is `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/Sections/`:
- `HeaderView/HomeHeaderView.swift` (3.2) — avatar diamond + points badge
- `LoopSection/LoopOfDaySectionView.swift` (3.4) — featured loop
- `UpcomingNooks/UpcomingNooksView.swift` (3.7) — scheduled nooks within 7 days
- `YourKornerz/YourKornerzSectionView.swift` (3.8) — user's memberships
- `RecentLoops/RecentLoopsView.swift` (3.6) — recent loops
- `RecentArticles/RecentArticlesView.swift` (3.5) — merged posts + videos feed

Every Android section Composable is a 1:1 port of its iOS counterpart — same layout, spacing, typography, color palette, tap targets, and shimmer behavior.

**Architecture compliance.** This change conforms to [`docs/architecture.md`](../../../docs/architecture.md) — the canonical source of patterns and conventions frozen by P0:
  - **MVVM + Repository + UDF** (arch §1, §2.1): each section has a `<Section>State`, `<Section>Event`, `<Section>ViewModel`, `<Section>Screen`, `<Section>Content` per the §4 folder layout. The Home tab root has its own `HomeViewModel` and orchestrates per-section ViewModels.
  - **`AsyncValue<T>` sealed interface** (arch §2.2): every section exposes `AsyncValue<List<T>>`; Composables render `Loading` / `Success` / `Error` exhaustively via `LoadingView` / content / `ErrorView` with Retry.
  - **`Channel<NavEvent>` side-channel** (arch §2.4): every tap → `HomeViewModel` → `Channel<HomeNavEvent>`; `HomeScreen` collects and routes via `NavController`. No VM touches `NavController` directly.
  - **`Flow<T>` for streams, `suspend fun ... Result<T>` for one-shots** (arch §2.3): `HomeRepository` returns `Flow<List<X>>` for the live memberships stream and `suspend fun ... Result<T>` for one-shot recent-posts / recent-videos fetches.
  - **Hilt scopes** (arch §2.6): `HomeRepository` and `HomeFirestoreApi` are `@Singleton`; all ViewModels are `@HiltViewModel`.
  - **Module boundaries** (arch §2.5): `feature/home` is imported only by `app`; `core/model`, `core/network`, `core/ui`, `core/storage` are imported by `feature/home` and `app`.
  - **Shared `core/ui/components/`** (arch §5): every section uses `KornerzAvatar`, `KornerzLoadingView`, `KornerzErrorState`, and the new shared `KornerzDiamondImage` / `HomeShimmerPlaceholder` — no per-section reimplementation.
  - **Canonical library list** (arch §3): no new dependencies. Coil is already pinned (P0); Lottie is not used by the Home tab; Material 3 `PullToRefreshBox` is used for pull-to-refresh.
  - **Clean-code rules** (arch §7): no business logic in Composables, no `var` in ViewModels, no `!!`, no magic numbers (every spacing/color/duration goes through `core/ui/theme/`), no `companion object` constants.
  - **Convention plugin** (arch §9): `feature/home` applies `kornerz.android.feature.compose.hilt` only; namespace `com.kornerz.feature.home`.

## Goals / Non-Goals

**Goals:**
- Render the Home tab with the same five (or fewer, if user has no memberships) sections the iOS app ships, populated from real Firestore data, in the same order, with the same visual treatment.
- Provide a single `HomeRepository` that orchestrates the per-section Firestore reads and a single `HomeViewModel` that composes the per-section `AsyncValue`s into one `HomeState`.
- 1:1 design parity with iOS — every section Composable matches the iOS layout, spacing, typography, color palette (`KornerzCardPalette`), avatar shape (`DiamondImageView` → `KornerzDiamondImage`), and shimmer treatment.
- Pull-to-refresh re-runs every section fetch in parallel.
- Map every tap → `Channel<HomeNavEvent>` per arch §2.4; until P3–P6 ship their destinations, taps resolve to a "Coming in Phase N" snackbar (non-blocking placeholder, not a crash).
- Add two new shared components (`KornerzDiamondImage`, `HomeShimmerPlaceholder`) to `core/ui/components/` per arch §5 so other tabs reuse them.
- Apply design tokens, Satoshi font, dark/light themes, and RTL-safe layouts to every new Composable; preview every section under `en` light/dark and `en-rXA` pseudo-locale per arch §5.

**Non-Goals:**
- Implementation of the destinations (Profile, Loop detail, Event detail, Korner detail, Post/Video/Forum detail) — those ship in P3, P5, P6, P7. P2 Home emits the nav events and resolves them to a placeholder snackbar.
- Video playback inside the Home carousels — P5 (Loops tab) handles loop detail playback; P2 only renders thumbnails.
- Full `Post`, `Video`, `Forum` model definitions — Home only consumes the public surface (id, title, content, thumbnail, publisher). Full definitions land in P3 / P5 with their owning tabs.
- Forum data in the Recent articles merge — iOS includes forums, but the Android `Forum` model is not yet defined (deferred to a later phase per `phases.md` "Out of scope"). Home merges posts + videos only.
- Premium gating — `AppHomeViewController.showSubscriptionScreen()` is an iOS side-effect that is not replicated in P2; it ships with P8.
- iOS legacy sections (`RegisterToAppCell`, `StartNewConversationCell`, `CommunitiesListingCell`, `MostReadArticlesCell`, `MostViewedVideosCell`) — confirmed out of scope per `docs/screen-inventory.md` "What is NOT in the Android port" and the 2026-10-07 stakeholder review.
- Karlo's gamification-leaderboard bar (Klouders tab content) — P10 only. The Home tab points badge is read-only.
- Any content for Kornerz, Nooks, Loops, Klouders tabs — those stay on the P0 placeholder until their respective phases.
- Localization beyond English (per `project-brief.md` §11.7); layouts are RTL-safe but copy is en-only.

## Decisions

### 1. One HomeViewModel + one HomeRepository, per-section sub-ViewModels
`HomeViewModel` (`@HiltViewModel`) composes the section-level state by injecting each section's `ViewModel` (`HomeHeaderViewModel`, `LoopOfDayViewModel`, `UpcomingNooksViewModel`, `YourKornerzViewModel`, `RecentLoopsViewModel`, `RecentArticlesViewModel`) and merging their `StateFlow<AsyncValue<…>>` into a single `StateFlow<HomeState>` (arch §2.1 — `MutableStateFlow` is the only mutable state). `HomeRepository` (`@Singleton`) is the single entry point for every Firestore read and delegates to small per-section repository functions. **Why:** keeps the wiring simple (one VM ↔ one repository per screen), and matches arch §2.5 (one feature module per screen). **Alternatives:** a god `HomeViewModel` with all fetch logic inline — rejected because every section already has its own VM contract in iOS and duplicating it under one god VM makes the file huge.

### 2. Pull-to-refresh via Material 3 `PullToRefreshBox` (Compose 1.4+)
The Home tab's `LazyColumn` is wrapped in Material 3's `PullToRefreshBox`. While the box is refreshing, `HomeViewModel.refresh()` re-invokes every section fetch in parallel via `coroutineScope { … }.launch { … }`. Each section's `AsyncValue` transitions back to `Loading` independently. **Why:** matches arch §3 (no new dependencies), matches iOS `.refreshable` behavior 1:1, and the existing Compose BOM already includes M3 1.3+ which ships `PullToRefreshBox`. **Alternatives:** Accompanist swipe-refresh — rejected; Accompanist is not in the canonical library list (arch §3) and M3's pull-to-refresh is the modern equivalent.

### 3. Firestore queries mirror iOS `FireStoreServiceManager` reads
Every section repository function reads the same Firestore collection the iOS `FireStoreServiceManager.shared` / repository calls read:
- Your Kornerz: `communities` where `array-contains` user id (or `memberships/{userId}/communities` subcollection — confirmed via iOS `AppHomeViewModel.fetchCommunities` reading `communityRepository.fetchCommunities(after: communitySnapshot)` against the shared `kornerz-72fba` project).
- Loop of the day: `loops` where `featured == true` order by `createdAt` desc limit 1 (mirrors iOS `LoopOfDaySectionViewModel`).
- Upcoming nooks: `events` where `communityId in userCommunityIds` AND `startDate in next 7 days` (mirrors `getOpenEventsListForNextSevenDaysForCommunities`).
- Recent loops: `loops` where `publisherId == userId` order by `createdAt` desc limit 10 (mirrors iOS `RecentLoopsViewModel`).
- Recent articles (merged): `articles` order by `publishTimeStamp` desc limit 10 + `videos` order by `dateTime` desc limit 10 → merged by date (mirrors `RecentArticlesViewModel.fetchData`).

**Why:** zero new backend work, identical security rules, identical user experience. **Alternatives:** Cloud Function aggregation — rejected; iOS reads directly from Firestore and Phase 2 mirrors that.

### 4. RecentItem sum-type with three cases (post, video, forum)
`RecentItem` is a `sealed interface` mirroring iOS `RecentArticlesViewModel.RecentItem` (`case .post(Post, User)`, `case .video(Video, User)`, `case .forum(Forum, User)`). Forum case is left as `TBD` in P2 (full Forum model ships later); the merged feed for P2 contains only `.post` and `.video` items. The Composable renders each case via an exhaustive `when`. **Why:** future-proofs the feed so when P5 ships forum detail, no API change is needed. **Alternatives:** a single `RecentArticle` data class — rejected because it loses the per-type UI hints (post vs. video vs. forum) that iOS uses.

### 5. Diamond avatar via shared `KornerzDiamondImage` (1:1 port of iOS `DiamondImageView`)
A new shared component in `core/ui/components/` (`KornerzDiamondImage.kt`) renders any image clipped into a 45°-rotated rounded square. It accepts `imageUrl: String?`, `fallbackAsset: String?`, `size: Dp`, `cornerRadius: Dp`, `Modifier` last (arch §4). The clip is implemented with `Modifier.graphicsLayer { rotationZ = 45f }` + a `clip(RoundedCornerShape(cornerRadius))`. **Why:** matches iOS `DiamondImageView` (the iOS view uses `RoundedRectangle(radius: cornerRadius).rotation(45°)`), is reused by the Home header AND the Kornerz tiles, and lives in `core/ui` so other tabs can consume it. **Alternatives:** per-Composable clip — rejected; would scatter the iOS-shape logic across every screen that displays an avatar.

### 6. Placeholder destinations resolve via snackbar until owning phases ship
Every `HomeNavEvent` is collected by `HomeScreen`, which calls a single `resolveNav(event: HomeNavEvent)` extension on `NavController` that maps events to "Coming in Phase N" snackbar messages:
- `NavigateToProfile` → P6
- `NavigateToLoopDetail` → P5
- `NavigateToEventDetail` → P5 (Nooks/Loops share P5)
- `NavigateToKornerDetail` → P3
- `NavigateToRecentItemDetail` → P5 / P7 (depending on item type)

The mapping lives in `feature/home/nav/HomeNavResolver.kt` and is the single place to update when a destination phase ships. **Why:** keeps P2 demoable (taps do not crash), centralizes the future wiring, and gives the demo a clear "look, the Home tab navigates" message. **Alternatives:** a no-op `NavController.navigate("coming_soon")` route — rejected; navigating pushes a screen that has nothing to show; a snackbar keeps the user on the Home tab.

### 7. Hilt modules + kotlinx-serialization for new DTOs
A new Hilt module `HomeModule` in `feature/home/di/` provides `HomeRepository` as `@Singleton`. All new model types (`Community`, `Loop`, `Article`, `Nook`, `RecentItem`) are `@Serializable` data classes per arch §3 (kotlinx-serialization). `HomeFirestoreApi` wraps Firestore with the same Kotlin idioms used in P1's `FirestoreAuthApi`. **Why:** consistent with P0 + P1 conventions, no reflection, no manual JSON.

### 8. Coil 2.x for image loading with shimmer placeholders
Image loading uses Coil's `AsyncImage` with a `placeholder` Painter from `HomeShimmerPlaceholder` and an `error` Painter from `KornerzAvatar.fallback`. Image requests are cached per-URL. **Why:** Coil is already pinned in P0 and arch §3; no new dependency. **Alternatives:** Glide — rejected; not in arch §3.

### 9. Section-level `LaunchedEffect` triggers initial fetch
Each section ViewModel exposes a `Section` Composable that triggers `LaunchedEffect(Unit) { viewModel.fetchData() }` on first composition. `HomeViewModel.refresh()` simply re-invokes each section's `fetchData()`. **Why:** mirrors iOS `HomeTabView.swift` `.onAppear { viewModel.fetchData() }` and the per-section `.onAppear` patterns; arch §2.1 leaves the rule for fetch triggering to per-screen patterns. **Alternatives:** centralizing all fetches in `HomeViewModel.init` — rejected; couples section lifecycles to the root VM.

### 10. Empty sections hide, never render placeholders
Every section Composable returns `Unit` (renders nothing) when its `AsyncValue` is `Success` with an empty list. This mirrors the iOS `updateCellToDisplay` logic: `if viewModel.events.count > 0 { cellToDisplay.append(.events) }`. **Why:** matches iOS exactly; no empty-state placeholders inside the feed composer. **Alternatives:** per-section empty states — rejected; the iOS reference hides empty sections and P2 must match.

### 11. RTL-safe layouts verified with `en-rXA` pseudo-locale preview
Every new Composable is previewed under `LayoutDirection.Ltr` AND `Rtl`. Horizontal padding/sizing uses `Modifier.padding(horizontal = Spacing.space16)` not `Modifier.padding(start = …)` so RTL mirrors naturally. **Why:** matches arch §5 and project-brief §11.7; even though copy is en-only, RTL safety is required.

### 12. New `core/model` types are minimal, additive
`Community`, `Loop`, `Article`, `Nook`, `RecentItem` are added with only the fields the Home tab reads (e.g., `Community` exposes `id`, `name`, `themeColor`, `coverUrl`, `isMember`). Full `Community` (with admin list, member count, ban flags, etc.) is deferred to P3's owning change. **Why:** avoids locking in fields we don't yet need; P3 will widen the type in its own change per arch §11 (amendments require a new OpenSpec change). **Alternatives:** port the full iOS `Community` model — rejected; spec drift risk and pulls in fields unrelated to Home.

## Risks / Trade-offs

- [Firestore query drift between iOS and Android] → Mitigation: the first task in P2 (Task §1) is a contract-review subtask that enumerates every iOS `FireStoreServiceManager` call used by the five Home sections and pins the Android equivalent in `core/network/home/HomeFirestoreApi.kt`. Any missing index surfaces here, before code is written.
- [Missing composite Firestore indexes for upcoming nooks / your Kornerz] → Mitigation: P2 ships a follow-up issue for any required index and falls back to client-side filtering if the index is not yet provisioned (with a `Timber.w("client-side filter")` log line for observability).
- [Loop detail / Korner detail / Profile destinations do not exist in P2] → Mitigation: every tap resolves to a non-fatal snackbar via `HomeNavResolver`; no crash. When the owning phase ships, the resolver is updated in that phase's change.
- [Diamond clip performance on low-end devices] → Mitigation: `KornerzDiamondImage` uses `Modifier.graphicsLayer { rotationZ = 45f }` + `clipToBounds()` rather than `Modifier.drawWithCache` to keep the cost low. Verified by smoke-testing on a Pixel-class API 26 emulator in the P2 exit checklist.
- [Coil shimmer placeholder jitter] → Mitigation: `HomeShimmerPlaceholder` uses a fixed-size `Box` so the shimmer does not cause layout shifts while the image loads.
- [Six concurrent Firestore reads on first composition] → Mitigation: each section's fetch is independent; `HomeViewModel` does NOT block; the user sees shimmer per section as it loads. Worst case = full-screen shimmer for ~1 second on a cold start, matching iOS performance.
- [Recent articles merges two repositories] → Mitigation: P2 only includes `.post` and `.video` items in the merge (no forums yet); when `Forum` ships later, the merge widens via a follow-up change.
- [RTL regressions in six new Composables] → Mitigation: every new Composable has a `@Preview` for `en` and `en-rXA`; the visual diff is captured in the PR screenshots.

## Migration Plan

P0 and P1 already ship; P2 is additive — no destructive changes.

1. Merge `feature/home`, the `core/model`/`core/network` additions, the `feature/shell` Home-tab routing update, and the `core/ui/components/` additions behind the existing CI gate.
2. Build `assembleDevDebug`, install on Pixel API 26 and API 35 emulators; verify:
   - Fresh install → sign up → Home tab renders five sections with real data.
   - Each section shows the iOS-matching layout, colors, typography, and avatar shape.
   - Tap each section → snackbar (not a crash).
   - Pull-to-refresh re-runs every fetch.
   - Sign out → Login → sign back in → Home tab renders again (session restored).
3. Tag `phase-2-end`.
4. Rollback: revert the merge commit. P1 still builds; the Home tab falls back to the P0 placeholder, and the other tabs are unaffected.

## References

- [`docs/architecture.md`](../../../docs/architecture.md) — canonical patterns this change conforms to (MVVM + UDF, `AsyncValue<T>`, `Channel<NavEvent>`, Hilt, module boundaries, shared components, library list).
- [`docs/phases.md`](../docs/phases.md) §"Phase 2 — Home Tab & Tab Shell" — 16 live screens + "First Impression" demoable claim.
- [`docs/screen-inventory.md`](../docs/screen-inventory.md) — 3.1–3.9 Home tab inventory.
- `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/HomeTabView.swift` — iOS source-of-truth for the Home tab root.
- `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/HomeTabViewModel.swift` — iOS source-of-truth for the per-section composition.
- `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/Sections/` — iOS source-of-truth for every section Composable.
- `kornerz/Kornerz/HomeVC/AppHomeVC/RegisterToAppCell/AppHomeViewModel.swift` — iOS Firestore read call sites.
- `kornerz/Kornerz/Components/DiamondImageView.swift` — iOS source-of-truth for the rotated-rectangle avatar.
- `kornerz/Kornerz/Helper/Config.swift` `AppColor` — iOS color tokens ported by `design-system`.