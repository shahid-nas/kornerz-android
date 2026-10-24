# loop-comments Specification

## References
- [`docs/architecture.md`](../../../../docs/architecture.md) — MVVM + Repository + UDF, `AsyncValue<T>`, module boundaries, `Flow` for streams.
- `kornerz/Kornerz/SwiftUI/Dashboard/Loops/Comments/Create/CreateCommentViewModel.swift` — iOS source-of-truth for the `.refreshLoopDetail` notification posted after a successful create.
- `kornerz/Kornerz/SwiftUI/Dashboard/Loops/Comments/ReplyView/LoopReplyViewModel.swift` — iOS source-of-truth for the thread re-fetching its replies when that notification arrives.
- [`openspec/changes/reply-thread-live-refresh/proposal.md`](../proposal.md) — the defect this closes.
- [`openspec/changes/reply-thread-live-refresh/design.md`](../design.md) — why the comment queries become Firestore snapshot listeners.

## ADDED Requirements

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
