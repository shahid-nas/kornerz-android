# loop-comments Specification

## Purpose
The `loop-comments` capability defines the comment section of the loop detail — the single comment row (6.6), the create-comment sheet (6.7, 6.8), and the reply thread (6.9, 6.10). This is the Android port of `kornerz/SwiftUI/Dashboard/Loops/Comments/LoopCommentView.swift`, `CreateCommentScreen.swift` + `CreateCommentViewModel.swift`, and `LoopReplyScreen.swift` + `LoopReplyViewModel.swift`. The capability owns the `loops/{loopId}/comment` dialog route (create-comment) and the `loops/{loopId}/reply/{commentId}` composable route (reply thread) inside the Loops tab's nested NavHost.
## Requirements
### Requirement: Single comment row renders LoopCommentView with avatar, body, metadata, and affordances
The `LoopCommentView` `@Composable` SHALL render a horizontal `Row` (top-aligned) containing: a **36dp** diamond-clipped avatar for the comment author (`KornerzDiamondImage`, corner radius 8dp, 1:1 with iOS `DiamondImageView`), separated from the content column by a **16dp** gap; and a `Column` containing: a header `Row` with the author **nickname** in Satoshi Medium 12sp on the leading edge and the `creationDate` formatted as `timeAgoSinceDate()` in Satoshi 10sp gray on the trailing edge; an **expandable** comment body (2-line collapsed, "View More" / "View Less" toggle in the accent colour, rendered by the shared `KornerzExpandableText`); and an action `Row` with **25dp** spacing holding: a `message.fill` icon (15dp) + the comment's `repliesCount` in Satoshi 10sp gray, and a `heart` / `heart.fill` icon (15dp) + the comment's `likesCount` in Satoshi 10sp gray, where the heart renders **red** when the comment is liked by the current user and gray otherwise. The row SHALL have **16dp** padding on all sides.

Tapping the heart SHALL invoke `onLike(commentInfo)`; tapping the message icon SHALL invoke `onReply(commentInfo)`. The row SHALL NOT render a permanent delete button. Deletion SHALL be exposed as a **trailing swipe action** — destructive, `trash` icon, full-swipe allowed — and SHALL render **only** when `canDelete = true` (the comment's `userId` equals the signed-in user's `codeUser`). Mirrors iOS `LoopCommentView.swift` and `LoopDetailView.swift` lines 273–308.

#### Scenario: Comment row renders diamond avatar, body, metadata, and affordances
- **WHEN** a `LoopCommentInfo(comment = ..., user = ..., isLikedByUser = false, likesCount = 3)` is rendered
- **THEN** the row renders a 36dp diamond-clipped avatar on the leading edge
- **AND** the author nickname + timestamp render in the header row of the trailing column
- **AND** the comment body renders through the expandable text component
- **AND** the action row renders the reply count and the like count with 15dp icons
- **AND** no permanent delete button renders

#### Scenario: The liked comment heart renders red and filled
- **WHEN** the comment is rendered with `isLikedByUser = true`
- **THEN** the heart icon renders filled and tinted red
- **WHEN** the comment is rendered with `isLikedByUser = false`
- **THEN** the heart icon renders outlined and tinted gray

#### Scenario: Comment row like toggle is optimistic
- **WHEN** the user taps the heart on a comment row
- **THEN** `onLike(commentInfo)` fires
- **AND** the underlying `LoopDetailViewModel.toggleCommentLike(commentInfo)` updates `isLikedByUser` and `likesCount` immediately
- **AND** `CommentLikeRepository.likeComment` or `unlikeComment` is invoked in the background
- **WHEN** the Firestore write fails
- **THEN** the local state is reverted

#### Scenario: Comment row delete is a trailing swipe action gated by canDelete
- **WHEN** `canDelete = false`
- **THEN** swiping the row trailing edge reveals no delete action
- **WHEN** `canDelete = true` (the signed-in user owns the comment)
- **THEN** swiping the row trailing edge reveals a destructive delete action with a `trash` icon
- **WHEN** the user confirms the delete
- **THEN** `LoopCommentRepository.deleteComment(commentId)` is invoked
- **WHEN** the Firestore write succeeds
- **THEN** the comment is removed from the local list
- **AND** `loop.commentsCount` decrements by 1

#### Scenario: The comment body collapses to two lines
- **WHEN** the comment body exceeds the 2-line limit
- **THEN** the body is truncated to 2 lines with a "View More" toggle
- **WHEN** the user taps "View More"
- **THEN** the body expands to its full height and the toggle reads "View Less"

### Requirement: Reply screen renders the parent comment pinned at the top with a replies list below
The reply screen SHALL be registered as a `composable` route at `loops/{loopId}/reply/{commentId}` inside the Loops tab's nested NavHost. The screen SHALL own a `Scaffold` whose `bottomBar` is the **shared add-comment bar** — the same composable the loop detail screen renders: a divider, the signed-in user's 50 dp diamond avatar, and the "Leave a comment" capsule — so the bar is **pinned below the thread** and never scrolls away (the Android equivalent of iOS's `safeAreaInset(edge: .bottom)`).

The screen's content SHALL render, in order: the `ReplyHeader` (back arrow + "Reply" title + the parent comment's author nickname); the **parent comment rendered via `LoopCommentView` with the reply affordance disabled, pinned above the scrolling list** with a `Divider` beneath it (iOS's sticky header section); and a vertical list of reply comments rendered via `LoopCommentView`. The thread SHALL NOT render its own text-input row.

Tapping the add-comment bar SHALL raise `ReplyNavEvent.OpenCreateComment(loopId, parentCommentId)` and the host route SHALL open the create-comment sheet in **reply mode**. The header's back arrow and the system back gesture SHALL both raise `ReplyNavEvent.Pop`, and the host route SHALL pop the thread's back stack.

The screen SHALL NOT reference a `NavController` — the ViewModel owns the nav events and the host route resolves them (architecture §10).

#### Scenario: Reply screen renders the parent comment and replies list
- **WHEN** the reply screen opens for `loopId = "l1"` and `commentId = "c1"` (parent comment with 3 replies)
- **THEN** the header renders a back arrow + "Reply" + "alice" (the parent's author nickname)
- **AND** the parent comment renders **pinned above the list**, with the reply affordance disabled
- **AND** the 3 reply comments render in the list below it
- **AND** the shared add-comment bar renders pinned at the bottom, with the signed-in user's avatar and the "Leave a comment" capsule

#### Scenario: The add-comment bar opens the create-comment sheet in reply mode
- **WHEN** the user taps the add-comment bar on the thread for `loopId = "l1"` and `commentId = "c1"`
- **THEN** `ReplyNavEvent.OpenCreateComment(loopId = "l1", parentCommentId = "c1")` is raised
- **AND** the host route opens the create-comment destination with the parent comment id
- **AND** the sheet renders its "Reply" title and the parent comment
- **WHEN** the user posts from that sheet
- **THEN** `LoopCommentRepository.createComment(loopId = "l1", parentId = "c1", text = "Nice!")` is invoked
- **AND** the thread's reply list shows the new reply

#### Scenario: Back dismisses the thread through one path
- **WHEN** the user taps the header's back arrow
- **THEN** `ReplyNavEvent.Pop` is raised and the host route pops the thread
- **WHEN** the user presses the system back gesture
- **THEN** the same `ReplyNavEvent.Pop` is raised and the host route pops the thread

#### Scenario: An empty thread renders the empty state
- **WHEN** the parent comment carries no replies
- **THEN** the list renders "No comments yet. Be the first!" (iOS `no_comment_message`)

### Requirement: Comment and reply repositories mirror the iOS auth + Firestore paths
The `LoopCommentRepository.createComment(loopId, parentId, text)` method SHALL write to the `LoopComments` collection with `loopId`, `parentId`, `userId = <currentUser.userCode>`, `active = true`, `comment = text`, `creationDate = now()`, `repliesCount = 0`, `likesCount = 0`. The `LoopCommentRepository.deleteComment(commentId)` method SHALL delete the document at `LoopComments/{commentId}` and SHALL decrement `LoopComments/{parentId}.repliesCount` by 1 (clamped at 0) when the deleted comment had a non-null `parentId`. The `LoopCommentRepository.observeComments(loopId)` method SHALL read `LoopComments` where `loopId == loopId AND parentId == null AND active == true`, ordered by `creationDate` **descending** — matching iOS `LoopCommentRepository.fetchAllCommentsForLoop`.

The comment **engagement** reads SHALL be **batched**, not per-comment: `CommentLikeRepository.fetchLikeCounts(commentIds)` SHALL read `CommentLikes` where `commentId in commentIds` in a single query and SHALL return a `Map<String, Int>` of live counts, replacing the denormalized `LoopComment.likesCount`; and `CommentLikeRepository.fetchLikedCommentIds(userId, commentIds)` SHALL read `CommentLikes` where `userId == userId AND commentId in commentIds` in a single query and SHALL return the set of comment ids the user has liked. A 40-comment loop SHALL cost **two** engagement reads, not one read per comment.

#### Scenario: Repository writes a top-level comment with null parentId
- **WHEN** `LoopCommentRepository.createComment(loopId = "l1", parentId = null, text = "Hi")` is invoked
- **THEN** a Firestore document is created at `LoopComments/{generatedId}` with `{"loopId": "l1", "parentId": null, "userId": "<userCode>", "active": true, "comment": "Hi", "creationDate": <ts>, "repliesCount": 0, "likesCount": 0}`
- **AND** `Loop/l1.commentsCount` is incremented by 1

#### Scenario: Repository writes a reply with a parent comment id
- **WHEN** `LoopCommentRepository.createComment(loopId = "l1", parentId = "c1", text = "Nice")` is invoked
- **THEN** a Firestore document is created at `LoopComments/{generatedId}` with `{"loopId": "l1", "parentId": "c1", ...}`
- **AND** `LoopComments/c1.repliesCount` is incremented by 1

#### Scenario: Repository deletes a comment and decrements the parent reply count
- **WHEN** `LoopCommentRepository.deleteComment(commentId = "c1")` is invoked
- **THEN** `LoopComments/c1` is deleted
- **AND** `Loop/l1.commentsCount` is decremented by 1 (clamped at 0)
- **WHEN** the deleted comment has a non-null `parentId`
- **THEN** `LoopComments/{parentId}.repliesCount` is decremented by 1 (clamped at 0)

#### Scenario: Top-level comments are read newest-first and exclude inactive rows
- **WHEN** `observeComments("l1")` is invoked
- **THEN** the query filters `loopId == "l1"`, `parentId == null`, and `active == true`
- **AND** the results are ordered by `creationDate` descending

#### Scenario: Comment engagement is read in two batched queries
- **WHEN** the loop detail resolves 40 comments
- **THEN** `fetchLikeCounts` is invoked **once** with all 40 comment ids
- **AND** `fetchLikedCommentIds` is invoked **once** with all 40 comment ids
- **AND** the live counts replace the denormalized `LoopComment.likesCount` on each row
- **AND** the returned liked-id set drives each row's `isLikedByUser`

### Requirement: Reply list pagination mirrors iOS behavior
The `ReplyViewModel.observeReplies(parentId)` SHALL observe `loopComments` where `parentId == commentId AND loopId == loopId`, ordered by `creationDate` ascending. There is no pagination cursor for the reply thread — iOS loads all replies at once. Each reply SHALL run its like-state + user fetch in parallel (mirroring iOS `LoopDetailViewModel.fetchAllComments` DispatchGroup pattern).

#### Scenario: Reply list renders all replies for a parent comment
- **WHEN** `loopComments` contains 5 reply documents with `parentId = "c1"`
- **THEN** the reply screen renders 5 `LoopCommentView` rows below the parent comment
- **AND** each row shows the reply author + body + metadata + like state

### Requirement: Create-comment sheet posts a top-level comment or a reply
The create-comment screen SHALL be registered as a `composable` route inside the Loops tab's nested NavHost at `loops/{loopId}/comment?parentCommentId={parentCommentId}`. The `parentCommentId` argument SHALL default to the empty string, and its presence SHALL select the sheet's mode — empty ⇒ a **top-level comment**, set ⇒ a **reply** to that comment (iOS `CreateCommentType.newComment` / `.replyToComment`).

In **reply mode** the sheet SHALL render a "Reply" title in its top bar and SHALL render the **parent comment** — via `LoopCommentView` with the reply affordance disabled — above the text box, in place of the loop summary it renders in top-level mode. The parent comment SHALL be resolved from `LoopCommentRepository.fetchCommentWithAuthor(parentCommentId)` — the comment together with its author — and SHALL be rendered in the loop's accent colour.

Tapping Post SHALL invoke `CreateCommentViewModel.createComment()`, which calls `LoopCommentRepository.createComment(loopId, parentId = <null for a top-level comment, the parent comment id for a reply>, text)`. A successful write SHALL award the `ACCESS_COMMENT` Klouders point and SHALL dismiss the sheet. Mirrors iOS `CreateCommentScreen.swift` + `CreateCommentViewModel.swift`.

#### Scenario: A top-level comment posts with a null parent
- **WHEN** the sheet is opened without a parent comment id and the user posts "Hi"
- **THEN** `LoopCommentRepository.createComment(loopId = "l1", parentId = null, text = "Hi")` is invoked
- **AND** the sheet dismisses on success

#### Scenario: A reply posts with the parent comment id
- **WHEN** the sheet is opened with `parentCommentId = "c1"` and the user posts "Nice!"
- **THEN** `LoopCommentRepository.createComment(loopId = "l1", parentId = "c1", text = "Nice!")` is invoked
- **AND** the sheet dismisses on success

#### Scenario: The sheet renders the parent comment in reply mode
- **WHEN** the sheet is opened with `parentCommentId = "c1"`
- **THEN** the top bar renders the title "Reply"
- **AND** the content renders the parent comment's author, body and metadata
- **AND** the loop summary is NOT rendered
- **WHEN** the sheet is opened without a parent comment id
- **THEN** the top bar renders no title
- **AND** the content renders the loop summary

### Requirement: The comment streams are live — a comment written anywhere reaches the list without a manual refresh

`LoopCommentFirestoreApi.observeComments(loopId)` and `LoopCommentFirestoreApi.observeReplies(loopId, parentCommentId)` SHALL be backed by a **Firestore snapshot listener** and SHALL re-emit the queried comments whenever the underlying documents change — a comment posted from the create-comment sheet, a deleted comment, a counter the backend's Cloud Function bumped, or a write from another device. The stream SHALL NOT complete after its first emission, and the listener SHALL be detached when its collector is cancelled.

`LoopCommentFirestoreApi.observeComment(commentId)` SHALL observe the single comment document and SHALL re-emit it whenever it changes, so a comment's `repliesCount` follows the replies posted under it.

The reply thread (`ReplyViewModel`) and the loop detail screen (`LoopDetailViewModel`) SHALL render the comments of the most recent emission. Neither SHALL require a manual refresh, a re-entry into the screen, or a signal from the screen that posted the comment. This is the Android mirror of iOS's `.refreshLoopDetail` notification, which `CreateCommentViewModel` posts after a successful create and which `LoopReplyViewModel` and `LoopDetailViewModel` observe to re-fetch their lists.

A snapshot that carries the same comments as the previous emission MAY be skipped: the rows would be identical, and composing them costs three batched reads.

A failed stream SHALL emit the failure as `AsyncValue.Error` and SHALL end — a Firestore listener does not recover from an error — so the screen renders its error state rather than crashing.

#### Scenario: A reply posted from the sheet appears in the thread
- **WHEN** the user posts "Nice!" from the create-comment sheet the reply thread opened for `loopId = "l1"` and `commentId = "c1"`
- **THEN** `LoopCommentRepository.createComment(loopId = "l1", parentId = "c1", text = "Nice!")` is invoked
- **AND** the thread's reply list shows the new reply
- **AND** the thread does not re-read anything by itself — the write reaches the list through the live stream

#### Scenario: A top-level comment posted from the sheet appears in the loop detail
- **WHEN** the user posts "Hi" from the create-comment sheet the loop detail opened for `loopId = "l1"`
- **THEN** the loop detail's comment list shows the new comment

#### Scenario: A deleted reply leaves the thread's list
- **WHEN** a reply in the thread is deleted
- **THEN** the thread's reply list no longer shows it

#### Scenario: The parent's reply count follows the replies posted under it
- **WHEN** a reply is posted under the parent comment `c1`
- **THEN** the thread's pinned parent comment carries the updated `repliesCount`

#### Scenario: A failed comment stream renders the error state
- **WHEN** the reply thread's comment stream fails
- **THEN** the thread renders "Failed to load replies"
- **AND** the stream ends without tearing down the screen

