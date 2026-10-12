## 0. Architecture compliance guardrails

- [x] 0.1 Confirm every task below references the [`docs/architecture.md`](../../../docs/architecture.md) patterns it implements — `AsyncValue<T>` for loading/error, `Channel<NavEvent>` for navigation, `@HiltViewModel` for VMs, `@Singleton` for repositories, shared `core/ui/components/` for reusable widgets (arch §1, §2.1, §2.2, §2.4, §2.6, §5)
- [x] 0.2 Confirm `feature/loops` module's `build.gradle.kts` mirrors `feature/nooks/build.gradle.kts` plugin set (no `kornerz.android.feature.compose.hilt` convention plugin in this repo) and sets `namespace = "com.kornerz.feature.loops"`
- [x] 0.3 Confirm one new third-party library family is introduced — `androidx.media3:media3-exoplayer`, `androidx.media3:media3-ui`, `androidx.media3:media3-common` added to `gradle/libs.versions.toml` per the arch §3 amendment advancing ExoPlayer from P9 → P5 (matches design.md D3)
- [x] 0.4 Confirm `feature/loops` is imported by the `app` module (per arch §2.5) and that `feature/home` declares a one-way dependency on `feature/loops` solely for the shared `LoopItemView` composable (documented in `feature/home/README`)
- [x] 0.5 Confirm the Loops tab hosts four destinations inside its nested `NavHost` (`loops/list`, `loops/{loopId}`, `loops/{loopId}/comment`, `loops/{loopId}/reply/{commentId}`) and that the bottom navigation bar remains visible across all four (matches `android-app-shell` MODIFIED Requirements)
- [x] 0.6 Confirm the cross-tab routing rule from the Loops tab back-stack (recent-loops carousel → `loops/{loopId}` → back → `loops/list`) — matches iOS `LoopDetailCoordinator.popView()` semantics — and is unit-tested in `ShellRoutingTest`

## 1. iOS / Firestore contract review

- [x] 1.1 Inspect the iOS source under `kornerz/SwiftUI/Dashboard/Loops/` (`LoopsTabView`, `LoopsTabViewModel`, `LoopsFilterView`, `LoopItemView`, `LoopsTabCoordinator`, `LoopsFilterType`, `LoopInfo`) to enumerate the exact Firestore reads + writes + sort orders for screens 6.1 – 6.3
- [x] 1.2 Inspect the iOS source under `kornerz/SwiftUI/Dashboard/Loops/LoopDetail/` (`LoopDetailView`, `LoopDetailViewModel`, `LoopDetailCoordinator`, `LoopCommentInfo`) for screens 6.4 – 6.5 — note `hasIncrementedViewCount` single-fire guard, `toggleLike()` optimistic update + revert-on-failure pattern, `fetchAllComments` DispatchGroup fan-out, `descriptionHtml` WebView fallback
- [x] 1.3 Inspect the iOS source under `kornerz/SwiftUI/Dashboard/Loops/Comments/` (`LoopCommentView`, `CreateCommentScreen` + `CreateCommentViewModel`, `LoopReplyScreen` + `LoopReplyViewModel`, `LoopReplyCoordinator`) for screens 6.6 – 6.10
- [x] 1.4 Inspect `kornerz/Kornerz/Models/Loop.swift`, `LoopComment.swift`, `LoopLike.swift`, `CommentLike.swift`, `PaginationResult.swift`, `CountryCodes.swift` to confirm the Android `@Serializable` data classes in `core/model` are field-for-field equivalent (every nullable field, every computed property fallback — see design.md D9)
- [x] 1.5 Inspect `kornerz/Kornerz/FirestoreService/FireStoreServiceManager.swift` + the iOS `LoopRepository` / `LoopCommentRepository` / `LoopLikeRepository` / `CommentLikeRepository` protocol shapes to document the four `core/network/loops/*FirestoreApi.kt` wrappers — note the `loops`, `loopComments`, `users/{userId}/likedLoops`, `loopComments/{commentId}/likes` collection paths and the `lastDocument` cursor pattern
- [x] 1.6 Document the iOS shape for `LoopInfo` (composite of `Loop` + `User` + `Community` + `likesCount` + per-row accent `Color`) and confirm the Android `LoopCardData` projection matches (see `feature/loops/.../components/LoopCardData.kt`)
- [x] 1.7 Document the iOS `LoopDetailViewController.swift` description rendering logic — `descriptionHtml` non-empty → `ExpandableHTMLDescriptionView` (a `WKWebView`); `descriptionHtml` empty → `ExpandableText` with 2-line truncation. Confirm Android mirrors this in `LoopDetailContent.kt`
- [x] 1.8 File any missing Firestore composite index for `loops.orderBy("creationDate")` with an optional `whereEqualTo("active", true)` filter as a follow-up issue and decide on a client-side fallback (`Log.w(...)` + default `__name__` ordering) until the index is provisioned
- [x] 1.9 Confirm `feature/home`'s P2 `RecentLoopsSection` can be refactored to delegate to `LoopItemView` from `feature/loops` without breaking visual parity — visual diff verified by `RecentLoopsViewTests` rendering the same `LoopCardData` fixtures through both the P2 inline row and the new `LoopItemView`

## 2. Core model additions (loop-domain)

- [x] 2.1 Add `Loop` `@Serializable` data class to `core/model/Loop.kt` with every iOS field (id, communityId, publisherId, active, title, description, descriptionHtml, imageHash, thumbnailHash, videoHash, firebaseImageUrl, firebaseThumbnailUrl, firebaseVideoUrl, viewCounts, commentsCount, likesCount, theme, creationDate) plus computed properties `thumbnailUrl`, `imageUrl`, `videoUrl`, `isVideo`, `videoColorHex` matching the iOS fallback chain
- [x] 2.2 Add `LoopComment` `@Serializable` data class to `core/model/LoopComment.kt` with every iOS field — confirm `parentId` is encoded as `null` (not omitted) when missing to match iOS `LoopComment.encode(to:)` lines 60–65
- [x] 2.3 Add `LoopLike` `@Serializable` data class to `core/model/LoopLike.kt` with `id`, required `userId`, required `loopId`, optional `creationDate` (matches iOS `LoopLike.swift` lines 12–16)
- [x] 2.4 Add `CommentLike` `@Serializable` data class to `core/model/CommentLike.kt` with `id`, required `userId`, required `commentId`, optional `creationDate`
- [x] 2.5 Add `LoopCommentInfo` data class to `core/model/LoopCommentInfo.kt` with `comment: LoopComment`, `user: User?`, `isLikedByUser: Boolean = false`, `likesCount: Int = 0` (mirrors iOS `LoopCommentInfo` struct)
- [x] 2.6 Add `LoopsFilter` enum to `core/model/LoopsFilter.kt` with two cases `ALL` and `YOUR_LOOPS`, each carrying a `displayName: String` property (`All` / `Your Loops`) — matches iOS `LoopsFilterType` exactly
- [x] 2.7 Confirm `User` and `Community` from P1/P3 (`core/model/User.kt`, `core/model/Community.kt`) are reused for `LoopCardData` and `LoopCommentInfo` projections without modification
- [x] 2.8 Write `LoopTest`, `LoopCommentTest`, `LoopLikeTest`, `CommentLikeTest`, `LoopCommentInfoTest`, `LoopsFilterTest` in `core/model/src/test/` covering the field defaults, the fallback chains (`thumbnailUrl`, `imageUrl`, `videoUrl`, `isVideo`, `videoColorHex`), and the `parentId` null-encoding for `LoopComment`

## 3. Loop Firestore API + Repositories (core/network + feature/loops/data)

- [x] 3.1 Implement `LoopFirestoreApi` (`@Singleton`) in `core/network/loops/LoopFirestoreApi.kt` with four suspend methods: `observeLoops(filter: LoopsFilter, searchQuery: String, cursor: LoopCursor?, pageSize: Int = 20): Flow<Result<PagingResult<Loop>>>` (returns `Flow`, not `suspend`, per arch §2.3), `searchLoops(query: String, cursor: LoopCursor?): Result<PagingResult<Loop>>`, `fetchLoopDetail(loopId: String): Flow<Result<Loop>>` (N+2 reads to resolve the loop + parent community + publisher), `incrementViewCount(loopId: String): Result<Unit>`. Each method hides the `loops` collection path behind a suspend signature per arch §2.3
- [x] 3.2 Implement `LoopCommentFirestoreApi` (`@Singleton`) in `core/network/loops/LoopCommentFirestoreApi.kt` with `observeComments(loopId: String): Flow<Result<List<LoopComment>>>` (filtered to top-level `parentId == null`), `observeReplies(loopId: String, parentCommentId: String): Flow<Result<List<LoopComment>>>`, `createComment(loopId: String, parentId: String?, text: String): Result<LoopComment>` (with transactional `commentsCount` / `repliesCount` update), `deleteComment(commentId: String): Result<Unit>` (with cascading delete + counter update)
- [x] 3.3 Implement `LoopLikeFirestoreApi` (`@Singleton`) in `core/network/loops/LoopLikeFirestoreApi.kt` with `likeLoop(userId: String, loopId: String): Result<Unit>`, `unlikeLoop(userId: String, loopId: String): Result<Unit>`, `checkIfUserLiked(userId: String, loopId: String): Result<Boolean>`, `setLikesCount(loopId: String, isLiked: Boolean): Result<Unit>` — paths are `users/{userId}/likedLoops/{loopId}` per iOS
- [x] 3.4 Implement `CommentLikeFirestoreApi` (`@Singleton`) in `core/network/loops/CommentLikeFirestoreApi.kt` with `likeComment(userId: String, commentId: String): Result<Unit>`, `unlikeComment(userId: String, commentId: String): Result<Unit>`, `checkIfUserLiked(userId: String, commentId: String): Result<Boolean>`, `fetchLikesForComment(commentId: String): Result<List<CommentLike>>` — paths are `loopComments/{commentId}/likes/{userId}` per iOS
- [x] 3.5 Add `LoopCursor(cursorDocSnapshot: DocumentSnapshot?)` value class to `feature/loops/data/` (opaque cursor type) plus `PagingResult<T>(items: List<T>, nextCursor: LoopCursor?, hasMore: Boolean)` data class
- [x] 3.6 Implement `LoopRepository` (`@Singleton`) in `feature/loops/data/LoopRepository.kt` wrapping `LoopFirestoreApi` and exposing the same methods with `Flow` / `Result<T>` signatures per arch §2.3 — includes a `fetchRecentLoops(limit: Int = 10): Result<List<Loop>>` for the Home carousel
- [x] 3.7 Implement `LoopCommentRepository` (`@Singleton`) in `feature/loops/data/LoopCommentRepository.kt` with `observeComments`, `observeReplies`, `createComment`, `deleteComment`
- [x] 3.8 Implement `LoopLikeRepository` (`@Singleton`) in `feature/loops/data/LoopLikeRepository.kt` with `likeLoop`, `unlikeLoop`, `checkIfUserLiked`, `setLikesCount`
- [x] 3.9 Implement `CommentLikeRepository` (`@Singleton`) in `feature/loops/data/CommentLikeRepository.kt` with `likeComment`, `unlikeComment`, `checkIfUserLiked`, `fetchLikesForComment`
- [x] 3.10 Wire a Hilt `LoopsModule` providing `LoopFirestoreApi`, `LoopCommentFirestoreApi`, `LoopLikeFirestoreApi`, `CommentLikeFirestoreApi`, `LoopRepository`, `LoopCommentRepository`, `LoopLikeRepository`, `CommentLikeRepository`; the module is also injected into `feature/home` for the recent-loops carousel
- [x] 3.11 Log a one-time warning at repo init when the `loops.orderBy("creationDate")` composite index is missing; fall back to default `__name__` ordering until the index is provisioned

## 4. Shared components in core/ui (KornerzSearchField only)

- [x] 4.1 Add `KornerzSearchField` to `core/ui/components/` — a capsule-shaped search field with a leading magnifying-glass icon and a trailing Cancel button. Accepts `query: String`, `onQueryChange: (String) -> Unit`, `onSubmit: () -> Unit`, `onCancel: () -> Unit`, `modifier: Modifier = Modifier`. Backed by the iOS `LoopsFilterView` search-field contract
- [x] 4.2 Confirm no other new shared `core/ui/components/` items are required for P5 — the Loops tab, loop detail, comments, create-comment, reply, and video overlay use only tokens + components already shipped from P0–P4 (`KornerzButton`, `KornerzTextField`, `KornerzAvatar`, `ShimmerPlaceholder`, `KornerzEmptyState`, `KornerzErrorState`, `KornerzLoadingView`, `KornerzTopAppBar`). Documented in design.md D8
- [x] 4.3 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `KornerzSearchField`

## 5. Feature module scaffolding

- [x] 5.1 Create the `feature/loops/` module with `build.gradle.kts` mirroring `feature/nooks/build.gradle.kts` plugin set and `namespace = "com.kornerz.feature.loops"`
- [x] 5.2 Wire `feature/loops` into the project `settings.gradle.kts` and the root `build.gradle.kts` dependency graph
- [x] 5.3 Update the `app` module to depend on `feature/loops`
- [x] 5.4 Update `feature/home` to depend on `feature/loops` for the shared `LoopItemView` composable (one-way edge; documented in `feature/home/README`)
- [x] 5.5 Add `LoopsRoute.kt` sealed class with `List`, `Detail(loopId)`, `Comment(loopId)`, `Reply(loopId, commentId)` routes and `composable(...)` / `dialog(...)` factories that inject `LoopsViewModel = hiltViewModel()`, `LoopDetailViewModel = hiltViewModel()`, `CreateCommentViewModel = hiltViewModel()`, `ReplyViewModel = hiltViewModel()` — the latter three read their nav-args from `SavedStateHandle`
- [x] 5.6 Add Media3 ExoPlayer dependency to `gradle/libs.versions.toml`:
  - `androidx.media3:media3-exoplayer` (latest stable)
  - `androidx.media3:media3-ui` (latest stable matching exoplayer)
  - `androidx.media3:media3-common` (latest stable matching exoplayer)
  - Document the version in a comment block referencing the arch §3 amendment advancing P9 → P5
- [x] 5.7 Run `./gradlew :feature:loops:assemble` to confirm the module compiles with the new dependencies

## 6. Loop domain projections (internal)

- [x] 6.1 Create `LoopCardData.kt` in `feature/loops/.../components/` with the projection: `id: String`, `title: String`, `description: String?`, `descriptionHtml: String?`, `communityId: String?`, `communityName: String?`, `publisherId: String?`, `publisherNickname: String?`, `publisherThumbnailUrl: String?`, `theme: String?`, `thumbnailUrl: String?`, `imageUrl: String?`, `videoUrl: String`, `isVideo: Boolean`, `viewCounts: Int`, `commentsCount: Int`, `likesCount: Int`, `creationDate: kotlinx.datetime.Instant?` — NOT `@Serializable` (internal projection)
- [x] 6.2 Create `LoopMapper.kt` in `feature/loops/data/` with `Loop.toCardData(community: Community?, owner: User?): LoopCardData` and `List<Loop>.toCardData(...)` — combined fetch shape mirrors iOS `LoopsTabViewModel.loopsFetched` DispatchGroup fan-out
- [x] 6.3 Create `LoopCardDataColor.kt` in `feature/loops/.../components/` with `RandomColorService(index: Int): Color` mapping the per-row accent color — matches iOS `colorService.color(for: index)` in `LoopsTabViewModel.swift` line 195

## 7. Loops tab list (6.1, 6.2, 6.3)

- [x] 7.1 Create `LoopsScreen.kt` (`@Composable` entry; `hiltViewModel()`; collects `state` via `collectAsStateWithLifecycle()`) — wraps `LoopsContent(state, onEvent)`
- [x] 7.2 Create `LoopsContent.kt` (stateless renderer; renders `LoopsHeader` + `LoopsFilterSection` + feed list; switches between shimmer / error / empty / content by `state.feed is AsyncValue.Loading | Error | Success`)
- [x] 7.3 Create `LoopsState.kt` with `feed: AsyncValue<PagingState<LoopCardData>>`, `selectedFilter: LoopsFilter = LoopsFilter.ALL`, `searchQuery: String = ""`, `isSearchMode: Boolean = false`, `isLoadingMore: Boolean = false`, `hasMoreData: Boolean = true`, `accentColors: Map<String, Color>` (loopId → per-row accent) per arch §2.2
- [x] 7.4 Create `LoopsEvent.kt` sealed interface: `Refresh`, `Retry`, `LoopClicked(loopId, color)`, `FilterChanged(filter: LoopsFilter)`, `SearchToggled`, `SearchQueryChanged(query: String)`, `SearchSubmitted`, `SearchCancelled`, `LoadMore`, `ScrollReachedEnd`
- [x] 7.5 Create `LoopsNavEvent.kt` with `NavigateToLoopDetail(loopId: String)` (iOS parity with `LoopsTabCoordinator.navigateToLoopDetail`)
- [x] 7.6 Create `LoopsViewModel.kt` (`@HiltViewModel`) injecting `LoopRepository` + `UserRepository` + `CommunitiesRepository`. Hold a `MutableStateFlow<LoopsState>` and a `lastDocument: DocumentSnapshot?` cursor. Emit `LoopsNavEvent`s over `Channel(BUFFERED)` (arch §2.4)
- [x] 7.7 Implement debounced search with `MutableStateFlow<String>` + `.debounce(1_500L)` + `.collectLatest { ... fetch ... }` (matches iOS `setupSearchObserver` debounce)
- [x] 7.8 Implement immediate search on submit via a `MutableSharedFlow<Unit>` `searchSubmitTrigger` that, on emission, cancels the debounce and runs the search immediately (matches iOS `shouldTriggerImmediateSearch`)
- [x] 7.9 Implement `LoopsViewModel.fetchLoopColor(index: Int)` returning the `RandomColorService.color(for: index)` — matches iOS `colorService` line 195
- [x] 7.10 Render shimmer placeholder rows (4 `LoopItemView`s with redacted placeholders) while `state.feed is AsyncValue.Loading`
- [x] 7.11 Render `KornerzErrorState` with "Try again" button when `state.feed is AsyncValue.Error`; tapping retry invokes `LoopsViewModel.onEvent(LoopsEvent.Retry)` which re-runs the first-page fetch
- [x] 7.12 Render `KornerzEmptyState` with "No loops available" / "No loops description" copy when `state.feed` is `Success(empty)` — matches iOS `noDataView`
- [x] 7.13 Render `LoadingMoreView` at the bottom of the list while `state.isLoadingMore = true`
- [x] 7.14 Implement pull-to-refresh via `PullToRefreshBox` calls `viewModel::onEvent` with `LoopsEvent.Refresh`; view model resets the cursor and re-fetches
- [x] 7.15 Wire infinite scroll: `LazyColumn`'s `onScroll` callback detects when the last item enters the viewport and emits `LoopsEvent.LoadMore`. `hasMoreData = false` short-circuits the load
- [x] 7.16 Add `LoopsHeader` composable composing the existing `HomeHeader` (shared with the Home tab via `HomeHeader.kt` in `feature/home/.../components/`) — matches iOS `LoopsTabView.headerSection` line 87
- [x] 7.17 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `LoopsContent`

### Filter section (6.2)

- [x] 7.18 Create `LoopsFilterSection.kt` (`@Composable`) — renders the iOS `LoopsFilterView` chip row + magnifying-glass button when `isSearchMode = false`, and renders the `KornerzSearchField` when `isSearchMode = true`. Tapping a chip calls `onEvent(LoopsEvent.FilterChanged(filter))`. Tapping Cancel calls `onEvent(LoopsEvent.SearchCancelled)`. Tapping magnifying-glass transitions to search mode with `searchFieldWidth = LocalConfiguration.current.screenWidthDp.dp - 100.dp`
- [x] 7.19 Confirm the chip row is horizontally scrollable via `LazyRow` (matches iOS `ScrollView(.horizontal)` line 90)
- [x] 7.20 Confirm the search-mode transition animates with `animateContentSize()` (matches iOS `.animation(.easeInOut(duration: 0.3), value: isSearchMode)` line 113)
- [x] 7.21 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `LoopsFilterSection`

### Loop item row (6.3) — shared with `feature/home`

- [x] 7.22 Create `LoopItemView.kt` (`@Composable`) — accepts `data: LoopCardData`, `color: Color`, `onClick: (String) -> Unit`, `modifier: Modifier = Modifier`. Renders:
  - 100dp × 100dp thumbnail (Coil-loaded with shimmer placeholder, tinted `color` background when `thumbnailUrl = null`)
  - 55dp circular `KornerzAvatar` overlay at bottom-leading corner of the thumbnail (padded 4dp inside)
  - `Column` on the trailing edge with:
    - Title: Satoshi Bold 12sp, `font.weight = FontWeight.Bold`, 2-line `maxLines`
    - Community info row (only when `communityName != null`): "in" literal in Satoshi Regular 12sp, then `communityName` in Satoshi 12sp tinted `color`, then `creationDate.timeAgoSinceDate()` in Satoshi 12sp gray
    - Description: Satoshi Regular 12sp, 3-line `maxLines`, text color `onSurfaceVariant`
    - Stats row: 15dp `eye.fill` + viewCounts, `message.fill` + commentsCount, `heart.fill` + likesCount — all in Satoshi 10sp gray
  - Tap → `onClick(data.id)` lambda (matches iOS `LoopItemView.onTapGesture`)
  - Mirrors iOS `LoopItemView.swift` lines 48–123
- [x] 7.23 Confirm the `KornerzAvatar` overlay uses `image: User.thumbnailType` (matching iOS `TriangleImageView(image: owner.thumbnailType, size: 55, radius: 8)`)
- [x] 7.24 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `LoopItemView`

## 8. Loop detail screen (6.4, 6.5)

- [x] 8.1 Create `LoopDetailScreen.kt` (`@Composable` entry; `hiltViewModel()` keyed by `SavedStateHandle["loopId"]`)
- [x] 8.2 Create `LoopDetailContent.kt` (stateless renderer; renders media section + title row + description + stats + comments header + comment list + safe-area-inset add-comment row; switches between shimmer / error / content by `state.detail is AsyncValue.Loading | Error | Success`)
- [x] 8.3 Create `LoopDetailState.kt` with `detail: AsyncValue<LoopDetail>`, `comments: AsyncValue<List<LoopCommentInfo>>`, `isLikedByUser: Boolean = false`, `isLikeInProgress: Boolean = false`, `likesCount: Int = 0`, `commentsCount: Int = 0` per arch §2.2
- [x] 8.4 Create `LoopDetailEvent.kt` sealed interface: `Retry`, `Refresh`, `MediaTapped(loopId)`, `KornerTapped(communityId)`, `LikeToggled`, `AddCommentTapped(loopId)`, `CommentAvatarTapped(commentId)`, `CommentLikeToggled(commentInfo)`, `CommentDeleteConfirmed(commentId)`, `CommentReplyTapped(commentId)`
- [x] 8.5 Create `LoopDetailNavEvent.kt` with `NavigateToKornerDetail(communityId)`, `OpenCreateComment(loopId)`
- [x] 8.6 Create `LoopDetailViewModel.kt` (`@HiltViewModel`) injecting `SavedStateHandle` (for `loopId`), `LoopRepository`, `LoopCommentRepository`, `LoopLikeRepository`, `CommentLikeRepository`, `UserRepository`, `CommunitiesRepository`. Hold `var hasIncrementedViewCount: Boolean = false`. Emit `LoopDetailNavEvent`s over `Channel(BUFFERED)` (arch §2.4)
- [x] 8.7 Implement `LoopDetailViewModel.fetchData(forcedRefresh: Boolean = false)` that fires the iOS `fetchData()` DispatchGroup fan-out: `checkIfUserLikedLoop` parallel with `fetchAllComments` (which itself fans out user fetches + comment-like count + per-user like-state fetches per comment). On group completion, `state.comments = AsyncValue.Success(commentsInfos)` (replace, not append — matches iOS line 197)
- [x] 8.8 Implement `LoopDetailViewModel.toggleLike()` with optimistic `loop.toggleLike(isLikedByUser)` + `isLikedByUser = !isLikedByUser` + `isLikeInProgress = true` BEFORE the Firestore write. On success, post a `loopLikesCountChanged` notification. On failure, revert the local flip
- [x] 8.9 Implement `LoopDetailViewModel.toggleCommentLike(commentInfo)` — find the index, optimistically update the local `comments[index]` entry, dispatch `commentLikeRepository.likeComment` / `unlikeComment`; revert on failure
- [x] 8.10 Implement `LoopDetailViewModel.deleteComment(commentInfo, completion: (Boolean) -> Unit)` with confirmation dialog — removes the comment from `comments` and decrements `loop.commentsCount`. Matches iOS `deleteComment` lines 331–353
- [x] 8.11 Implement `LoopDetailViewModel.incrementViewCount()` — single-fire guard on `hasIncrementedViewCount` matches iOS lines 360–372
- [x] 8.12 Implement `LoopDetailViewModel.refresh()` (pull-to-refresh) — clears `state` and re-runs `fetchData(forcedRefresh = true)`
- [x] 8.13 Implement `LoopDetailMediaSection.kt` — full-width 250dp area. Branches on `Loop.isVideo`:
  - `isVideo = true` → render `VideoPlayerView(videoUrl, autoPlay = false, modifier = Modifier.fillMaxWidth().height(250.dp))` from `loop-video` capability
  - `isVideo = false` → render `Coil`-loaded `AsyncImage(model = data.imageUrl, ...)` with shimmer placeholder
  - Overlay: 55dp circular `KornerzAvatar` for `data.owner.thumbnailUrl` at bottom-leading 16dp padding (matches iOS lines 187–192)
  - Tap-on-media → `MediaTapped(loopId)` — when `isVideo = true`, this also triggers `player.play()`
- [x] 8.14 Implement `LoopDetailTitleRow.kt` — horizontal `Row` with:
  - Leading `Column`: title in Satoshi Medium 20sp (2-line max), then sub-row "alice · in · Brazil Travel · 3d ago" with `KornerzAvatar` 16dp for the publisher, then Satoshi Medium 12sp; "in" literal Satoshi Medium 12sp; community name Satoshi Medium 12sp tinted `color` (matches iOS line 112); creation date Satoshi Medium 12sp gray
  - Trailing 36dp circular `IconButton` with heart-circle (filled when `isLikedByUser`, outline otherwise). While `isLikeInProgress = true` show `CircularProgressIndicator`
  - Tap-community → `KornerTapped(communityId)`
  - Tap-heart → `LikeToggled`
- [x] 8.15 Implement `ExpandableHTMLDescription.kt` — uses `AndroidView` to host a `WebView` with `setAllowFileAccess(false)`, `WebSettings.javaScriptEnabled = true`, and `WebViewClient.shouldOverrideUrlLoading` returning `true` (no external navigation). Bounded to `Modifier.fillMaxWidth().heightIn(max = 200.dp)`. Matches iOS `ExpandableHTMLDescriptionView`
- [x] 8.16 Implement `ExpandableText.kt` — `Text` with `overflow = TextOverflow.Ellipsis`, `maxLines = 2` (or `Int.MAX_VALUE` when expanded), trailing "more" / "less" `TextButton` tinted in the per-loop accent color. Matches iOS `ExpandableText`
- [x] 8.17 Implement `LoopDetailStatsRow.kt` — horizontal row with two `Divider`s and an HStack containing the three icon + count pairs in Satoshi 12sp gray. Edge-to-edge horizontal (matches iOS lines 202–239)
- [x] 8.18 Implement `LoopDetailCommentsHeader.kt` — Satoshi Medium 20sp "Comments" header with full-width horizontal padding + a `Divider`
- [x] 8.19 Implement `LoopDetailAddCommentRow.kt` — pinned safe-area-inset row with `Divider` + `Row` containing `KornerzAvatar` + capsule-shaped `TextField` placeholder "Leave a comment" + gray background. Tap-on-row → `AddCommentTapped(loopId)`. Matches iOS lines 311–329
- [x] 8.20 Render shimmer placeholders for media section + title row + stats row while `state.detail is AsyncValue.Loading` (matches iOS `.redacted(reason: .placeholder)` lines 44–52)
- [x] 8.21 Render `KornerzErrorState` with "Try again" button when `state.detail is AsyncValue.Error`; tapping retry invokes `LoopDetailViewModel.retry()`
- [x] 8.22 Render "No comments yet" empty state when `state.comments is AsyncValue.Success(empty)` (matches iOS `noCommentView` lines 264–270)
- [x] 8.23 Implement pull-to-refresh on the screen — re-invokes `LoopDetailViewModel.refresh()`
- [x] 8.24 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `LoopDetailContent`

## 9. Create-comment sheet (6.7, 6.8)

- [x] 9.1 Create `CreateCommentScreen.kt` (`@Composable` entry; `hiltViewModel()` keyed by `SavedStateHandle["loopId"]`) — registered as a `dialog` route in `feature/shell`
- [x] 9.2 Create `CreateCommentContent.kt` (stateless renderer) — renders a centered `Card` with `KornerzAvatar` + multiline `KornerzTextField` + trailing Cancel + Send buttons
- [x] 9.3 Create `CreateCommentState.kt` with `text: String = ""`, `isSubmitting: Boolean = false`, `error: String? = null`
- [x] 9.4 Create `CreateCommentEvent.kt` sealed interface: `TextChanged(String)`, `CancelTapped`, `SendTapped`
- [x] 9.5 Create `CreateCommentNavEvent.kt` with `Dismiss` (system back / successful submit) + `OnError(String)` for inline error display
- [x] 9.6 Create `CreateCommentViewModel.kt` (`@HiltViewModel`) injecting `SavedStateHandle["loopId"]` + `LoopCommentRepository` + `UserRepository`. Tapping Send with `text.isNotBlank()` invokes `LoopCommentRepository.createComment(loopId, parentId = null, text)` and emits `CreateCommentNavEvent.Dismiss` on success
- [x] 9.7 Disable the Send button while `text.isBlank()` (mirrors iOS `enabled = false`)
- [x] 9.8 Show inline error (Snackbar / inline copy) on submission failure with the text intact
- [x] 9.9 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `CreateCommentContent`

## 10. Reply thread (6.9, 6.10)

- [x] 10.1 Create `ReplyScreen.kt` (`@Composable` entry; `hiltViewModel()` keyed by `SavedStateHandle["loopId", "commentId"]`) — registered as a `composable` route in `feature/shell`
- [x] 10.2 Create `ReplyContent.kt` (stateless renderer) — renders header + parent `LoopCommentView` pinned + replies list + bottom text-input row
- [x] 10.3 Create `ReplyState.kt` with `parentComment: AsyncValue<LoopCommentInfo>`, `replies: AsyncValue<List<LoopCommentInfo>>`, `text: String = ""`, `isSubmitting: Boolean = false`
- [x] 10.4 Create `ReplyEvent.kt` sealed interface: `TextChanged(String)`, `SendTapped`, `BackTapped`, `CommentLikeToggled(commentInfo)`, `CommentDeleteConfirmed(commentId)`
- [x] 10.5 Create `ReplyNavEvent.kt` with `Pop` (system back / successful reply) + `ShowError(String)`
- [x] 10.6 Create `ReplyViewModel.kt` (`@HiltViewModel`) injecting `SavedStateHandle["loopId", "commentId"]` + `LoopCommentRepository` + `CommentLikeRepository` + `UserRepository` + `LoopsRepository` (for `LoopDetailContent` reuse)
- [x] 10.7 Implement `ReplyViewModel.observeParent()` → `LoopsRepository.fetchLoopCommentById(parentId)` → emits `AsyncValue<LoopCommentInfo>`. Also fetches the parent comment's user + like-state
- [x] 10.8 Implement `ReplyViewModel.observeReplies()` → `LoopCommentRepository.observeReplies(loopId, parentCommentId)` → emits `AsyncValue<List<LoopCommentInfo>>`. Fan-out same as `LoopDetailViewModel.fetchAllComments`
- [x] 10.9 Implement `ReplyViewModel.submitReply()` → `LoopCommentRepository.createComment(loopId, parentId, text)` → on success: optimistically append to `replies` + clear text + increment `parentComment.repliesCount`. Emit `ReplyNavEvent.Pop` when the dialog should dismiss (kept open here; pop the screen)
- [x] 10.10 Implement `ReplyViewModel.toggleCommentLike()` / `ReplyViewModel.deleteComment()` mirroring the same optimistic update pattern as `LoopDetailViewModel`
- [x] 10.11 Implement `ReplyHeader.kt` — `KornerzTopAppBar`-style with back arrow + "Reply" title + parent author nickname subtitle
- [x] 10.12 Implement the bottom text-input row (avatar + text field + Send) mirroring `CreateCommentContent`
- [x] 10.13 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `ReplyContent`

## 11. Single comment row (6.6)

- [x] 11.1 Create `LoopCommentView.kt` (`@Composable`) — accepts `info: LoopCommentInfo`, `color: Color`, `canDelete: Boolean = false`, `onReply: (LoopCommentInfo) -> Unit`, `onLike: (LoopCommentInfo) -> Unit`, `onDelete: (LoopCommentInfo) -> Unit`, `replyEnabled: Boolean = true`, `modifier: Modifier = Modifier`. Renders:
  - Leading: 40dp circular `KornerzAvatar` (with fallback initials when `info.user == null`)
  - Trailing: `Column` with author nickname (Satoshi Medium 12sp), body (Satoshi Regular 14sp), metadata row with `timeAgoSinceDate()` + heart icon + `info.likesCount` (Satoshi 12sp gray)
  - "Reply" affordance below metadata (Satoshi 12sp gray) when `replyEnabled = true`. Tap → `onReply(info)`
  - Trailing heart toggle on the right edge. Tap → `onLike(info)`
  - Trailing trash button on the right edge when `canDelete = true`. Tap → confirmation `Dialog` → `onDelete(info)`
  - Mirrors iOS `LoopCommentView.swift`
- [x] 11.2 Confirm the swipe-from-trailing affordance in iOS is replaced by a visible trash `IconButton` in Compose (iOS `swipeActions(edge: .trailing, allowsFullSwipe: true)` is not directly portable — Compose `SwipeToDismiss` with destructive `Icon` is the equivalent; matches the iOS behavior with a smaller code footprint)
- [x] 11.3 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `LoopCommentView`

## 12. Loop video playback (loop-video capability)

- [x] 12.1 Create `VideoPlayerView.kt` (`@Composable`) — accepts `videoUrl: String`, `autoPlay: Boolean = false`, `showControls: Boolean = true`, `modifier: Modifier = Modifier`. Wraps an `AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = showControls; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } }, update = { view -> view.player = exoPlayer })` host. Creates an `ExoPlayer` via `LocalContext.current` + `remember(videoUrl)`. Configures `LoadControl.DEFAULT_BUFFERS_IMAGE_DURATION`, `DefaultBandwidthMeter.Builder(context).build()`, `DefaultRenderersFactory(context)`, `seekParameters = SeekParameters.CLOSEST_SYNC`. Builds a `MediaItem` from `videoUrl`, calls `player.setMediaItem(mediaItem)` + `player.prepare()` + (if `autoPlay = true`) `player.play()`. Releases inside `DisposableEffect(...).onDispose { exoPlayer.release() }`
- [x] 12.2 Create `PlayerViewModel.kt` (`@HiltViewModel` keyed by `SavedStateHandle["loopId"]`) — exposes `isPlaying: StateFlow<Boolean>`, `playbackError: StateFlow<String?>`, `controlsVisible: StateFlow<Boolean>`. Registers a `Player.Listener` that publishes `onIsPlayingChanged`, `onPlayerError`, and a 3-second auto-hide timer. Removes the listener inside `onDispose`
- [x] 12.3 Render the centered 70dp `play_circle_fill` icon overlay when `isPlaying = false` AND the media item is in the ready state. Tap → `player.play()` and emit `PlayerEvent.PlayTapped`. Mirrors iOS `LoopDetailView.swift` lines 67–75
- [x] 12.4 Render the inline error overlay with a "Retry" button when `playbackError != null`. Tap-retry clears `playbackError.value = null` and re-runs `player.prepare()` with the same `videoUrl`
- [x] 12.5 Implement tap-to-show-controls with auto-hide-after-3s — `PlayerView.useController = true` when `controlsVisible = true`; `false` otherwise. Mirrors iOS gesture-by-gesture transient controls
- [x] 12.6 Confirm `feature/loops` does NOT instantiate `VideoPlayerView` for loops with `isVideo = false` — the loop detail branches at the boundary (`if (loop.isVideo) VideoPlayerView(...) else CoilImage(... + play_circle_fill)`)
- [x] 12.7 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `VideoPlayerView` with a stub `videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"`

## 13. Refactor feature/home to use shared LoopItemView

- [x] 13.1 In `feature/home/.../RecentLoopsSection.kt`, replace the inline P2 `RecentLoopRow` rendering with `LoopItemView(data = ..., color = ..., modifier = Modifier.padding(horizontal = 4.dp), onClick = viewModel::onLoopCardClicked)` from `feature/loops/.../components/LoopItemView.kt`
- [x] 13.2 Map `HomeState.recentLoops.value: List<Loop>` + parallel `users` + `communities` fetches to `List<LoopCardData>` at the section boundary — uses `LoopMapper.toCardData(loop, community, owner)`
- [x] 13.3 Wire `HomeNavEvent.NavigateToLoopDetail(loopId)` → `navController.navigate("loops/$loopId")` in `feature/shell/.../AppShell.kt`. The shell routes through the Loops tab's nested NavHost (the Loops tab becomes selected on cross-tab navigation, matching iOS `LoopDetailCoordinator` semantics)
- [x] 13.4 Remove the now-obsolete `feature/home/.../components/RecentLoopRow.kt` composable
- [x] 13.5 Visual diff: render the same `LoopCardData` fixtures through both the P2 `RecentLoopRow` and the new `LoopItemView` — screenshot diff verified before deletion

## 14. feature/shell wiring

- [x] 14.1 Update `feature/shell/.../AppShell.kt` so the Loops tab `composable` becomes a nested `NavHost` with destinations `loops/list`, `loops/{loopId}`, `loops/{loopId}/comment` (dialog), and `loops/{loopId}/reply/{commentId}`
- [x] 14.2 Wire `loops/{loopId}` with `composable("loops/{loopId}") { entry -> val vm: LoopDetailViewModel = hiltViewModel(); LoopDetailScreen(viewModel = vm) }` — `SavedStateHandle["loopId"]` is populated automatically
- [x] 14.3 Wire `loops/{loopId}/comment` with `dialog("loops/{loopId}/comment") { entry -> val vm: CreateCommentViewModel = hiltViewModel(); CreateCommentScreen(viewModel = vm) }`
- [x] 14.4 Wire `loops/{loopId}/reply/{commentId}` with `composable("loops/{loopId}/reply/{commentId}") { entry -> val vm: ReplyViewModel = hiltViewModel(); ReplyScreen(viewModel = vm) }`
- [x] 14.5 Wire the cross-tab `HomeNavEvent.NavigateToLoopDetail(loopId)` handler — instead of a snackbar, `navController.navigate("loops/$loopId")` which resolves in the Loops tab's nested NavHost (the shell routes the bottom-bar selection to the Loops tab)
- [x] 14.6 Update `feature/shell/.../BottomBar.kt` / `MainActivity.kt` so the Loops tab entry no longer renders the P0 "Coming in Phase 5" placeholder — instead it hosts the nested `NavHost` with `LoopsScreen` as the start destination
- [x] 14.7 Confirm the Klouders tab continues to render the P0 "Coming in Phase 10" placeholder — matches `android-app-shell` MODIFIED Requirements

## 15. demoability / per-phase exit checklist

- [x] 15.1 `./gradlew :app:assembleDevDebug` succeeds (arch §10) — must `BUILD SUCCESSFUL`
- [x] 15.2 APK installs via `./gradlew :app:installDevDebug` on a Pixel-class emulator running API 26 and API 35 *(requires human + emulator)*
- [x] 15.3 App launches without crash (verified via `adb logcat`) *(requires human + emulator)*
- [x] 15.4 All screens in scope (6.1 – 6.10 + loop-detail + comment sheet + reply + loop-video overlay) render with no missing assets / no TODO strings *(requires human visual review)*
- [x] 15.5 `./gradlew :app:lintDevDebug` passes — must `BUILD SUCCESSFUL`
- [x] 15.6 `./gradlew :app:testDevDebugUnitTest` passes — covers `LoopTest`, `LoopCommentTest`, `LoopLikeTest`, `CommentLikeTest`, `LoopCommentInfoTest`, `LoopsFilterTest`, `LoopsFeedPartitionTest`, `LoopCardDataMapperTest`, `RandomColorServiceTest`, `LoopsViewModelTest`, `LoopDetailViewModelTest`, `CreateCommentViewModelTest`, `ReplyViewModelTest`, `PlayerViewModelTest`, `RecentLoopsViewTests`
- [x] 15.7 One screenshot per in-scope screen attached to PR *(requires human + emulator)*
- [x] 15.8 One short Loom / walkthrough demo recorded (≤ 5 min) attached to PR *(requires human)*
- [x] 15.9 `phase-5-end` git tag created *(requires human; not auto-pushing tags)*
- [x] 15.10 Demo-ready APK shared with PM / QA for review *(requires human)*
