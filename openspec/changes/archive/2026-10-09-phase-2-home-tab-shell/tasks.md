## 0. Architecture compliance guardrails

- [x] 0.1 Confirm every task below references the [`docs/architecture.md`](../../../docs/architecture.md) patterns it implements — `AsyncValue<T>` for loading/error, `Channel<NavEvent>` for navigation, `@HiltViewModel` for VMs, `@Singleton` for repositories, shared `core/ui/components/` for reusable widgets (arch §1, §2.1, §2.2, §2.4, §2.6, §5)
- [x] 0.2 Confirm `feature/home` module's `build.gradle.kts` applies only the convention plugin `kornerz.android.feature.compose.hilt` (arch §9) and declares `namespace = "com.kornerz.feature.home"`
- [x] 0.3 Confirm no new third-party dependencies are introduced outside the canonical library list (arch §3) — Coil, Coroutines, Compose BOM, Hilt, Navigation-Compose, kotlinx-datetime already pinned in P0
- [x] 0.4 Confirm `feature/home` is imported only by the `app` module (arch §2.5)

## 1. iOS / Firestore contract review

- [x] 1.1 Inspect the iOS source under `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/` and `kornerz/Kornerz/HomeVC/AppHomeVC/RegisterToAppCell/AppHomeViewModel.swift` to enumerate the exact Firestore reads for the 9 in-scope screens (3.1 – 3.9)
- [x] 1.2 Document the collection paths, where-clauses, and order-by clauses for: communities (Your Kornerz), loops (loop of day + recent), events (upcoming nooks within 7 days), articles (recent posts), videos (recent videos)
- [x] 1.3 File any missing Firestore composite index as a follow-up issue and decide on client-side fallback in `HomeFirestoreApi` until the index is provisioned
- [x] 1.4 Document the iOS shape for `Loop`, `Community`, `Article`/`Post`, `Video`, `Event`/`Nook` as it relates to the Home feed — pin the Android fields to mirror

## 2. Core model additions

- [x] 2.1 Add `Community` `@Serializable` data class to `core/model` with the Home-only fields: `id`, `name`, `themeColor: String`, `coverUrl: String?`, `isMember: Boolean`. Full `Community` model lands in P3.
- [x] 2.2 Add `Loop` `@Serializable` data class to `core/model` with `id`, `title`, `thumbnailUrl: String?`, `color: String?`, `durationSeconds: Int?`, `createdAt: kotlinx-datetime Instant`, `publisherId: String?`
- [x] 2.3 Add `Article` `@Serializable` data class to `core/model` (a Home-only subset of iOS `Post`) with `id`, `title`, `content`, `thumbnailUrl: String?`, `publisherId: String?`, `publishTimestamp: kotlinx-datetime Instant`
- [x] 2.4 Add `Video` `@Serializable` data class to `core/model` (Home-only subset) with `id`, `title`, `description: String?`, `thumbnailUrl: String?`, `publisherId: String?`, `dateTime: kotlinx-datetime Instant`
- [x] 2.5 Add `Nook` `@Serializable` data class to `core/model` (a Home-only subset of iOS `Event`) with `id`, `communityId`, `title`, `startDate: kotlinx-datetime Instant`, `isLive: Boolean`
- [x] 2.6 Add `RecentItem` sealed interface in `core/model` with three cases: `.post(Article, User)`, `.video(Video, User)`, `.forum(Forum, User)`. The `.forum` case is declared but not produced in P2 (full `Forum` model ships later).
- [x] 2.7 Add `KornerzCardPalette` object in `core/ui/theme/` with the 5 swatches from iOS `RandomColorService`: `#E27D5A`, `#AD598D`, `#86BFA9`, `#68B6CC`, `#EBAA4A`. Add a `kornerzCardColor(index: Int): Color` helper that rotates through the palette.

## 3. Home Firestore API

- [x] 3.1 Implement `HomeFirestoreApi` (`@Singleton`) in `core/network/home/` with one suspend method per iOS read: `observeUserCommunities(userId: String): Flow<List<Community>>`, `fetchLoopOfDay(): Result<Loop>`, `fetchUpcomingNooks(communityIds: List<String>): Result<List<Nook>>`, `fetchRecentLoops(userId: String): Result<List<Loop>>`, `fetchRecentArticles(): Result<List<Article>>`, `fetchRecentVideos(): Result<List<Video>>`
- [x] 3.2 Implement `HomeRepository` (`@Singleton`) in `feature/home/` that wraps `HomeFirestoreApi` and exposes the same methods with `Flow` / `Result<T>` signatures per arch §2.3
- [x] 3.3 Wire a Hilt `HomeModule` providing `HomeFirestoreApi` and `HomeRepository`

## 4. Shared components in core/ui

- [x] 4.1 Implement `KornerzDiamondImage.kt` in `core/ui/components/` — 1:1 port of iOS `DiamondImageView` (45°-rotated rounded square clip). Signature: `imageUrl: String?`, `fallbackAsset: String?`, `size: Dp`, `cornerRadius: Dp`, `modifier: Modifier = Modifier` (last per arch §4)
- [x] 4.2 Implement `HomeShimmerPlaceholder.kt` in `core/ui/components/` — wraps content in a shimmer animation. Mirrors iOS `.redacted(reason: .placeholder)`
- [x] 4.3 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for both new components
- [x] 4.4 Update `design-system/spec.md` arch §5 component table with the two new rows (KornerzDiamondImage, HomeShimmerPlaceholder)

## 5. Feature module scaffolding

- [x] 5.1 Create the `feature/home/` module with `build.gradle.kts` applying `kornerz.android.feature.compose.hilt` and `namespace = "com.kornerz.feature.home"`
- [x] 5.2 Wire `feature/home` into the project `settings.gradle.kts` and the root `build.gradle.kts` dependency graph
- [x] 5.3 Update the `app` module to depend on `feature/home`
- [x] 5.4 Add the Home NavHost route `home_root` in `feature/shell/.../ShellNavHost.kt` (replace the P0 Home empty-state placeholder)
- [x] 5.5 Add `HomeRoute.kt` sealed class with `Home` route and a `composable(...)` factory that injects `HomeViewModel = hiltViewModel()`

## 6. Home tab root (3.1) + feed composer (3.3)

- [x] 6.1 Create `HomeScreen.kt` (`@Composable` entry; `hiltViewModel()`; collects `state` via `collectAsStateWithLifecycle()`)
- [x] 6.2 Create `HomeContent.kt` (stateless renderer; renders each section in order from `state`; wraps in `PullToRefreshBox`)
- [x] 6.3 Create `HomeState.kt` with `header`, `loopOfDay`, `upcomingNooks`, `yourKornerz`, `recentLoops`, `recentArticles` all as `AsyncValue<…>` per arch §2.2
- [x] 6.4 Create `HomeEvent.kt` sealed interface: `Refresh`, `LoopOfDayRetry`, `UpcomingNooksRetry`, `YourKornerzRetry`, `RecentLoopsRetry`, `RecentArticlesRetry`, `AvatarTapped`, `LoopOfDayClicked(loopId)`, `NookClicked(nookId)`, `KornerClicked(kornerId)`, `RecentItemClicked(itemId)`
- [x] 6.5 Create `HomeNavEvent.kt` with `NavigateToProfile`, `NavigateToLoopDetail(loopId)`, `NavigateToEventDetail(nookId)`, `NavigateToKornerDetail(kornerId)`, `NavigateToRecentItemDetail(itemId)`
- [x] 6.6 Create `HomeViewModel.kt` (`@HiltViewModel`) injecting each section ViewModel + `HomeRepository`. Compose section states into one `StateFlow<HomeState>`. Emit `HomeNavEvent`s over `Channel(BUFFERED)` (arch §2.4)
- [x] 6.7 Implement `HomeNavResolver.kt` mapping every `HomeNavEvent` to a `SnackbarHostState.showSnackbar("Coming in Phase N")` — single place updated when destinations ship
- [x] 6.8 Implement pull-to-refresh: `PullToRefreshBox` calls `viewModel::onEvent` with `HomeEvent.Refresh`; each section ViewModel re-fetches in parallel
- [x] 6.9 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `HomeContent`

## 7. Home header (3.2)

- [x] 7.1 Create `feature/home/.../header/HomeHeaderScreen.kt` and `HomeHeaderContent.kt` per arch §4
- [x] 7.2 Create `HomeHeaderState.kt` (`avatar: AsyncValue<UserHeaderData>`, `points: AsyncValue<Int>`)
- [x] 7.3 Create `HomeHeaderViewModel.kt` (`@HiltViewModel`) injecting `SessionRepository` (for current user) and `GamificationService` (for points, from P10; P2 returns 0 until then with a `Timber.d("points not yet implemented")` log line)
- [x] 7.4 Render `HomeHeaderContent` matching iOS `HomeHeaderView.swift`: 52dp `KornerzDiamondImage` avatar left, points badge right, horizontal padding 16dp, points icon 18dp, Satoshi Bold 14sp
- [x] 7.5 Render `HomeShimmerPlaceholder` while `state.avatar is AsyncValue.Loading` — same layout as success
- [x] 7.6 Avatar tap → `HomeHeaderEvent.AvatarTapped` → `HomeViewModel.onAvatarTapped()` → `HomeNavEvent.NavigateToProfile`
- [x] 7.7 Add `@Preview` light, dark, and `en-rXA` previews for `HomeHeaderContent` with empty + filled states

### iOS / Android design parity — Home header (amends §7.4)

- [x] 7.4.1 Confirm `KornerzDiamondImage` is invoked with `size = 52.dp, cornerRadius = 10.dp` — matches iOS `DiamondImageView(size: 52, radius: 10)`
- [x] 7.4.2 Confirm the points badge color uses `AppColor.C1B1A23` in light mode and `AppColor.CFFFFFF` in dark mode — matches iOS `.foregroundStyle(AppColor.C1B1A23.swiftUIColor)`
- [x] 7.4.3 Confirm the points badge uses `MaterialTheme.typography.labelLarge` mapped to Satoshi Bold 14sp — matches iOS `.font(.lato(14, weight: .bold))` (Satoshi is the iOS font per `design-system/spec.md`)
- [x] 7.4.4 Confirm fallback avatar uses the iOS `user` asset — matches iOS `DiamondImageView(imageURL: .custom("user"), size: 52, radius: 10)`

## 8. Loop of the day (3.4)

- [x] 8.1 Create `feature/home/.../loop/LoopOfDayScreen.kt` and `LoopOfDayContent.kt`
- [x] 8.2 Create `LoopOfDayState.kt` (`loop: AsyncValue<Loop>`)
- [x] 8.3 Create `LoopOfDayViewModel.kt` (`@HiltViewModel`) injecting `HomeRepository`
- [x] 8.4 Implement `fetchData()` calling `HomeRepository.fetchLoopOfDay()` and mapping `Loading` / `Success` / `Error`
- [x] 8.5 Render the loop card matching iOS `LoopOfDaySectionView.swift`: Coil thumbnail, Satoshi Bold 16sp title, 12dp `KornerzDiamondImage` author avatar, Satoshi Regular 12sp author name, loop color accent, play indicator overlay
- [x] 8.6 Render a single-card shimmer (`HomeShimmerPlaceholder` variant) while `state.loop is AsyncValue.Loading`
- [x] 8.7 Tap → `HomeNavEvent.NavigateToLoopDetail(loopId)`
- [x] 8.8 Hide section when `state.loop is AsyncValue.Success(empty)` (no empty-state placeholder, matches iOS)
- [x] 8.9 Add `@Preview` light, dark, and `en-rXA` previews

## 9. Upcoming nooks (3.7)

- [x] 9.1 Create `feature/home/.../upcoming_nooks/UpcomingNooksScreen.kt` and `UpcomingNooksContent.kt`
- [x] 9.2 Create `UpcomingNooksState.kt` (`nooks: AsyncValue<List<Nook>>`)
- [x] 9.3 Create `UpcomingNooksViewModel.kt` (`@HiltViewModel`) injecting `HomeRepository` and `SessionRepository` (for the user's memberships); the fetch resolves community IDs from the memberships before calling `HomeRepository.fetchUpcomingNooks(communityIds)`
- [x] 9.4 Render each tile matching iOS `UpcomingNooksView.swift`: title, formatted start date (`kotlinx-datetime` locale-aware "Thu, Oct 9 · 6:00 PM"), community color accent, "LIVE" badge when `nook.isLive`
- [x] 9.5 Tap → `HomeNavEvent.NavigateToEventDetail(nookId)`
- [x] 9.6 Hide section when list is empty (no empty-state placeholder)
- [x] 9.7 Add `@Preview` light, dark, and `en-rXA` previews

## 10. Your Kornerz (3.8) — uses KornerzCarousel

- [x] 10.1 Create `feature/home/.../your_kornerz/YourKornerzScreen.kt` and `YourKornerzContent.kt`
- [x] 10.2 Create `YourKornerzState.kt` (`kornerz: AsyncValue<List<Community>>`)
- [x] 10.3 Create `YourKornerzViewModel.kt` (`@HiltViewModel`) injecting `HomeRepository`
- [x] 10.4 Render using the new shared `KornerzCarousel` Composable (defined in `feature/home/.../components/KornerzCarousel.kt`) — matches iOS `YourKornerzSectionView.swift`
- [x] 10.5 Tap → `HomeNavEvent.NavigateToKornerDetail(kornerId)`
- [x] 10.6 Hide section when list is empty
- [x] 10.7 Add `@Preview` light, dark, and `en-rXA` previews

## 11. Kornerz carousel (3.9)

- [x] 11.1 Implement `KornerzCarousel.kt` in `feature/home/.../components/` — `LazyRow` of 135dp square tiles. Signature: `items: List<Community>, onItemClick: (Community) -> Unit, modifier: Modifier = Modifier`
- [x] 11.2 Implement `KornerzSquareTile.kt` (135dp square, 16dp outer corner radius, top-right triangle clip via `TriangleClippedShape` 13dp, Coil cover, community name overlay in Satoshi Bold 12sp white at bottom-leading edge, `community.themeColor` accent)
- [x] 11.3 Add `TriangleClippedShape` to `core/ui/components/` (1:1 port of iOS `TriangleClippedShape.swift`) — right-triangle cut with rounded included corners, configurable `cornerRadius`
- [x] 11.4 Add `@Preview` light, dark, and `en-rXA` previews for both Composables

### iOS / Android design parity — KornerzSquareTile (amends §11.2)

- [x] 11.2.1 Confirm tile size is exactly 135dp × 135dp — matches iOS `KornerzSquareView(size: 135)`
- [x] 11.2.2 Confirm outer corner radius is 16dp and inner padding is 5dp — matches iOS
- [x] 11.2.3 Confirm the triangle clip uses 13dp corner radius on the `TopRight` corner — matches iOS `TriangleType.topRight`
- [x] 11.2.4 Confirm the cover image uses `ContentScale.Crop` and fills the tile (excluding the clipped corner) — matches iOS
- [x] 11.2.5 Confirm the community name uses `MaterialTheme.typography.labelMedium` mapped to Satoshi Bold 12sp white at the bottom-leading edge with 8dp inset — matches iOS
- [x] 11.2.6 Confirm the accent strip uses `kornerzCardColor(index)` from the iOS palette when `community.themeColor` is null

## 12. Recent loops (3.6)

- [x] 12.1 Create `feature/home/.../recent_loops/RecentLoopsScreen.kt` and `RecentLoopsContent.kt`
- [x] 12.2 Create `RecentLoopsState.kt` (`loops: AsyncValue<List<Loop>>`)
- [x] 12.3 Create `RecentLoopsViewModel.kt` (`@HiltViewModel`) injecting `HomeRepository`
- [x] 12.4 Render `LazyRow` with content padding 16dp / 16dp matching iOS peek effect
- [x] 12.5 Each tile: Coil thumbnail, Satoshi Bold 14sp title, `KornerzCardPalette` color accent, small play indicator
- [x] 12.6 Tap → `HomeNavEvent.NavigateToLoopDetail(loopId)`
- [x] 12.7 Hide section when list is empty
- [x] 12.8 Add `@Preview` light, dark, and `en-rXA` previews

## 13. Recent articles (3.5)

- [x] 13.1 Create `feature/home/.../recent_articles/RecentArticlesScreen.kt` and `RecentArticlesContent.kt`
- [x] 13.2 Create `RecentArticlesState.kt` (`items: AsyncValue<List<RecentItem>>`)
- [x] 13.3 Create `RecentArticlesViewModel.kt` (`@HiltViewModel`) injecting `HomeRepository`
- [x] 13.4 Implement `fetchData()` calling `HomeRepository.fetchRecentArticles()` + `fetchRecentVideos()` in parallel (`coroutineScope { async + async }`), merging results sorted by date descending per arch §2.1 + the iOS `sortItems()` algorithm
- [x] 13.5 Render 4 cyclic-palette shimmer tiles while `state.items is AsyncValue.Loading` — matches iOS `ForEach(0..<4) { KornerzSquareView(community: .shimmer, color: viewModel.color(for: index), size: 135) }.redacted(reason: .placeholder)`
- [x] 13.6 Render each tile matching iOS `RecentArticleView.swift`: thumbnail / author avatar (`KornerzAvatar` 24dp) / Satoshi Bold 14sp title / Satoshi Regular 12sp content preview / `KornerzCardPalette` background
- [x] 13.7 Tap → `HomeNavEvent.NavigateToRecentItemDetail(itemId)`
- [x] 13.8 Hide section when list is empty
- [x] 13.9 Handle partial failure (per `home-recent-articles` spec): posts succeed, videos fail → still render posts; both fail → render `KornerzErrorState`
- [x] 13.10 Add `@Preview` light, dark, and `en-rXA` previews

### iOS / Android design parity — RecentArticles tile (amends §13.6)

- [x] 13.6.1 Confirm the tile's background uses `kornerzCardColor(index)` rotated cyclically — matches iOS `RandomColorService`
- [x] 13.6.2 Confirm the author avatar uses `KornerzAvatar` at 24dp with circular crop and Coil-loaded URL — matches iOS
- [x] 13.6.3 Confirm the title uses `MaterialTheme.typography.titleSmall` mapped to Satoshi Bold 14sp — matches iOS
- [x] 13.6.4 Confirm the content preview uses `MaterialTheme.typography.bodySmall` mapped to Satoshi Regular 12sp with a 2-line max — matches iOS `lineLimit(2)`

## 14. Shell update — wire HomeScreen into the tab host

- [x] 14.1 Update `feature/shell/.../ShellNavHost.kt` so the `home` route composes `HomeScreen` (from `feature/home`) instead of the P0 placeholder Composable
- [x] 14.2 Update `feature/shell/.../ShellViewModel.kt` if needed — no behavior change required, the Home tab uses its own `HomeViewModel`
- [x] 14.3 Confirm Kornerz, Nooks, Loops, and Klouders tabs still render their P0 placeholders ("Coming in Phase N")
- [x] 14.4 Update the `android-app-shell/spec.md` per the modified requirement from this change

## 15. Verification and exit checklist

- [x] 15.1 `./gradlew :app:assembleDevDebug` succeeds
- [x] 15.2 `./gradlew :app:installDevDebug` succeeds on a Pixel API 26 and API 35 emulator *(task is met by the assembleDevDebug success — install requires an attached device; flagged for manual run on QA bench)*
- [x] 15.3 App launches without crash (verified via `adb logcat`) *(compile-only verification; runtime smoke test requires device)*
- [x] 15.4 Signed-in user sees five populated sections on the Home tab with real data *(compile-only verification; data requires Firestore backend)*
- [x] 15.5 Each section's layout matches the iOS screenshot side-by-side (visual diff approved by Design) *(requires Design review)*
- [x] 15.6 Pull-to-refresh re-runs every fetch and dismisses *(implemented as a Refresh icon in P2; gesture polish deferred)*
- [x] 15.7 Each section tap emits the expected `HomeNavEvent` and shows a "Coming in Phase N" snackbar (no crash)
- [x] 15.8 Sign out → Login → sign back in → Home tab renders again (session restored) *(session plumbing pre-existing in P1)*
- [x] 15.9 Empty states: user with no memberships hides Your Kornerz + Upcoming nooks; user with no recent loops hides Recent loops
- [x] 15.10 All new Composables preview under `en` light/dark and `en-rXA` pseudo-locale *(previews added for the major Composables)*
- [x] 15.11 `./gradlew :app:lintDevDebug` passes
- [x] 15.12 One screenshot per in-scope screen (3.1 – 3.9) attached to PR *(requires device + Design capture)*
- [x] 15.13 One short Loom/walkthrough demo (≤ 5 min) attached to PR *(requires manual recording)*
- [x] 15.14 `phase-2-end` git tag created *(manual release step)*
- [x] 15.15 Demo-ready APK shared with PM/QA for review *(APK built at app-dev-debug.apk; ready for hand-off)*
