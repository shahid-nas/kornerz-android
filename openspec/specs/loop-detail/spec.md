# loop-detail Specification

## Purpose
The `loop-detail` capability defines the `loops/{loopId}` destination — a per-loop detail screen with full-width media (image or video), title row with like toggle, expandable description, stats row, comments header, and a safe-area-inset add-comment button that opens the create-comment sheet. This is the Android port of `kornerz/SwiftUI/Dashboard/Loops/LoopDetail/LoopDetailView.swift` and `LoopDetailViewModel.swift`. It owns screens 6.4 (loop detail host) and 6.5 (loop detail view). The detail VM calls `incrementViewCount(loopId)` exactly once per session (per iOS `hasIncrementedViewCount` guard) and exposes `toggleLike()` / `toggleCommentLike()` with optimistic UI.
## Requirements
### Requirement: Loop detail renders a full-width media area at the top
The loop detail screen SHALL render a full-width **250dp** media area at the top of the screen, edge-to-edge (no horizontal insets). The area SHALL be painted with the **per-loop accent colour** as its background, so it is the placeholder whenever no image is available — matching iOS `LoopDetailView.swift` lines 172–200, where the fallback is `Rectangle().fill(viewModel.color)`. When `Loop.isVideo = false`, the media area SHALL render a Coil image loading `Loop.iOsImageUrl` (falling back to `Loop.iOsThumbnailUrl`), scaled to fill and clipped. When `Loop.isVideo = true`, the media area SHALL render the **poster image** — `Loop.iOsThumbnailUrl` falling back to `Loop.iOsImageUrl` — with a centred **70dp** `play.circle.fill` badge overlay; the media area SHALL NOT embed an inline player.

The media area SHALL render the resolved owner's avatar as a **55dp** triangle-clipped image at the **bottom-leading** corner with **16dp** padding, using the owner's thumbnail URL and the shared `KornerzTriangleImage` component. The avatar SHALL NOT render when the owner is unresolved.

Tapping the media area SHALL emit `LoopDetailEvent.MediaTapped(loopId)`. For a video loop the screen SHALL open the modal player (see the added requirement "Loop detail opens the video in a modal player"); for a still-image loop the tap SHALL be a no-op.

#### Scenario: Loop detail with a still image renders the poster
- **WHEN** the loop detail loads for a loop with `isVideo = false` and `firebaseImageUrl = "https://cdn.example.com/i.jpg"`
- **THEN** the media area renders the Coil image scaled to fill, clipped to the 250dp height
- **AND** no play badge renders
- **AND** tapping the media area does not open a player

#### Scenario: Loop detail with a video loop renders the poster and a play badge
- **WHEN** the loop detail loads for a loop with `isVideo = true` and `firebaseThumbnailUrl = "https://cdn.example.com/t.jpg"`
- **THEN** the media area renders the thumbnail image scaled to fill, clipped to the 250dp height
- **AND** a centred 70dp `play.circle.fill` badge renders over the image
- **AND** no inline ExoPlayer is hosted inside the media area

#### Scenario: Media area falls back to the accent colour
- **WHEN** the loop detail loads for a loop whose image URL resolves to `null`
- **THEN** the media area renders the per-loop accent colour with no image
- **AND** the play badge renders for a video loop

#### Scenario: Publisher avatar renders in the bottom-leading corner
- **WHEN** the loop detail loads for a loop whose publisher resolves to a user with `firebaseThumbnailUrl = "https://cdn.example.com/u.jpg"`
- **THEN** a 55dp triangle-clipped avatar rendering that thumbnail renders at the bottom-leading corner of the media area
- **AND** the avatar is inset 16dp from the media edges

#### Scenario: Publisher avatar is omitted when the owner is unresolved
- **WHEN** the loop detail loads for a loop whose `publisherId` resolves to no `Users` document
- **THEN** no avatar overlay renders in the media area

### Requirement: Loop detail renders a title row with publisher, community, date, and like toggle
Below the media area, the loop detail SHALL render a horizontal `Row` containing: a `Column` with the loop title in **Satoshi Medium 20sp**; a sub-row with **4dp** spacing between: the **publisher nickname** in Satoshi Medium 12sp, the literal word **"in"** in Satoshi Medium 12sp, the **community name** in the per-loop accent colour as a tappable text, and the creation date in `timeAgoSinceDate()` format in Satoshi Medium 12sp gray. The publisher nickname and the community name SHALL be omitted when they do not resolve. Followed on the trailing edge by a **36dp** circular button that renders `heart_circle_fill` when `isLikedByUser = true` and `heart_circle` when it is `false`.

While `LoopDetailState.isLikeInProgress = true` the button SHALL render a centred `CircularProgressIndicator` instead of the heart, and SHALL be disabled. The community-name text SHALL be tappable → emits `LoopDetailEvent.KornerTapped(communityId)`. The like button SHALL be tappable → emits `LoopDetailEvent.LikeToggled`. The like count SHALL NOT be rendered next to the button — the count lives in the stats row, matching iOS.

#### Scenario: Loop detail title row renders publisher, community, date, like toggle
- **WHEN** the loop detail loads for a loop whose publisher resolves to nickname "alice", whose community resolves to "Brazil Travel", whose `creationDate` is 3 days ago, and `isLikedByUser = false`
- **THEN** the title row renders "alice   in   Brazil Travel   3d ago" stacked under the title
- **AND** the community name renders in the per-loop accent colour
- **AND** the like button renders `heart_circle`

#### Scenario: The like toggle is optimistic and reverts on failure
- **WHEN** the user taps the like button
- **THEN** `LoopDetailState.isLikeInProgress = true`
- **AND** the heart button immediately renders `heart_circle_fill`
- **AND** the stats-row heart renders filled
- **AND** the stats-row like count increments by 1
- **WHEN** the Firestore write resolves successfully
- **THEN** `isLikeInProgress = false`
- **WHEN** the Firestore write fails
- **THEN** `isLikeInProgress = false` AND the optimistic change is reverted (`isLikedByUser = false`, the count decremented by 1)

#### Scenario: Unresolved publisher and community are omitted
- **WHEN** the loop detail loads for a loop whose `publisherId` and `communityId` both resolve to nothing
- **THEN** the meta row renders only the time-ago text
- **AND** neither "in" nor a community name renders

#### Scenario: Tapping the community name routes to the parent Korner detail
- **WHEN** the user taps the community name in the loop detail title row
- **THEN** `LoopDetailNavEvent.NavigateToKornerDetail(communityId)` is emitted
- **AND** the nested NavHost pushes `kornerz/{communityId}` (reusing the existing destination)

### Requirement: Loop detail renders an expandable description
When `Loop.descriptionHtml` is non-null and non-empty, the description section SHALL render the shared `KornerzHtmlText` component — a `WebView` host (1:1 with iOS `ExpandableHTMLDescriptionView` + `WebViewRepresentable`) that shows a 2-line plain-text preview when collapsed and the live HTML when expanded, toggled by a **"See More" / "Show Less"** button in the per-loop accent colour. When `Loop.descriptionHtml` is null or empty, the description section SHALL render the shared `KornerzExpandableText` component with a 2-line initial truncation and a **"View More" / "View Less"** button in the per-loop accent colour.

The overflow threshold SHALL be iOS's: the text is considered overflowing when `text.length / 50 + newlineCount > lineLimit` — **not** a raw character-count threshold. The description section SHALL render nothing when both `descriptionHtml` and `description` are empty.

#### Scenario: Loop detail with descriptionHtml non-empty renders the WebView
- **WHEN** the loop loads with `descriptionHtml = "<p>Hello world</p>"`
- **THEN** the description section renders the `KornerzHtmlText` component in its collapsed state with a 2-line plain-text preview
- **AND** the WebView disables file access
- **AND** the WebView intercepts URL clicks via `shouldOverrideUrlLoading` returning true (no external navigation)
- **WHEN** the user taps "See More"
- **THEN** the live HTML renders in the WebView
- **AND** the button label becomes "Show Less"

#### Scenario: Loop detail without descriptionHtml renders ExpandableText
- **WHEN** the loop loads with `descriptionHtml = null` and `description = "long text here..."`
- **THEN** the description section renders `KornerzExpandableText` with the text body
- **AND** initially the text is truncated to 2 lines with a "View More" button
- **WHEN** the user taps "View More"
- **THEN** the text expands to its full height
- **AND** the button label becomes "View Less"

#### Scenario: The overflow threshold matches iOS
- **WHEN** the description is 100 characters on a single line
- **THEN** the toggle renders (100 / 50 = 2 > the 2-line limit)
- **WHEN** the description is 100 characters spread over 2 newlines
- **THEN** the toggle renders (100 / 50 + 2 = 4 > the 2-line limit)
- **WHEN** the description is 90 characters on a single line
- **THEN** the toggle does not render (90 / 50 = 1, which is not greater than 2)

### Requirement: Loop detail renders a stats row with view, comment, and like counts
Below the description, the loop detail SHALL render a horizontal row with a `Divider` above and a `Divider` below, and SHALL be edge-to-edge (no horizontal insets). The row's content SHALL have **16dp** padding on all sides and SHALL contain, with **25dp** gaps between them: an eye-fill icon (16dp) + `loop.viewCounts` in Satoshi 12sp gray, a message-fill icon (16dp) + `loop.commentsCount` in Satoshi 12sp gray, and a heart icon (16dp) + `loop.likesCount` in Satoshi 12sp gray. The heart SHALL render filled when `isLikedByUser = true` and outlined when it is `false`. Mirrors iOS `LoopDetailView.swift` lines 203–239.

#### Scenario: Stats row renders all three counts
- **WHEN** the loop loads with `viewCounts = 1024`, `commentsCount = 12`, `likesCount = 8`
- **THEN** the stats row renders the eye, message, and heart icons with the counts 1024, 12, and 8
- **AND** the row is edge-to-edge between two dividers

#### Scenario: The stats heart reflects the like state
- **WHEN** the user has liked the loop
- **THEN** the stats-row heart renders filled
- **WHEN** the user has not liked the loop
- **THEN** the stats-row heart renders outlined

#### Scenario: Stats counts are observer-aware
- **WHEN** `LoopDetailViewModel` mutates `likesCount` after a like toggle
- **THEN** the stats row re-renders with the updated count

### Requirement: Loop detail renders a comments header and the comment list
Below the stats row, the loop detail SHALL render a "Comments" section header in **Satoshi Medium 20sp** with full-width horizontal padding, followed by a `Divider`, followed by the `LoopCommentView` list. Each comment row SHALL be followed by a `Divider`. When the comment list is empty, the section SHALL render the text **"No comments yet. Be the first!"** in Satoshi 14sp gray with full-width horizontal padding. Mirrors iOS `LoopDetailView.swift` lines 242–308.

#### Scenario: Loop detail with at least one comment renders the comment list
- **WHEN** `LoopDetailState.comments` resolves to `AsyncValue.Success([...LoopCommentInfo])` and the list is non-empty
- **THEN** a "Comments" header renders
- **AND** the list renders one `LoopCommentView` per `LoopCommentInfo`, separated by `Divider`s

#### Scenario: Loop detail with zero comments renders the empty state
- **WHEN** `LoopDetailState.comments` resolves to `AsyncValue.Success([])`
- **THEN** the "Comments" header renders
- **AND** below the divider, a single text "No comments yet. Be the first!" in Satoshi 14sp gray renders with full-width horizontal padding
- **AND** no `LoopCommentView` rows render

### Requirement: Loop detail hosts the create-comment sheet via a safe-area-inset add-comment button
The loop detail SHALL render an add-comment row pinned **outside** the scrolling content, in the bottom bar above the system navigation inset — matching iOS `safeAreaInset(edge: .bottom)` in `LoopDetailView.swift` lines 30–35. The row SHALL contain a `Divider`, then a `Row` with the current-user diamond avatar (50dp, radius 10) and a capsule-shaped surface filled with `#EBE1D3` carrying the placeholder text "Leave a comment" in Satoshi 12sp gray, with 16dp horizontal and 8dp vertical padding around the row, on a white background.

Tapping anywhere on the row SHALL emit `LoopDetailEvent.AddCommentTapped(loopId)` which opens the `loops/{loopId}/comment` dialog (registered in the Loops tab's nested NavHost; see [`loop-comments`](./loop-comments/spec.md)).

#### Scenario: Tapping the add-comment row opens the create-comment sheet
- **WHEN** the user taps anywhere on the bottom add-comment row
- **THEN** `LoopDetailNavEvent.OpenCreateComment(loopId)` is emitted
- **AND** the nested NavHost pushes the `loops/{loopId}/comment` dialog

#### Scenario: The add-comment row stays visible while the content scrolls
- **WHEN** the user scrolls the comment list to the end
- **THEN** the add-comment row remains pinned above the system navigation inset
- **AND** it does not scroll out of view

#### Scenario: The add-comment row shows the signed-in user's avatar
- **WHEN** a signed-in user with a resolved thumbnail opens the loop detail
- **THEN** the add-comment row renders that thumbnail in the 50dp diamond avatar
- **WHEN** the user is signed out
- **THEN** the add-comment row renders the placeholder mark instead of an image

### Requirement: LoopDetailViewModel fires incrementViewCount exactly once per session
The `LoopDetailViewModel.init { ... }` block SHALL observe `SavedStateHandle["loopId"]` and SHALL keep a `var hasIncrementedViewCount: Boolean` flag. On the first emit of the loop id to the UI's `LaunchedEffect(loopId)` (mirrors iOS `onAppear`), if `hasIncrementedViewCount = false`, the VM SHALL invoke `LoopRepository.incrementViewCount(loopId)` and set the flag to `true`. A subsequent configuration change (rotation) SHALL NOT re-increment.

#### Scenario: First appearance increments view count
- **WHEN** the loop detail is opened for `loopId = "l1"` and `hasIncrementedViewCount = false`
- **THEN** `LoopRepository.incrementViewCount("l1")` is invoked exactly once
- **AND** the flag is set to `true`

#### Scenario: Configuration change does not re-increment
- **WHEN** the loop detail rotates from portrait to landscape after the first increment
- **THEN** no additional `incrementViewCount` call is made
- **AND** `hasIncrementedViewCount` remains `true`

### Requirement: Loop detail handles loading, error, and refresh states
The loop detail SHALL render the media area, the title row, and the stats row redacted (shimmer placeholders matching the iOS `.redacted(reason: .placeholder)` pattern) while `LoopDetailState.detail is AsyncValue.Loading`, and SHALL NOT render the description, the comments header, or the comment list in that state. The loop detail SHALL render a `KornerzErrorState` with a "Try again" button when the loop fails to load. Pull-to-refresh SHALL re-invoke the detail and comment fetch and SHALL preserve the scroll position.

#### Scenario: Loop detail renders shimmer placeholders during load
- **WHEN** `LoopDetailState.detail is AsyncValue.Loading`
- **THEN** the screen renders the media area + title + stats sections all with redacted placeholder content
- **AND** no description, comments header, or comment list is rendered

#### Scenario: Pull-to-refresh re-fetches loop detail
- **WHEN** the user triggers a pull-to-refresh gesture
- **THEN** the detail + comments fetch re-fires
- **AND** the screen preserves scroll position

#### Scenario: A failed load renders the shared error state with a working retry
- **WHEN** `LoopDetailState.detail is AsyncValue.Error`
- **THEN** the screen renders `KornerzErrorState`
- **WHEN** the user taps the retry button
- **THEN** the fetch re-runs and the screen returns to the loading state

### Requirement: Loop detail renders a back affordance over the media area
The loop detail SHALL render a back control that floats over the top-leading corner of the media area, on top of the content rather than in a top bar — iOS makes the navigation bar transparent and hangs a leading control over the media (`LoopDetailController.setupCustomBackButton`), and a top bar would push the full-bleed media down. The control SHALL use the platform chevron (`Icons.AutoMirrored.Filled.ArrowBack`) on a translucent disc so it stays legible over both the accent-coloured placeholder and an arbitrary photo, and SHALL carry a non-empty content description naming the action (per the `app-polish` capability's accessibility requirement). The control SHALL be rendered in every state — loading, error, and loaded — so it is always reachable.

Tapping the control SHALL emit `LoopDetailEvent.BackTapped`, which the ViewModel turns into `LoopDetailNavEvent.Pop`; the host route pops the destination off the tab's own nested `NavHost` back stack, returning the user to the list the detail was opened from. The screen SHALL NOT reference a `NavController` itself (architecture §2.4). The system back gesture SHALL take the same route as the control — both raise `LoopDetailEvent.BackTapped`, so the screen leaves through one event.

#### Scenario: Tapping the back control leaves the loop detail
- **WHEN** the user taps the back control on the loop detail
- **THEN** `LoopDetailEvent.BackTapped` is emitted
- **AND** `LoopDetailNavEvent.Pop` is raised
- **AND** the nested `NavHost` pops the loop detail, returning the user to the list it was opened from

#### Scenario: The back control is reachable in every state
- **WHEN** the loop detail is loading, has failed to load, or is loaded
- **THEN** the back control renders above the content in all three states

#### Scenario: The system back gesture takes the same route
- **WHEN** the user presses the system back button on the loop detail
- **THEN** `LoopDetailEvent.BackTapped` is emitted
- **AND** the detail is popped exactly as it is by the in-app control

### Requirement: Loop detail resolves the loop, its owner, and its community in a single fetch
The loop detail SHALL resolve its three data sources through one repository call, mirroring iOS's `LoopInfo` argument object (`LoopsTabViewModel.swift` lines 14–40). `LoopDetailRepository.fetchLoopDetail(loopId)` SHALL perform, in order: (1) a read of the `Loop` collection resolved by the `id` **field** — `whereEqualTo("id", loopId)` — falling back to `document(loopId)` when the field is absent, matching iOS `APICommunity.swift` line 606; (2) a read of `Users` where `codeUser in [publisherId]`; (3) a read of `Communities` where `communityId in [communityId]`. The result SHALL be a `LoopDetailInfo` carrying the `Loop`, the resolved owner `User?`, the resolved community `Community?`, and the per-loop accent colour as a hex `String`.

The accent colour SHALL be derived from the loop id by the shared iOS palette (`#E27D5A`, `#AD598D`, `#86BFA9`, `#68B6CC`, `#EBAA4A`), cycled by index — the Android counterpart of iOS `RandomColorService.color(for:)`. The same accent colour SHALL be used for the media placeholder, the community-name text, the description toggle, and the comment rows of that screen. A loop that does not exist SHALL produce `AsyncValue.Error`.

#### Scenario: The detail resolves loop, owner, and community in one call
- **WHEN** the loop detail opens for `loopId = "l1"` whose `publisherId = "u1"` and `communityId = "c1"`
- **THEN** `LoopDetailRepository.fetchLoopDetail("l1")` is invoked once
- **AND** the resulting `LoopDetailInfo` carries the loop, the `Users` document whose `codeUser = "u1"`, and the `Communities` document whose `communityId = "c1"`

#### Scenario: The loop is resolved by its id field
- **WHEN** a `Loop` document exists whose `id` field is `"l1"` but whose Firestore document id is `"doc-xyz"`
- **THEN** `fetchLoopDetail("l1")` resolves that document
- **WHEN** no document carries `id = "l1"` but a document exists at `Loop/l1`
- **THEN** `fetchLoopDetail("l1")` resolves that document through the document-id fallback

#### Scenario: An unresolvable owner or community degrades to null
- **WHEN** the loop's `publisherId` matches no `Users` document
- **THEN** `LoopDetailInfo.owner` is `null`
- **AND** the title row omits the nickname

#### Scenario: A missing loop produces an error
- **WHEN** no `Loop` document resolves for the requested id
- **THEN** `LoopDetailState.detail` becomes `AsyncValue.Error`

### Requirement: Loop detail write actions are gated on the signed-in user's code
Every write action on the loop detail — liking or unliking the loop, liking or unliking a comment, and deleting a comment — SHALL be gated on the signed-in user's `codeUser`, resolved from `SessionRepository.data` → `SessionState.Authenticated.user.userCode`. The `LoopDetailViewModel` SHALL NOT use a hard-coded placeholder identity. When no signed-in user with a `codeUser` resolves, the like toggle SHALL be a no-op and the screen SHALL NOT emit a write.

The comment delete affordance SHALL be offered **only** for comments whose `comment.userId` equals the signed-in user's `codeUser`, mirroring iOS `LoopDetailViewModel.isCommentOwnedByUser` and the `.swipeActions` guard in `LoopDetailView.swift` lines 273–308.

#### Scenario: The like toggle writes under the signed-in user's code
- **WHEN** a signed-in user with `userCode = "u1"` taps the like button
- **THEN** `LoopLikeRepository.likeLoop(userId = "u1", loopId = "l1")` is invoked
- **AND** the `LoopLikes` document is written with `userId = "u1"`

#### Scenario: The like toggle is a no-op when signed out
- **WHEN** no signed-in user with a `codeUser` resolves and the user taps the like button
- **THEN** no `LoopLikes` document is written
- **AND** the like state does not change

#### Scenario: Comment deletion is offered only for the user's own comments
- **WHEN** the signed-in user's `codeUser` is `"u1"`
- **THEN** a comment authored by `"u1"` offers the delete swipe action
- **AND** a comment authored by `"u2"` does not offer the delete swipe action

### Requirement: Loop detail awards a Klouders point when the loop is opened
When the loop detail resolves a loop for a signed-in user, the screen SHALL award **1** Klouders point by writing a `Gamefication` document with `gameficationAction = "ACCESS_LOOP"`, `gameficationQtdPts = 1`, `gameficationDateHora = now`, `gameficationUserId = <the signed-in user's codeUser>`, and `gameficationReferenceId = <loopId>`. Mirrors iOS `LoopDetailController.viewDidLoad` → `GamificationService.addPoints(action: .accessLoop, points: 1, referenceId: loopId)`.

The write SHALL NOT block rendering, SHALL NOT be retried on configuration change, and SHALL be skipped when no signed-in user with a `codeUser` resolves.

#### Scenario: Opening a loop writes an ACCESS_LOOP point
- **WHEN** a signed-in user with `userCode = "u1"` opens the loop detail for `loopId = "l1"`
- **THEN** a `Gamefication` document is written with `gameficationAction = "ACCESS_LOOP"`, `gameficationQtdPts = 1`, `gameficationUserId = "u1"`, and `gameficationReferenceId = "l1"`

#### Scenario: No point is awarded when signed out
- **WHEN** no signed-in user with a `codeUser` resolves and the loop detail opens
- **THEN** no `Gamefication` document is written

### Requirement: Loop detail opens the video in a modal player and awards a replay point
When the user taps the media area of a loop whose `isVideo = true`, the screen SHALL open the video in a **modal** player rather than hosting an inline player inside the scroll — matching iOS, which presents an `AVPlayerViewController` from `UIViewController.playLoopVideo(url:)`. The tap SHALL be a no-op when `firebaseVideoUrl` is empty.

Opening the modal player SHALL award **1** Klouders point by writing a `Gamefication` document with `gameficationAction = "ACCESS_REPLAYS"`, `gameficationQtdPts = 1`, and `gameficationReferenceId = <the video URL>`, mirroring iOS `UIViewController.playLoopVideo`. The point SHALL be skipped when no signed-in user with a `codeUser` resolves.

#### Scenario: Tapping a video loop opens the modal player
- **WHEN** the user taps the media area of a loop with `isVideo = true`
- **THEN** the screen opens the video in a modal player
- **AND** the media area does not host an inline player

#### Scenario: Tapping a still-image loop does nothing
- **WHEN** the user taps the media area of a loop with `isVideo = false`
- **THEN** no player opens

#### Scenario: Replaying a video awards a replay point
- **WHEN** the user opens the modal player for a loop with `firebaseVideoUrl = "https://cdn.example.com/v.mp4"`
- **THEN** a `Gamefication` document is written with `gameficationAction = "ACCESS_REPLAYS"`, `gameficationQtdPts = 1`, and `gameficationReferenceId = "https://cdn.example.com/v.mp4"`

