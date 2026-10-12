## Context

Phases P0–P4 shipped a populated Home, Kornerz, and Nooks tab on top of a real Firebase backend (`kornerz-72fba`). The Home tab's `RecentLoopsView` carousel (P2 — `specs/home-recent-loops/spec.md`) renders recent loops but tapping a tile currently surfaces a `Coming in Phase 5` snackbar because no standalone loops destination exists, and the fourth tab in the bottom navigation bar (`Loops`) is still the P0 "Coming in Phase 5" empty-state placeholder. Phase 5 closes that gap by porting the entire iOS Loops vertical — feed, filter, item row, detail (with video playback), comments, create-comment sheet, and reply thread. The architectural baseline is unchanged from P4: MVVM + Repository + UDF, `AsyncValue<T>` per section, `Channel<NavEvent>` for navigation, Hilt scopes (`@HiltViewModel`, `@Singleton` repositories), `SavedStateHandle` for nav-arg ViewModels, `core/model` for `@Serializable` data, Coil for images, and the standard `feature/*` Gradle scaffolding.

The source of truth for behavior, field names, sort orders, and pagination lives at `kornerz/SwiftUI/Dashboard/Loops/` (and the underlying `kornerz/Kornerz/FirestoreService/*` for the collection paths). iOS uses UIKit coordinators + SwiftUI; the Android port uses Compose + Navigation-Compose + ViewModels. The Android port must reach behavioral parity — every Firestore read, every sort order, every field, every interaction matches the iOS app.

The new additions are one feature module (`feature/loops`) hosting three top-level destinations (`loops/list`, `loops/{loopId}`, plus the create-comment and reply screens) reachable from two entry points (the Loops bottom-nav tab and the Home recent loops carousel). One library family arrives ahead of its originally scheduled phase: **Media3 ExoPlayer** — arch §3 already lists it under "UI helpers / Video playback (P9)" so this proposal is the amendment that moves it from P9 to P5 because loop detail video playback is part of the Loops vertical.

## Goals / Non-Goals

**Goals:**

- Implement the Loops tab root screen — a paginated, filterable, searchable feed of loops ordered by `creationDate` descending, with the filter chips (`All` / `Your Loops`) and search bar ported from iOS `LoopsFilterView.swift`.
- Implement the shared `LoopItemView` composable used by both the Loops tab and the Home recent loops carousel (6.3, refactored from the P2 inline row composable).
- Implement the loop detail screen (6.4, 6.5) — full-width media area (image or ExoPlayer video), title + owner/community/date row + like toggle, expandable description (HTML or plain text), stats row, comments header, comment list, and a safe-area-inset add-comment button.
- Implement the comments section (6.6), create-comment sheet (6.7, 6.8), and reply thread (6.9, 6.10).
- Add four repositories wrapping the iOS `LoopRepository`, `LoopCommentRepository`, `LoopLikeRepository`, `CommentLikeRepository` shapes — same Firestore paths, same `Result<T>` / `Flow<T>` return shapes per arch §2.3.
- Add four Firestore API wrappers in `core/network` that hide the collection paths (`loops`, `loopComments`, `users/{userId}/likedLoops`, `loopComments/{commentId}/likes`).
- Add Coil-based image loading for loop thumbnails, avatars, and Media3 ExoPlayer wiring for the loop detail video playback.
- Pass the per-phase exit checklist (assembleDevDebug, installDevDebug on API 26 + API 35 emulators, no crashes, lint, unit tests, screenshots, demo walkthrough, `phase-5-end` tag).

**Non-Goals:**

- Profile + Notifications (P6), Create Community + Admin (P7), Premium / IAP (P8), Video Calling via Vonage / WebRTC (P9), Ranking + Deep Links + Polish (P10).
- Loop creation wizard (iOS `CreateLoopController.swift` exists but no entry point in the active flow per `screen-inventory.md` §6; stays deferred).
- Reaction emojis on comments (not in iOS live flow).
- Loop video upload / transcode (iOS uses the Video Kit + IPFS pipeline; stays deferred).
- Push notification deep links from `loopId` (P10).
- Like animation (iOS Lottie overlays on like; not in this phase — a follow-up can add `KornerzHeartBurstLottie` from `core/ui/components/`).
- `LoopFilter` chip beyond `All` / `Your Loops` (iOS has two chips only — no others in active flow).
- RTL / pt-BR localization (English only this phase per `project-brief.md` §11.7).
- Video playback analytics (we just call `incrementViewCount` once per session — full video analytics are out of scope).
- Korner → Loops-list deep link (only loop detail deep link is in scope; Korner → Loops link is held for a follow-up if a Korner ever needs it).

## Decisions

### Decision: One feature module, four destinations (`loops/list`, `loops/{loopId}`, comment sheet, reply screen) with nested NavHost

The Loops bottom-nav tab hosts a nested `NavHost` with `loops/list` as its start destination. `loops/{loopId}` is pushed when a `LoopItemView` is tapped from anywhere (the tab feed, the Home recent loops carousel, or — once a future Korner exposes them — a Korner detail entry point). The create-comment sheet is registered as a `dialog` route inside the Loops tab so `Dialog` semantics match the iOS modal sheet; the reply screen is a regular `composable("loops/{loopId}/reply/{commentId}")` nested under the loop detail.

**Alternatives considered:**

- Hosting the comment sheet as a bottom-sheet using `ModalBottomSheet`. Rejected: the iOS sheet is a centered modal with a rounded `Card`-style container (see `CreateCommentScreen.swift`); a `Dialog` route in Navigation-Compose gives the same visual shape with less custom chrome.
- Single top-level route for the loop detail with bottom-bar visibility logic. Rejected: P3/P4 established the per-tab nested-NavHost pattern, and this change mirrors it.

### Decision: ViewModel composition follows iOS exactly — one ViewModel per screen, `SavedStateHandle["loopId"]` keys the detail / comment / reply VMs

Navigation-Compose hands each route its nav-args through `SavedStateHandle`. `LoopDetailViewModel`, `CreateCommentViewModel`, and `ReplyViewModel` each read their nav-args (`loopId`, `loopId` + `parentCommentId`) and inject the appropriate repository. `LoopsViewModel` has no nav-args — it is shared across the bottom-nav tab's lifetime. This matches arch §10's "What adding a new screen looks like" recipe.

**Alternatives considered:**

- Pass the full `Loop` as a JSON nav-arg instead of a `loopId`. Rejected: iOS always re-fetches via the repository to pick up fresh `likesCount`, `commentsCount`, `isVideo` state; mirror that.
- A single `LoopsViewModel` for both the tab and the detail using Hilt `ViewModelProvider.Factory` wiring. Rejected: single-responsibility per screen is the UDF contract and easier to test.

### Decision: Four repositories map 1:1 to the four iOS repository protocols

The iOS app splits loop-related writes into four repositories (`LoopRepository`, `LoopCommentRepository`, `LoopLikeRepository`, `CommentLikeRepository`). We mirror that exactly: each is a `@Singleton` in `feature/loops/data/` wrapping its own `core/network/loops/*FirestoreApi.kt` wrapper. The reasons are the same as iOS: each repository owns a different collection path, each has its own auth rules, and the comment-like flow uses a different per-user collection path from the loop-like flow.

**Alternatives considered:**

- One `LoopRepository` that owns all four collections. Rejected: confuses per-collection auth and breaks the iOS parity test (a diff against `kornerz/Kornerz/FirestoreService/*`).
- A single `LoopsApi` facade. Rejected: hides the four distinct auth paths; `Result<T>` errors would lose context.

### Decision: Media3 ExoPlayer for video playback, advanced from P9 to P5

The `Loop.isVideo` predicate resolves true when `firebaseVideoUrl` is non-empty (per `Loop.isVideo` computed property on the iOS model). When `isVideo = false`, the loop detail shows a still image (`Loop.thumbnailURL` fallback chain: `firebaseThumbnailUrl` → `firebaseImageUrl` → `IPFS.baseUrl + imageHash`). When `isVideo = true`, the loop detail uses `androidx.media3:media3-exoplayer` + `androidx.media3:media3-ui` inside an `AndroidView(PlayerView)` host, wrapped in a `VideoPlayerView` composable. A `PlayerViewModel` exposes `isPlaying`, `playbackError`, and `controlsVisible` flags; the player is released on dispose.

Arch §3 already lists Media3 ExoPlayer under "UI helpers / Video playback (P9)". This change is the amendment that moves the introduction forward to P5 because the Loops detail screen requires video playback in the live iOS flow. The rule "loops detail video playback ships in P5" replaces the original "P9" line — no canonical library is added or removed, only its introduction phase changes.

**Alternatives considered:**

- Use `VideoView` + `MediaPlayer` (built-in Android). Rejected: worse UX than ExoPlayer (no buffering controls, no adaptive bitrate, deprecated in Android 14+).
- Use `WebView` with an `<video>` element pointing at IPFS / Firebase URLs. Rejected: iOS uses a native player (`AVPlayer`-style), and a WebView would diverge from iOS behavior (no fullscreen on rotate, no background audio).
- Defer video playback to P9 and show a still thumbnail with a "Video coming soon" badge. Rejected: drops the iOS parity requirement.

### Decision: Filter + search behavior mirrors iOS — debounced search, immediate search on submit, search-cancel resets to non-search mode

The `LoopsFilterView` exposes a chip row (`All` / `Your Loops`) and a magnifying-glass button that toggles into a search field with a "Cancel" button. Search is **debounced 1.5s** in iOS via Combine `.debounce(for: .seconds(1.5))` and **immediate** on `Keyboard.onSubmit` via `shouldTriggerImmediateSearch`. The Android port uses `kotlinx.coroutines.Flow.debounce(1_500L)` + `MutableStateFlow` + `SharedFlow.tryEmit` with a "submit" channel. Cancel clears the query and resets to non-search mode — same as iOS.

**Alternatives considered:**

- Single Android-specific `SearchBar` Material 3 component. Rejected: iOS uses a custom capsule that does not exist in the M3 catalog yet; we ship `KornerzSearchField` in `core/ui/components/` and wire it to the iOS-equivalent contract.
- Always-immediate search on every keystroke (no debounce). Rejected: iOS debounce; we mirror to keep behavior identical.

### Decision: Pagination via `startAfterDocument` cursor — same semantics as iOS `lastDocument`

iOS uses Firestore `query(startAt: lastDocument)` to advance pages; Android uses `Query.startAfter(lastDocumentSnapshot)` from the Kotlin Firestore SDK. The `LoopRepository.loadMore(cursor: LoopCursor?)` method accepts an opaque `LoopCursor(cursorDocSnapshot)` and emits a `PagingResult(items: List<Loop>, nextCursor: LoopCursor?)`. A `null` cursor starts from the beginning; a `null` `nextCursor` means no more pages. The Loops tab tracks `hasMoreData` in `LoopsState` and renders a `LoadingMoreView` at the bottom while `isLoadingMore = true`.

**Alternatives considered:**

- `androidx.paging3` `PagingSource`. Rejected: iOS does not use a paging library; mirror iOS shape exactly. Paging3 can be added in a follow-up without changing screens (per arch evolution rule).
- Fetch all + sort in memory. Rejected: wastes bandwidth and diverges from iOS pagination behavior.

### Decision: Comments + likes use write-through optimistic UI

For the loop like button, when `LoopDetailViewModel.toggleLike()` is invoked, the state `isLikedByUser` flips immediately and `loop.likesCount` increments locally; the Firestore write happens in the background. If the write fails, the UI reverts. The same pattern is used for the comment like button and the create-comment submission (the comment is added to the local list optimistically, removed if the write fails). This matches iOS `toggleLike()` / `toggleCommentLike()` which mutate local state before dispatching the write.

**Alternatives considered:**

- Strict write-then-read (no optimistic UI). Rejected: iOS uses optimistic; mirror.

### Decision: No new design tokens or shared `core/ui` components beyond what's already shipped

The Loops tab, loop item, loop detail, comments, create-comment, reply, and video overlay use only tokens and components already shipped from P0–P4: 8pt grid spacing, Satoshi typography, color palette, `KornerzButton`, `KornerzTextField`, `KornerzAvatar`, `ShimmerPlaceholder`, `KornerzEmptyState`, `KornerzErrorState` (with Retry button), `KornerzLoadingView`, `KornerzTopAppBar`. One new `core/ui/components/` item is added — `KornerzSearchField` (capsule with magnifying-glass icon + trailing Cancel button) — to back the search affordance that iOS builds inline. All screens previewed under light, dark, and `en-rXA` pseudo-locale.

### Decision: `feature/home` depends on `feature/loops` for shared `LoopItemView`

The P2 `RecentLoopsSection` composable currently renders an inline row layout. Phase 5 refactors it to delegate to `LoopItemView` from `feature/loops`. `feature/home` declares a one-way dependency on `feature/loops` — same pattern P4 used for `feature/kornerz → feature/nooks` for shared `NookCard`. This eliminates duplicated card chrome and guarantees visual parity across the home carousel and the Loops tab.

### Decision: Back-stack rule — `loops/{loopId}` pops to `loops/list`; `loops/{loopId}/reply/{commentId}` pops to `loops/{loopId}`; comment sheet dialog dismisses back to the loop detail

`navController.popBackStack()` resolves each nested route back to its parent within the Loops tab's nested `NavHost`. The comment sheet is registered as a `dialog`; Compose dismisses it back to the loop detail. The system back press always pops by one level — same as the iOS NavigationStack semantics.

## Risks / Trade-offs

- [Pagination cursor dragthrough] → Mitigation: the `LoopCursor` is opaque to consumers; a Firestore API change (e.g., adding `whereEqualTo("active", true)`) only requires re-emitting the cursor. If a stale cursor surfaces (Firestore index recreation, schema backfill), the repository catches the error and emits a `Result.failure(StaleCursor)`; the tab falls back to a fresh fetch.
- [Optimistic like + comment write failures] → Mitigation: every write is wrapped in `runCatching`; failures revert local state and surface an `AsyncValue.Error` for the relevant section. The view model surfaces a transient snackbar via a new `snackbarEvents: SharedFlow<String>` channel.
- [Video playback memory pressure on low-RAM devices] → Mitigation: ExoPlayer is configured with `setHandleAudioBecomingNoisy(true)` and `seekParameters = ` default; on `onRelease` we call `player.release()` from `DisposableEffect`'s `onDispose`. Tested on a Pixel 3-class emulator (4 GB RAM).
- [Missing `loops.orderBy("creationDate").whereEqualTo("active", true)` composite index in `kornerz-72fba`] → Mitigation: log a one-time warning at repo init; the list renders with the default `__name__` ordering until the index is provisioned (iOS does not enforce the index — same fallback as P4).
- [Media3 ExoPlayer introduced ahead of P9] → Trade-off: P9 (Vonage video calling) still needs a baseline video stack; moving ExoPlayer to P5 means P9 builds on an already-exercised player and reduces rework. Documented in the proposal as the single amendment to arch §3.
- [Shared module dependency: `feature/home` depends on `feature/loops` for shared `LoopItemView`] → Trade-off: introduces a one-way edge between two feature modules. Justified because the composable is loops-specific and `feature/home` is the only second consumer beyond `feature/loops` itself; documented in `feature/home/README` as the dependency rationale. Does not violate arch §2.5.
- [Composer crash on long descriptions in `descriptionHtml`] → Mitigation: render with a `WebView` only when the `ExpandableHTMLDescriptionView`-equivalent composable (`descriptionHtml` non-empty) is needed; the WebView is created on-demand inside a `LazyColumn` item with `Modifier.fillMaxWidth().heightIn(max = 200.dp)`. P5 ships a fallback `ExpandableText` (3 lines + "more") for loops with `descriptionHtml` empty or null — same as iOS `ExpandableText` fallback.

## Migration Plan

This is a new feature module + a small refactor of `feature/home`'s `RecentLoopsSection`. Rollout steps:

1. Merge `feature/loops` module behind the existing Home recent loops carousel — Home carousel still renders with the P2 inline row, Loops tab still shows P0 placeholder.
2. Add Media3 ExoPlayer dependency to `gradle/libs.versions.toml` and verify `./gradlew :feature:loops:assemble` succeeds.
3. Wire `LoopRepository` + `LoopItemView`; switch `feature/home/.../RecentLoopsSection` to compose `LoopItemView` from `feature/loops`. Visual parity verified by screenshot diff (P2 4 fixtures vs P5 same fixtures rendered through `LoopItemView`).
4. Enable the Loops tab `composable` to host `LoopsScreen` (list + filter + search). Detail route not wired yet.
5. Wire `loops/{loopId}` nested route + `LoopDetailScreen`. ExoPlayer wired only inside the detail. Single-fire `incrementViewCount(loopId)` on `onAppear`.
6. Wire `loops/{loopId}/comment` `dialog` route + create-comment VM + back-to-detail. Wire `loops/{loopId}/reply/{commentId}` `composable` route + reply VM.
7. Wire `toggleLike()` + `toggleCommentLike()` against `LoopLikeRepository` / `CommentLikeRepository` with optimistic UI and revert-on-failure semantics.
8. Replace the `Coming in Phase 5` snackbar wired through `HomeNavEvent.NavigateToLoopDetail` in `feature/home` (shipped in P2) with a real `navController.navigate("loops/${loop.id}")` call.
9. Run per-phase exit checklist and tag `phase-5-end`.

No data migrations. No schema changes. No backend rollout. Rollback is `git revert` of the merge commit — the P2 `RecentLoopsSection` snackbar is restored and the Loops tab reverts to the P0 placeholder.

## Open Questions

- **`descriptionHtml` rendering** — iOS uses a custom `ExpandableHTMLDescriptionView` (likely a `WKWebView` wrapper). Android equivalent: `WebView` via `AndroidView` host — same set of privacy / a11y caveats as iOS. Will resolve during implementation; if the `descriptionHtml` field is empty for >95 % of loops in the demo data (likely the case per the iOS code paths), the `WebView` only ships behind a feature flag and the plain-text `ExpandableText` is the default.
- **Search results pagination reset** — iOS resets `lastDocument` to `nil` whenever `isSearchActive` flips true. The Android port mirrors that inside `LoopRepository.searchLoops(query, lastDocument)` which accepts `null` to restart. Confirm during implementation that the call site clears `lastDocument` on every search-mode change.
- **`descriptionHtml` HTML sanitizer** — iOS does not appear to sanitize; Android must at minimum set `setAllowFileAccess(false)` and override `shouldOverrideUrlLoading` to no-op. Will add a tiny `HtmlHost` wrapper composable during implementation; flag in PR review.
- **Empty state copy** — iOS uses "No loops available" / "No loops description" but those strings need an extract from the iOS `.strings` file. Will pull from iOS during implementation; if missing, ship English placeholders and file a follow-up.
- **Reply-thread parent comment ID encoding** — iOS passes `parentId` as a JSON object inside `loopComment.parentId`. Android uses `SavedStateHandle["parentCommentId"]` for the nav-arg (matches the data shape); the repository receives a plain `String`. Confirm the `parentId` Firestore write path matches iOS during implementation.
