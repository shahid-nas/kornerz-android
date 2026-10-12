# loop-comments Specification

## Purpose
The `loop-comments` capability defines the comment section of the loop detail — the single comment row (6.6), the create-comment sheet (6.7, 6.8), and the reply thread (6.9, 6.10). This is the Android port of `kornerz/SwiftUI/Dashboard/Loops/Comments/LoopCommentView.swift`, `CreateCommentScreen.swift` + `CreateCommentViewModel.swift`, and `LoopReplyScreen.swift` + `LoopReplyViewModel.swift`. The capability owns the `loops/{loopId}/comment` dialog route (create-comment) and the `loops/{loopId}/reply/{commentId}` composable route (reply thread) inside the Loops tab's nested NavHost.

## Requirements

### Requirement: Single comment row renders LoopCommentView with avatar, body, metadata, and affordances
The `LoopCommentView` `@Composable` SHALL render a horizontal `Row` with: a 40dp circular `KornerzAvatar` for the comment author (with fallback initials when `user.thumbnailUrl` is null); a `Column` containing the author nickname in Satoshi Medium 12sp on the leading edge, followed by the comment body in Satoshi Regular 14sp text color, followed by a metadata row with `creationDate` formatted as `timeAgoSinceDate()` in Satoshi 12sp gray and `likesCount` in Satoshi 12sp gray with a heart icon. The `onReply` affordance (Satoshi 12sp "Reply") SHALL render below the metadata, and a trailing heart toggle SHALL render on the right edge for like / unlike. Tap on the heart → `onLike(commentInfo)`; tap on "Reply" → `onReply(commentInfo)`. When `canDelete = true` (the current user is the comment author), a swipe-from-trailing `IconButton` with a trash icon SHALL render — invokes `onDelete(commentInfo)` and shows a confirmation `Dialog` before dispatching. Mirrors iOS `LoopCommentView.swift`.

#### Scenario: Comment row renders avatar, body, metadata, and affordances
- **WHEN** a `LoopCommentInfo(comment = ..., user = ..., isLikedByUser = false, likesCount = 3)` is rendered
- **THEN** the row renders the user avatar on the leading edge
- **AND** the author nickname + body + metadata row renders in the trailing column
- **AND** a "Reply" affordance and a heart toggle render on the trailing edge

#### Scenario: Comment row like toggle is optimistic
- **WHEN** the user taps the heart toggle on a comment row
- **THEN** `onLike(commentInfo)` fires
- **AND** the underlying `LoopDetailViewModel.toggleCommentLike(commentInfo)` updates `isLikedByUser` and `likesCount` immediately
- **AND** `CommentLikeRepository.likeComment` or `unlikeComment` is invoked in the background
- **WHEN** the Firestore write fails
- **THEN** the local state is reverted

#### Scenario: Comment row delete is gated by canDelete
- **WHEN** `canDelete = true` (current user owns the comment)
- **THEN** a trailing trash button renders
- **WHEN** the user taps the trash button
- **THEN** a confirmation `Dialog` renders with "Delete this comment?" copy and Cancel / Delete buttons
- **WHEN** the user taps Delete
- **THEN** `LoopCommentRepository.deleteComment(commentId)` is invoked
- **WHEN** the Firestore write succeeds
- **THEN** the comment is removed from the local list
- **AND** `loop.commentsCount` decrements by 1

### Requirement: Create-comment sheet is a dialog route with avatar + text field + send button
The create-comment screen SHALL be registered as a `dialog` route inside the Loops tab's nested NavHost at `loops/{loopId}/comment`. The dialog content SHALL render a centered `Card` (or rounded background) containing: the current-user `KornerzAvatar` (40dp circular) on the leading edge, a `KornerzTextField` (multiline variant) with placeholder "Leave a comment" in Satoshi 14sp and a min height of 120dp, and a `Row` of trailing actions with a "Cancel" `KornerzButton` (text variant, accent-color tint) and a "Send" `KornerzButton` (filled variant, disabled until `text.isNotBlank()`). Tapping Cancel SHALL call `onDismiss()`. Tapping Send while `text.isNotBlank()` SHALL invoke `CreateCommentViewModel.submit()` which calls `LoopCommentRepository.createComment(loopId, parentId = null, text)`. Mirrors iOS `CreateCommentScreen.swift`.

#### Scenario: Create-comment dialog disables Send until text is non-empty
- **WHEN** the dialog opens and the text field is empty
- **THEN** the Send button is disabled (`enabled = false`)
- **WHEN** the user types "Hello world" into the text field
- **THEN** the Send button becomes enabled
- **WHEN** the user taps Send
- **THEN** `CreateCommentViewModel.submit()` runs
- **AND** `LoopCommentRepository.createComment(loopId, parentId = null, text = "Hello world")` is invoked
- **WHEN** the Firestore write succeeds
- **THEN** the dialog dismisses
- **AND** the comment is appended to the loop detail's local comment list
- **AND** `loop.commentsCount` increments by 1
- **WHEN** the Firestore write fails
- **THEN** an inline error message renders in the dialog
- **AND** the dialog remains open with the text intact

#### Scenario: Cancel dismisses the dialog without submitting
- **WHEN** the user taps Cancel
- **THEN** the dialog dismisses without invoking `submit()`
- **AND** the loop detail's local comment list is unchanged

### Requirement: Reply screen renders the parent comment pinned at the top with a replies list below
The reply screen SHALL be registered as a `composable` route at `loops/{loopId}/reply/{commentId}` inside the Loops tab's nested NavHost. The screen SHALL render a `Column` containing the `ReplyHeader` (back arrow + "Reply" title + parent comment's author nickname) at the top, a `KornerzTopAppBar`-style header background, a `Divider`, the parent comment rendered via `LoopCommentView` pinned at the top with the reply affordance disabled, a `Divider`, and a vertical list of reply comments rendered via `LoopCommentView`. Below the list, a text-input row mirrors the create-comment dialog (avatar + text field + Send button). Tapping Send SHALL invoke `ReplyViewModel.submitReply()` which calls `LoopCommentRepository.createComment(loopId, parentId = commentId, text)`. Mirrors iOS `LoopReplyScreen.swift`.

#### Scenario: Reply screen renders the parent comment and replies list
- **WHEN** the reply screen opens for `loopId = "l1"` and `commentId = "c1"` (parent comment with 3 replies)
- **THEN** the header renders a back arrow + "Reply" + "alice" (the parent's author nickname)
- **AND** the parent comment renders pinned below the divider
- **AND** the 3 reply comments render below the parent comment
- **AND** the bottom text-input row renders with the current-user avatar + text field + Send button

#### Scenario: Submitting a reply appends to the local list and dismisses on success
- **WHEN** the user types "Nice!" into the text field and taps Send
- **THEN** `ReplyViewModel.submitReply()` runs
- **AND** `LoopCommentRepository.createComment(loopId = "l1", parentId = "c1", text = "Nice!")` is invoked
- **WHEN** the Firestore write succeeds
- **THEN** the reply is appended to the local list
- **AND** `parentComment.repliesCount` increments by 1 (so the parent's count stays in sync)
- **AND** the text field clears

### Requirement: Comment and reply repositories mirror the iOS auth + Firestore paths
The `LoopCommentRepository.createComment(loopId, parentId, text)` method SHALL write to `loopComments/{commentId}` with `loopId`, `parentId`, `userId = <currentUser.userCode>`, `active = true`, `comment = text`, `creationDate = now()`, `repliesCount = 0`, `likesCount = 0`. The `LoopCommentRepository.deleteComment(commentId)` method SHALL delete the document at `loopComments/{commentId}` and SHALL also delete any sub-collection documents at `loopComments/{commentId}/likes/*` (mirrors iOS cascading delete). The `LoopCommentRepository.observeComments(loopId)` method SHALL observe `loopComments` where `loopId == loopId AND parentId == null`, ordered by `creationDate` ascending.

#### Scenario: Repository writes a top-level comment with null parentId
- **WHEN** `LoopCommentRepository.createComment(loopId = "l1", parentId = null, text = "Hi")` is invoked
- **THEN** a Firestore document is created at `loopComments/{generatedId}` with `{"loopId": "l1", "parentId": null, "userId": "<uid>", "active": true, "comment": "Hi", "creationDate": <ts>, "repliesCount": 0, "likesCount": 0}`
- **AND** `loops/{l1}.commentsCount` is incremented by 1 via a transactional update

#### Scenario: Repository writes a reply with a parent comment id
- **WHEN** `LoopCommentRepository.createComment(loopId = "l1", parentId = "c1", text = "Nice")` is invoked
- **THEN** a Firestore document is created at `loopComments/{generatedId}` with `{"loopId": "l1", "parentId": "c1", ...}`
- **AND** `loopComments/c1.repliesCount` is incremented by 1 via a transactional update

#### Scenario: Repository deletes a comment and its likes sub-collection
- **WHEN** `LoopCommentRepository.deleteComment(commentId = "c1")` is invoked
- **THEN** `loopComments/c1` is deleted
- **AND** every document under `loopComments/c1/likes/*` is deleted
- **AND** `loops/{l1}.commentsCount` is decremented by 1 (clamped at 0)
- **WHEN** the deleted comment has a non-null `parentId`
- **THEN** `loopComments/{parentId}.repliesCount` is decremented by 1 (clamped at 0)

### Requirement: Reply list pagination mirrors iOS behavior
The `ReplyViewModel.observeReplies(parentId)` SHALL observe `loopComments` where `parentId == commentId AND loopId == loopId`, ordered by `creationDate` ascending. There is no pagination cursor for the reply thread — iOS loads all replies at once. Each reply SHALL run its like-state + user fetch in parallel (mirroring iOS `LoopDetailViewModel.fetchAllComments` DispatchGroup pattern).

#### Scenario: Reply list renders all replies for a parent comment
- **WHEN** `loopComments` contains 5 reply documents with `parentId = "c1"`
- **THEN** the reply screen renders 5 `LoopCommentView` rows below the parent comment
- **AND** each row shows the reply author + body + metadata + like state
