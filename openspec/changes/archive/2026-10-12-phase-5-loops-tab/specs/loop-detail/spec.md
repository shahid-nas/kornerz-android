# loop-detail Specification

## Purpose
The `loop-detail` capability defines the `loops/{loopId}` destination — a per-loop detail screen with full-width media (image or video), title row with like toggle, expandable description, stats row, comments header, and a safe-area-inset add-comment button that opens the create-comment sheet. This is the Android port of `kornerz/SwiftUI/Dashboard/Loops/LoopDetail/LoopDetailView.swift` and `LoopDetailViewModel.swift`. It owns screens 6.4 (loop detail host) and 6.5 (loop detail view). The detail VM calls `incrementViewCount(loopId)` exactly once per session (per iOS `hasIncrementedViewCount` guard) and exposes `toggleLike()` / `toggleCommentLike()` with optimistic UI.

## References
- [`docs/architecture.md`](../../../docs/architecture.md) — MVVM + Repository + UDF, `AsyncValue<T>`, `Channel<NavEvent>`, `SavedStateHandle` for nav-arg VMs.
- [`docs/phases.md`](../../../docs/phases.md) Phase 5 — codename *Loops*.
- [`docs/screen-inventory.md`](../../../docs/screen-inventory.md) §6.4 – 6.5 — Loop detail.
- `openspec/changes/phase-5-loops-tab/proposal.md` — capability rationale.
- `openspec/changes/phase-5-loops-tab/design.md` — technical decisions D3 (Media3 ExoPlayer), D6 (pagination), D7 (optimistic like UI), D8 (loop comment sheet and reply screen nested routes).
- `kornerz/Kornerz/SwiftUI/Dashboard/Loops/LoopDetail/*` — iOS source-of-truth.
- [`loop-comments`](./loop-comments/spec.md) — comments + reply destinations nested under this detail.
- [`loop-video`](./loop-video/spec.md) — ExoPlayer host composable.

## ADDED Requirements

### Requirement: Loop detail renders a full-width media area at the top
The loop detail screen SHALL render a full-width 250dp media area at the top of the screen. When `Loop.isVideo = true`, the media area SHALL render `VideoPlayerView` from [`loop-video`](./loop-video/spec.md) loading `Loop.videoUrl`. When `Loop.isVideo = false`, the media area SHALL render a Coil image loading `Loop.imageUrl` with shimmer placeholder, scaled to fill, clipped, with a centered 70dp play-circle overlay icon. The media area SHALL render a 55dp circular publisher-avatar `KornerzAvatar` overlay at the bottom-leading corner with 16dp padding — matches iOS `LoopDetailView.swift` lines 187–192. The media area SHALL consume a tap → `LoopDetailEvent.MediaTapped(loopId)` which triggers fullscreen video playback when `isVideo = true`.

#### Scenario: Loop detail with a still image renders the Coil fallback
- **WHEN** the loop detail loads for a loop with `isVideo = false` and `imageUrl = "https://cdn.example.com/i.jpg"`
- **THEN** the media area renders the Coil image scaled to fill, clipped to the 250dp height
- **AND** a centered `play_circle_fill` icon overlay renders
- **AND** tapping the media area does not trigger playback (no player is started)

#### Scenario: Loop detail with a video loop renders ExoPlayer
- **WHEN** the loop detail loads for a loop with `isVideo = true` and `videoUrl = "https://cdn.example.com/v.mp4"`
- **THEN** the media area renders `VideoPlayerView` with the player paused at frame 0
- **AND** tapping the play overlay starts playback
- **AND** tapping the player view pauses / resumes playback

### Requirement: Loop detail renders a title row with publisher, community, date, and like toggle
Below the media area, the loop detail SHALL render a horizontal `Row` containing: a `Column` with the loop title in Satoshi Medium 20sp (2-line max), a sub-row with the publisher nickname in Satoshi Medium 12sp, then the literal word "in" in Satoshi Medium 12sp, then the community name in the per-loop accent color as a tappable text, then the creation date in `timeAgoSinceDate()` format in Satoshi Medium 12sp gray; followed on the trailing edge by a 36dp circular button that renders `heart_circle_fill` (when `isLikedByUser = true`) or `heart_circle` (when `isLikedByUser = false`). While `LoopDetailState.isLikeInProgress = true` the button SHALL render a centered `CircularProgressIndicator` instead. The community-name text is tappable → emits `LoopDetailEvent.KornerTapped(communityId)`. The like button is tappable → emits `LoopDetailEvent.LikeToggled`. Mirrors iOS `LoopDetailView.swift` lines 97–146.

#### Scenario: Loop detail title row renders publisher, community, date, like toggle
- **WHEN** the loop detail loads for a loop with `publisher = "alice"`, `community = "Brazil Travel"`, `creationDate = 3 days ago`, `isLikedByUser = false`
- **THEN** the title row renders "alice   in   Brazil Travel   3d ago" stacked under the title
- **AND** the like button renders `heart_circle`
- **WHEN** the user taps the like button
- **THEN** `LoopDetailState.isLikeInProgress = true`
- **AND** `LoopDetailViewModel.toggleLike()` runs the optimistic flip (`isLikedByUser = true`, `loop.likesCount + 1`)
- **AND** the heart button immediately renders `heart_circle_fill`
- **WHEN** the Firestore write resolves successfully
- **THEN** `isLikeInProgress = false`
- **WHEN** the Firestore write fails
- **THEN** `isLikeInProgress = false` AND the optimistic flip is reverted (`isLikedByUser = false`, `loop.likesCount - 1`)

#### Scenario: Tapping the community name routes to the parent Korner detail
- **WHEN** the user taps the community name in the loop detail title row
- **THEN** `LoopDetailNavEvent.NavigateToKornerDetail(communityId)` is emitted
- **AND** the nested NavHost pushes `kornerz/{communityId}` (reusing the existing destination)

### Requirement: Loop detail renders an expandable description
When `Loop.descriptionHtml` is non-null and non-empty, the description section SHALL render `ExpandableHTMLDescriptionView` (a `WebView` host bounded to `Modifier.fillMaxWidth().heightIn(max = 200.dp)`). When `Loop.descriptionHtml` is null or empty, the description section SHALL render `ExpandableText` with a 2-line initial truncation and a "more" button tinted in the per-loop accent color. Mirrors iOS `LoopDetailView.swift` lines 149–169.

#### Scenario: Loop detail with descriptionHtml non-empty renders the WebView
- **WHEN** the loop loads with `descriptionHtml = "<p>Hello world</p>"`
- **THEN** the description section renders a `WebView` host with the HTML body
- **AND** the WebView disables file access (`setAllowFileAccess(false)`)
- **AND** the WebView intercepts URL clicks via `shouldOverrideUrlLoading` returning true (no external navigation)

#### Scenario: Loop detail without descriptionHtml renders ExpandableText
- **WHEN** the loop loads with `descriptionHtml = null` and `description = "long text here..."`
- **THEN** the description section renders `ExpandableText` with the text body
- **AND** initially the text is truncated to 2 lines with a "more" button
- **WHEN** the user taps "more"
- **THEN** the text expands to its full height
- **AND** the button toggles to "less"

### Requirement: Loop detail renders a stats row with view, comment, and like counts
Below the description, the loop detail SHALL render a horizontal row with two `Divider`s (above and below) and an HStack containing: an eye-fill icon + `loop.viewCounts` in Satoshi 12sp gray, a message-fill icon + `loop.commentsCount` in Satoshi 12sp gray, and a heart icon + `loop.likesCount` in Satoshi 12sp gray. The row SHALL be edge-to-edge (no horizontal insets). Mirrors iOS `LoopDetailView.swift` lines 202–239.

#### Scenario: Stats row renders all three counts
- **WHEN** the loop loads with `viewCounts = 1024`, `commentsCount = 12`, `likesCount = 8`
- **THEN** the stats row renders "👁 1024   💬 12   ❤ 8" edge-to-edge between two dividers
- **AND** the counts are observer-aware — they update when `LoopDetailViewModel` mutates them after a like toggle

### Requirement: Loop detail renders a comments header and the comment list
Below the stats row, the loop detail SHALL render a "Comments" section header in Satoshi Medium 20sp with full-width horizontal padding, followed by a `Divider`, followed by the `LoopCommentView` list. Mirrors iOS `LoopDetailView.swift` lines 240–269.

#### Scenario: Loop detail with at least one comment renders the comment list
- **WHEN** `LoopDetailState.comments` resolves to `AsyncValue.Success([...LoopCommentInfo])` and the list is non-empty
- **THEN** a "Comments" header renders
- **AND** the list renders one `LoopCommentView` per `LoopCommentInfo`, separated by `Divider`s

#### Scenario: Loop detail with zero comments renders the empty state
- **WHEN** `LoopDetailState.comments` resolves to `AsyncValue.Success([])`
- **THEN** the "Comments" header renders
- **AND** below the divider, a single text "No comments yet" in Satoshi 14sp gray renders with full-width horizontal padding
- **AND** no `LoopCommentView` rows render

### Requirement: Loop detail hosts the create-comment sheet via a safe-area-inset add-comment button
The loop detail SHALL render an add-comment row pinned to the bottom safe-area inset (matches iOS `safeAreaInset(edge: .bottom)` in `LoopDetailView.swift` lines 30–35). The row SHALL contain a `Divider`, a `Row` with the current-user `KornerzAvatar` (40dp circular), a capsule-shaped `TextField` with placeholder "Leave a comment" in Satoshi 12sp gray, edge-to-edge horizontal padding + 8dp vertical padding, and a white background. Tapping anywhere on the row SHALL emit `LoopDetailEvent.AddCommentTapped(loopId)` which opens the `loops/{loopId}/comment` dialog (registered in the Loops tab's nested NavHost; see [`loop-comments`](./loop-comments/spec.md)).

#### Scenario: Tapping the add-comment row opens the create-comment sheet
- **WHEN** the user taps anywhere on the safe-area-inset add-comment row
- **THEN** `LoopDetailNavEvent.OpenCreateComment(loopId)` is emitted
- **AND** the nested NavHost pushes the `loops/{loopId}/comment` dialog

### Requirement: LoopDetailViewModel fires incrementViewCount exactly once per session
The `LoopDetailViewModel.init { ... }` block SHALL observe `SavedStateHandle["loopId"]` and SHALL keep a `var hasIncrementedViewCount: Boolean` flag. On first emit of the loop id to the UI's `LaunchedEffect(loopId)` (mirrors iOS `onAppear`), if `hasIncrementedViewCount = false`, the VM SHALL invoke `LoopRepository.incrementViewCount(loopId)` and set the flag to `true`. A subsequent config change (rotation) SHALL NOT re-increment. Matches iOS `LoopDetailViewModel.onAppear()` lines 75–86.

#### Scenario: First appearance increments view count
- **WHEN** the loop detail is opened for `loopId = "l1"` and `hasIncrementedViewCount = false`
- **THEN** `LoopRepository.incrementViewCount("l1")` is invoked exactly once
- **AND** the flag is set to `true`

#### Scenario: Configuration change does not re-increment
- **WHEN** the loop detail rotates from portrait to landscape after the first increment
- **THEN** no additional `incrementViewCount` call is made
- **AND** `hasIncrementedViewCount` remains `true`

### Requirement: Loop detail handles loading, error, and refresh states
The loop detail SHALL render the full content tree redacted (shimmer placeholders matching the iOS `.redacted(reason: .placeholder)` pattern — `mediaSection`, `titleView`, `statsSection`) while `LoopDetailState.detail is AsyncValue.Loading`. The loop detail SHALL render a `KornerzErrorState` with a "Try again" button when the loop fails to load. Pull-to-refresh SHALL re-invoke `LoopDetailViewModel.refresh()` which clears state and re-runs the fetch. Matches iOS `LoopDetailView.swift` lines 44–52 and 36–38.

#### Scenario: Loop detail renders shimmer placeholders during load
- **WHEN** `LoopDetailState.detail is AsyncValue.Loading`
- **THEN** the screen renders the media area + title + stats sections all with redacted placeholder content
- **AND** no description, comments header, or comment list is rendered

#### Scenario: Pull-to-refresh re-fetches loop detail
- **WHEN** the user triggers a pull-to-refresh gesture
- **THEN** `LoopDetailViewModel.refresh()` runs
- **AND** the detail + comments fetch re-fires
- **AND** the screen preserves scroll position
