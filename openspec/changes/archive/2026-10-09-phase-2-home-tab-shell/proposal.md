## Why

> **See also:** [`docs/architecture.md`](../../../docs/architecture.md) for the MVVM + Repository + UDF contract, `AsyncValue<T>`, `Channel<NavEvent>`, module boundaries, Hilt scopes, and the canonical library list that every artifact in this change conforms to.

Phase 0 produced a branded shell and Phase 1 produced a working auth flow, so a signed-in Android user can reach the Home tab — but the Home tab itself is still the P0 empty-state placeholder. Every other tab (Kornerz, Nooks, Loops, Klouders) remains an empty placeholder too. Phase 2 closes that gap by populating the Home tab with the same five sections the iOS app ships (`HomeTabView.swift` → `Sections/HomeFeedSection.swift`), so the app becomes presentable to PM, Marketing, and Design as the "First Impression" milestone. Every section is implemented 1:1 against the corresponding iOS SwiftUI view so the Android and iOS builds look identical in screenshots and walkthroughs.

## What Changes

- Add a new `feature/home` module with the standard scaffolding (`kornerz.android.feature.compose.hilt`), hosting the Home tab's `NavHost` route and five section composables that mirror `SwiftUI/Dashboard/HomeTab/Sections/`.
- Add a `HomeViewModel` (`@HiltViewModel`) that exposes a `HomeState` with one `AsyncValue<List<…>>` per section (header avatar, loop of the day, upcoming nooks, your Kornerz, recent loops, recent articles), fetches each section independently via a new `HomeRepository`, and emits navigation events through `Channel<HomeNavEvent>` per arch §2.4.
- Add `HomeRepository` (`@Singleton`) backed by Firestore queries that mirror the iOS `AppHomeViewModel` / `RecentArticlesViewModel` reads — communities the signed-in user is a member of (drives Your Kornerz + Upcoming Nooks), the loop-of-the-day document, recent loops, and a merged feed of recent articles/videos/forums.
- Add `core/model` types `Community` (the public subset needed by Home — id, name, theme color, member flag, cover URL), `Loop` (id, title, thumbnail, author, color, duration), `Article` (id, title, content, thumbnail, author, publishedAt), `Nook` (id, communityId, title, startDate, isLive) — all `@Serializable` per arch §3. Full `Post`, `Video`, `Forum` definitions are deferred to their owning phases (P5 Loops, P3 Kornerz); Home only consumes their title, thumbnail, and publisher fields via a `RecentItem` sum-type that mirrors `SwiftUI …/RecentArticles/RecentArticlesViewModel.swift:RecentItem`.
- Add Coil-based image loading (`Coil 2.x` already pinned in P0) with shimmer placeholders for every section's avatar / thumbnail / cover (mirrors `HomeHeaderView.swift` `.redacted(reason: .placeholder)`).
- Update `feature/shell` so the Home tab's `NavHost` route no longer renders the P0 "Coming in Phase 2" placeholder; instead it hosts `HomeScreen`. The 5-tab order, tab icons, and selection state are unchanged. The Home tab pulls-to-refresh gesture (mirrors `HomeTabView.swift` `.refreshable`) is wired through a `HomeEvent.Refresh` → `HomeViewModel.refresh()` path that re-runs every section fetch.
- Wire all "tap" callbacks from the iOS sections (`onUserTap` → Profile, `onLoopTap` → LoopDetail, `onNookTap` → EventDetail, `onKornerTap` → KornerDetail, `onRecentItemTap` → Post/Video/Forum detail) into `Channel<HomeNavEvent>` per arch §2.4 — each destination resolves to a no-op route that P3–P10 fill in (logged-in P2 Home still navigates; the destinations show a brief "Coming in Phase N" snackbar instead of crashing).
- Remove the P0 Home empty-state placeholder from `feature/shell` (it stays in place for the Kornerz, Nooks, Loops, and Klouders tabs until their respective phases ship).

## Capabilities

### New Capabilities

- `home-feed`: the Home tab root (3.1) plus the home feed section composer (3.3) — the `LazyColumn` that renders each section in order, pull-to-refresh, and the per-section `AsyncValue` plumbing that gates `Loading` / `Success` / `Error` rendering per arch §2.2.
- `home-header`: the Home header (3.2) — avatar diamond (port of `DiamondImageView` at 52dp / radius 10), signed-in user points count, and `onUserTap` navigation hook to Profile.
- `home-loop-of-day`: Loop of the day (3.4) — single featured `Loop` card mirroring `LoopOfDaySectionView.swift`.
- `home-recent-articles`: Recent articles (3.5) — a 1:1 port of `RecentArticlesView.swift`'s mixed `.post` / `.video` / `.forum` `RecentItem` carousel (the merged articles-videos-forums feed). Each tile mirrors the iOS color palette (`RandomColorService`) and uses the same thumbnail / owner-avatar / title / content layout.
- `home-recent-loops`: Recent loops (3.6) — horizontal carousel mirroring `RecentLoopsView.swift`, each tile tappable to `LoopDetail`.
- `home-upcoming-nooks`: Upcoming nooks (3.7) — horizontal carousel mirroring `UpcomingNooksView.swift`, each tile tappable to `EventDetail`.
- `home-your-kornerz`: Your Kornerz (3.8) — horizontal carousel mirroring `YourKornerzSectionView.swift`, each tile tappable to `KornerDetail`.
- `home-kornerz-carousel`: Kornerz carousel (3.9) — the compact preview carousel used inside `home-your-kornerz` and reused from the iOS `SwiftUI/Dashboard/Korner/Sections/KornerzCarouselView.swift`.

### Modified Capabilities

- `android-app-shell`: the Home tab route must render `HomeScreen` instead of the P0 placeholder. The shell-level tab order, selection, insets, and theming requirements are unchanged.
- `design-system`: two new shared composables are added to `core/ui/components/` (mirroring the iOS shapes): `KornerzDiamondImage` (the rotated-rectangle 1:1 port of iOS `DiamondImageView` used for the Home header avatar and the home tiles that display a community mark) and `HomeShimmerPlaceholder` (the shimmer variant used while `AsyncValue.Loading` is active for a section). Both are added to the arch §5 shared-components table as new rows but no existing requirement is removed.

## Impact

- New module: `feature/home` (convention plugin `kornerz.android.feature.compose.hilt`, `namespace = "com.kornerz.feature.home"`). Imported only by `app` per arch §2.5.
- New code in `core/model` (`Community`, `Loop`, `Article`, `Nook`, `RecentItem`, `User` already present from P1), `core/network` (`HomeFirestoreApi` wrapper around the same Firestore paths iOS reads), `feature/home` (`HomeScreen`/`Content`, `HomeState`/`Event`/`NavEvent`, `HomeViewModel`, `HomeRepository`, plus one Composable + ViewModel per section per arch §4).
- Touched modules: `feature/shell` (Home tab `composable` now hosts `HomeScreen` instead of the placeholder), `core/ui/components/` (add `KornerzDiamondImage` and `HomeShimmerPlaceholder`), `app` (Hilt module wires `HomeRepository` into the Home graph), `gradle/libs.versions.toml` (no new dependencies — Coil, Coroutines, Compose BOM, Hilt, Navigation-Compose, kotlinx-datetime already pinned in P0).
- Dependencies: none outside arch §3. Coil is already pinned for image loading. Lottie-Compose is not used by the Home tab in P2 — the iOS Home does not animate the avatar.
- Backend: zero new backend work. Every Firestore collection and document path read by Android matches the iOS app's reads against the shared `kornerz-72fba` project (`communities`, `users/{uid}/memberships`, `loops`, `articles`, `videos`, `forums`, `events`). If a missing index surfaces during development, it is filed as a follow-up issue and does not block P2.
- Localization: English only this phase per `project-brief.md` §11.7 and `phases.md` Principle 7; all new screens are RTL-safe and previewed under the `en-rXA` pseudo-locale.
- Out of scope (deferred): iOS legacy sections `RegisterToAppCell` / `StartNewConversationCell` / `CommunitiesListingCell` / `MostReadArticlesCell` / `MostViewedVideosCell` — confirmed not in the live iOS flow (see `screen-inventory.md` "What is NOT in the Android port"). Full `Post`, `Video`, `Forum` model definitions (Home only consumes their title / thumbnail / publisher fields). Loop comments, loop replies, and video playback inside the Home carousel (P5). Event detail / Korner detail / Profile destinations — they emit a `Coming in Phase N` snackbar in P2 and are wired in P3 / P5 / P6.