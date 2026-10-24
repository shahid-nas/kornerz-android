# loop-comments Specification

## References
- [`docs/architecture.md`](../../../../docs/architecture.md) — MVVM + Repository + UDF, `AsyncValue<T>`, module boundaries, shared-component contracts in `core/ui/components/`.
- `kornerz/Kornerz/SwiftUI/Dashboard/Loops/Comments/LoopCommentView.swift` — iOS source-of-truth for the comment row (diamond avatar, expandable body, icon action row, swipe-to-delete).
- `kornerz/Kornerz/SwiftUI/Dashboard/Loops/LoopDetail/LoopDetailView.swift` — iOS source-of-truth for the trailing swipe action that gates deletion on comment ownership.
- `kornerz/Kornerz/Components/ExpandableText.swift` — iOS source-of-truth for the collapsible body and its overflow heuristic.
- [`openspec/changes/loop-detail-ios-parity/proposal.md`](../proposal.md) — capability rationale for the iOS parity work.
- [`openspec/changes/loop-detail-ios-parity/design.md`](../design.md) — the comment-row restyle and the batched engagement reads.

## MODIFIED Requirements

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
