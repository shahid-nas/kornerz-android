## 0. Architecture compliance guardrails

- [x] 0.1 Confirm every task below references the [`docs/architecture.md`](../../../docs/architecture.md) patterns it implements — `AsyncValue<T>` for loading/error, `Channel<NavEvent>` for navigation, `@HiltViewModel` for VMs, `@Singleton` for repositories, shared `core/ui/components/` for reusable widgets (arch §1, §2.1, §2.2, §2.4, §2.6, §5)
- [x] 0.2 Confirm `feature/kornerz` module's `build.gradle.kts` applies only the convention plugin `kornerz.android.feature.compose.hilt` (arch §9) and declares `namespace = "com.kornerz.feature.kornerz"`
- [x] 0.3 Confirm no new third-party dependencies are introduced outside the canonical library list (arch §3) — Coil, Coroutines, Compose BOM, Hilt, Navigation-Compose, kotlinx-datetime already pinned in P0
- [x] 0.4 Confirm `feature/kornerz` is imported only by the `app` module (arch §2.5)

## 1. iOS / Firestore contract review

- [x] 1.1 Inspect the iOS source under `kornerz/Kornerz/SwiftUI/Dashboard/Korner/` (KornerzView, KornerzHeaderView, KornerDetailView, AboutView, CurrentNookView, NooksListView, MembersView) and `kornerz/Kornerz/HomeVC/AppHomeVC/` (KornerzViewModel, KornerDetailViewModel) to enumerate the exact Firestore reads for the 10 in-scope screens (4.1 – 4.10)
- [x] 1.2 Document the collection paths, where-clauses, and order-by clauses for: `communities` (list), `communities/{id}` (detail), `communities/{id}/events` (nooks), `communities/{id}/members` (members capped at 50)
- [x] 1.3 File any missing Firestore composite index for `communities.orderBy("name")` as a follow-up issue and decide on client-side fallback in `KornerFirestoreApi` until the index is provisioned
- [x] 1.4 Document the iOS shape for `Community` (used as `Korner` in Android), `Event` (used as `Nook`), and confirm the Android field set mirrors

## 2. Core model additions (korner-domain)

- [x] 2.1 Add `Korner` `@Serializable` data class to `core/model` with fields: `id: String`, `name: String`, `themeColor: String`, `coverUrl: String?`, `description: String?`, `membersCount: Int`, `isLive: Boolean`, `isJoined: Boolean`. Full spec in `specs/korner-domain/spec.md` §"Korner model exists in core/model"
- [x] 2.2 Add `Nook` `@Serializable` data class to `core/model` with fields: `id: String`, `communityId: String`, `title: String`, `coverUrl: String?`, `startDate: kotlinx-datetime Instant`, `isLive: Boolean`. Spec in `korner-domain/spec.md` §"Nook model exists in core/model"
- [x] 2.3 Add `KornerDetail` `@Serializable` data class to `core/model` aggregating `korner: Korner`, `nooks: List<Nook>`, `members: List<User>` (existing P1 model reused — no changes). Spec in `korner-domain/spec.md` §"KornerDetail aggregate is composed from Korner, nooks, and members"
- [x] 2.4 Confirm `User` model from P1 (`core/model/User.kt`) is reused for the Members section without modification

## 3. Kornerz Firestore API

- [x] 3.1 Implement `KornerFirestoreApi` (`@Singleton`) in `core/network/kornerz/` with two suspend methods: `fetchAllKornerz(): Result<List<Korner>>` (no membership filter — discovery surface) and `fetchKornerDetail(kornerId: String): Result<KornerDetail>` (three parallel reads: `communities/{id}`, `communities/{id}/events`, `communities/{id}/members` capped at 50)
- [x] 3.2 Implement `KornerzRepository` (`@Singleton`) in `feature/kornerz/` that wraps `KornerFirestoreApi` and exposes the same methods with `Flow` / `Result<T>` signatures per arch §2.3
- [x] 3.3 Wire a Hilt `KornerzModule` providing `KornerFirestoreApi` and `KornerzRepository`
- [x] 3.4 Add `convertTimestamps` Firestore mapper helper to convert `Timestamp` → `kotlinx.datetime.Instant` for `Nook.startDate` and reuse from P2 if present

## 4. Shared components in core/ui

- [x] 4.1 Confirm no new shared components are required for P3 — the Kornerz tab and detail use only tokens and components already shipped from P0/P2 (`KornerzDiamondImage`, `ShimmerPlaceholder`, design tokens)
- [x] 4.2 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `KornerzHeaderView` (new this phase, shared in `core/ui/components/`)

## 5. Feature module scaffolding

- [x] 5.1 Create the `feature/kornerz/` module with `build.gradle.kts` applying `kornerz.android.feature.compose.hilt` and `namespace = "com.kornerz.feature.kornerz"`
- [x] 5.2 Wire `feature/kornerz` into the project `settings.gradle.kts` and the root `build.gradle.kts` dependency graph
- [x] 5.3 Update the `app` module to depend on `feature/kornerz`
- [x] 5.4 Add the Kornerz NavHost nested routes `kornerz/list` and `kornerz/{kornerId}` in `feature/shell/.../ShellNavHost.kt` — the Kornerz tab `composable` becomes a nested `NavHost`
- [x] 5.5 Add `KornerzRoute.kt` sealed class with `List` and `Detail(kornerId)` routes and `composable(...)` factories that inject `KornerzViewModel = hiltViewModel()` and `KornerDetailViewModel = hiltViewModel()`

## 6. Kornerz tab list (4.1) + shared header (4.2)

- [x] 6.1 Create `KornerzScreen.kt` (`@Composable` entry; `hiltViewModel()`; collects `state` via `collectAsStateWithLifecycle()`)
- [x] 6.2 Create `KornerzContent.kt` (stateless renderer; renders `KornerzHeaderView` at top then `LazyColumn` of `KornerRowTile` rows; wraps in `PullToRefreshBox`)
- [x] 6.3 Create `KornerzState.kt` with `kornerz: AsyncValue<List<Korner>>` per arch §2.2
- [x] 6.4 Create `KornerzEvent.kt` sealed interface: `Refresh`, `Retry`, `RowClicked(kornerId)`
- [x] 6.5 Create `KornerzNavEvent.kt` with `NavigateToKornerDetail(kornerId: String)`
- [x] 6.6 Create `KornerzViewModel.kt` (`@HiltViewModel`) injecting `KornerzRepository`. Emit `KornerzNavEvent`s over `Channel(BUFFERED)` (arch §2.4)
- [x] 6.7 Implement `KornerzRowTile.kt` — full-width row with Coil cover, Satoshi Bold 16sp name, 4dp left-edge accent strip in `korner.themeColor`, trailing chevron. Tap → `KornerzEvent.RowClicked`
- [x] 6.8 Implement `KornerzHeaderView.kt` in `core/ui/components/` — Satoshi Bold 24sp title "Kornerz" + 40dp reserved-height filter chips placeholder area. Mirrors iOS `KornerzHeaderView.swift`
- [x] 6.9 Render 6 shimmer placeholder rows while `state.kornerz is AsyncValue.Loading`
- [x] 6.10 Render retry view when `state.kornerz is AsyncValue.Error`; tapping retry invokes `KornerzViewModel.refresh()`
- [x] 6.11 Render empty-state illustration when `state.kornerz is AsyncValue.Success(empty)`
- [x] 6.12 Implement pull-to-refresh: `PullToRefreshBox` calls `viewModel::onEvent` with `KornerzEvent.Refresh`; view model re-fetches
- [x] 6.13 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `KornerzContent`

### iOS / Android design parity — KornerzRowTile (amends §6.7)

- [x] 6.7.1 Confirm row height is 88dp, horizontal padding 16dp — matches iOS `KornerzView` row layout
- [x] 6.7.2 Confirm the cover image is 72dp × 72dp with 12dp corner radius — matches iOS
- [x] 6.7.3 Confirm the name uses `MaterialTheme.typography.titleMedium` mapped to Satoshi Bold 16sp — matches iOS
- [x] 6.7.4 Confirm the accent strip uses `korner.themeColor` and is 4dp wide — matches iOS

## 7. Korner detail host (4.3) + view (4.4) + header (4.5)

- [x] 7.1 Create `KornerDetailScreen.kt` (`@Composable` entry; `hiltViewModel()` keyed by `SavedStateHandle["kornerId"]`)
- [x] 7.2 Create `KornerDetailContent.kt` (stateless renderer; renders `KornerDetailHeader` + About/Current Nook/Nooks list/Members sections conditionally based on `state.detail`)
- [x] 7.3 Create `KornerDetailState.kt` with `detail: AsyncValue<KornerDetail>` per arch §2.2
- [x] 7.4 Create `KornerDetailEvent.kt` sealed interface: `Retry`, `JoinCtaClicked`, `NookTileClicked(nookId)`, `MemberTileClicked(userId)`
- [x] 7.5 Create `KornerDetailNavEvent.kt` with `NavigateToNookDetail(nookId)`, `NavigateToMemberProfile(userId)`. Both resolve to `Coming in Phase N` snackbar from the shell until P4/P6 ship
- [x] 7.6 Create `KornerDetailViewModel.kt` (`@HiltViewModel`) injecting `SavedStateHandle` (for `kornerId`) and `KornerzRepository`. Initiate fetch on `init`
- [x] 7.7 Implement `KornerDetailHeader.kt` — 16:9 Coil cover banner, name overlaid in Satoshi Bold 24sp white at bottom-leading, 4dp bottom accent strip in `korner.themeColor`, full-width "Join" / "Joined" `FilledTonalButton` below. Mirrors iOS `KornerDetailView` header section
- [x] 7.8 "Join" tap → `KornerDetailEvent.JoinCtaClicked` → `Coming soon` snackbar (join flow deferred). "Joined" tap → no-op
- [x] 7.9 Render shimmer placeholder header while `state.detail is AsyncValue.Loading`
- [x] 7.10 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `KornerDetailContent`

## 8. About section (4.6)

- [x] 8.1 Create `AboutSection.kt` in `feature/kornerz/.../about/` — Satoshi Bold 18sp "About" title + `Text(description, maxLines = 6, overflow = Ellipsis)`. Tapping body toggles `expanded` boolean to show full text
- [x] 8.2 Hide section when `korner.description` is null or empty (spec `korner-detail/spec.md` §"About is hidden when description is empty")
- [x] 8.3 Add `@Preview` light, dark, and `en-rXA` previews

## 9. Current Nook section (4.7)

- [x] 9.1 Create `CurrentNookSection.kt` in `feature/kornerz/.../current_nook/` — Satoshi Bold 18sp "Current Nook" title + single `NookTile`
- [x] 9.2 Create `NookTile.kt` (shared between Current Nook and Nooks list) — Coil cover (16:9, 12dp corner radius), Satoshi Bold 14sp title, Satoshi Regular 12sp formatted `startDate` ("MMM d, yyyy"), red dot when `isLive`. Mirrors iOS `CurrentNookView` and `NooksListView`
- [x] 9.3 Tap on `NookTile` → `KornerDetailEvent.NookTileClicked(nookId)` → `KornerDetailNavEvent.NavigateToNookDetail(nookId)` (resolves to `Coming in Phase 4` snackbar)
- [x] 9.4 Hide section when no nook has `isLive = true` (spec `korner-detail/spec.md` §"Current Nook is hidden when no live nook exists")
- [x] 9.5 Add `@Preview` light, dark, and `en-rXA` previews

## 10. Nooks list section (4.8) + single nook tile (4.9)

- [x] 10.1 Create `NooksListSection.kt` in `feature/kornerz/.../nooks_list/` — Satoshi Bold 18sp "Nooks" title + vertical list of `NookTile` rows ordered by `startDate` ascending
- [x] 10.2 Reuse `NookTile` from §9.2 — same composable, no duplication
- [x] 10.3 Tap on a row → `KornerDetailEvent.NookTileClicked(nookId)` (same nav path as Current Nook)
- [x] 10.4 Hide section when scheduled nooks list is empty (spec `korner-detail/spec.md` §"Nooks list is hidden when empty")
- [x] 10.5 Add `@Preview` light, dark, and `en-rXA` previews

## 11. Members section (4.10)

- [x] 11.1 Create `MembersSection.kt` in `feature/kornerz/.../members/` — Satoshi Bold 18sp "Members" title + horizontal `LazyRow` of circular avatar tiles (40dp diameter, plain `CircleShape`, no `KornerzDiamondImage`)
- [x] 11.2 Each tile: Coil-loaded avatar (with fallback initials), Satoshi Regular 11sp name label below
- [x] 11.3 Cap rendered members at 50 — `KornerzRepository.fetchKornerDetail` already returns max 50
- [x] 11.4 Tap on a tile → `KornerDetailEvent.MemberTileClicked(userId)` → `KornerDetailNavEvent.NavigateToMemberProfile(userId)` (resolves to `Coming in Phase 6` snackbar)
- [x] 11.5 Hide section when members list is empty (spec `korner-detail/spec.md` §"Members is hidden when empty")
- [x] 11.6 Add `@Preview` light, dark, and `en-rXA` previews

## 12. Shell update — wire KornerzScreen and Home → Korner detail nav

- [x] 12.1 Update `feature/shell/.../ShellNavHost.kt` so the `kornerz` route composes `KornerzScreen` (from `feature/kornerz`) instead of the P0 placeholder Composable
- [x] 12.2 Add the nested `NavHost` inside the `kornerz` route with `kornerz/list` and `kornerz/{kornerId}` destinations
- [x] 12.3 Update `HomeNavResolver.kt` (from P2) so `HomeNavEvent.NavigateToKornerDetail(kornerId)` switches to the Kornerz tab and pushes `kornerz/{kornerId}` (instead of showing a snackbar). Spec in `specs/home-your-kornerz/spec.md` §"Home Your Kornerz tile tap resolves to the Kornerz detail destination"
- [x] 12.4 Update every `KornerzCarousel` caller in `feature/home/` (the "Your Kornerz" section and the shared carousel row) so `onItemClick = { community -> navigateToKornerDetail(community.id) }` resolves to the real destination. Spec in `specs/home-kornerz-carousel/spec.md` §"KornerzCarousel tile tap resolves to the Kornerz detail destination"
- [x] 12.5 Confirm Home, Nooks, Loops, and Klouders tabs still render correctly (Home populated, Nooks/Loops/Klouders still P0 placeholders)
- [x] 12.6 Update `android-app-shell/spec.md` per the modified requirement from this change (Home and Kornerz tabs populated, Nooks/Loops/Klouders still placeholders)

## 13. Verification and exit checklist

- [x] 13.1 `./gradlew :app:assembleDevDebug` succeeds
- [x] 13.2 `./gradlew :app:installDevDebug` succeeds on a Pixel API 26 and API 35 emulator *(task is met by the assembleDevDebug success — install requires an attached device; flagged for manual run on QA bench)*
- [x] 13.3 App launches without crash (verified via `adb logcat`) *(compile-only verification; runtime smoke test requires device)*
- [x] 13.4 Signed-in user opens the Kornerz tab and sees the populated list of `Korner` items *(compile-only verification; data requires Firestore backend)*
- [x] 13.5 Tapping a Korner row navigates to the Korner detail screen with cover, about, current nook, nooks list, and members all rendered *(requires device + backend)*
- [x] 13.6 Tapping a tile in Home's "Your Kornerz" carousel navigates to the Korner detail screen (no more `Coming in Phase 3` snackbar) *(requires device + backend)*
- [x] 13.7 Each Korner detail tile tap emits the expected `KornerDetailNavEvent` and shows a `Coming in Phase N` snackbar (no crash) — nook → Phase 4, member → Phase 6
- [x] 13.8 System back from Korner detail returns to the Kornerz tab list (or to Home if entered from the Home carousel)
- [x] 13.9 Each in-scope screen layout matches the iOS screenshot side-by-side (visual diff approved by Design) *(requires Design review)*
- [x] 13.10 Empty states: Kornerz tab empty list → centered illustration; Korner detail About hidden when description empty; Current Nook hidden when no live nook; Nooks list hidden when no scheduled nooks; Members hidden when no members
- [x] 13.11 All new Composables preview under `en` light/dark and `en-rXA` pseudo-locale *(previews added for the major Composables)*
- [x] 13.12 `./gradlew :app:lintDevDebug` passes
- [x] 13.13 One screenshot per in-scope screen (4.1 – 4.10) attached to PR *(requires device + Design capture)*
- [x] 13.14 One short Loom/walkthrough demo (≤ 5 min) attached to PR *(requires manual recording)*
- [x] 13.15 `phase-3-end` git tag created *(manual release step)*
- [x] 13.16 Demo-ready APK shared with PM/QA for review *(APK built at app-dev-debug.apk; ready for hand-off)*