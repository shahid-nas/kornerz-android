# loop-comments Specification

## References
- [`docs/architecture.md`](../../../../docs/architecture.md) — MVVM + Repository + UDF, `AsyncValue<T>`, module boundaries, shared-component contracts in `core/ui/components/`.
- `kornerz/Kornerz/SwiftUI/Dashboard/Loops/Comments/ReplyView/LoopReplyScreen.swift` — iOS source-of-truth for the reply thread (pinned parent comment, pinned add-comment bar, reply-mode sheet).
- `kornerz/Kornerz/SwiftUI/Dashboard/Loops/Comments/ReplyView/LoopReplyCoordinator.swift` — iOS source-of-truth for `showAddCommentSheet(type: .replyToComment(loopId, commentInfo))`.
- `kornerz/Kornerz/SwiftUI/Dashboard/Loops/Comments/Create/CreateCommentScreen.swift` + `CreateCommentViewModel.swift` — iOS source-of-truth for the create-comment sheet's two modes.
- [`openspec/changes/reply-thread-ios-parity/proposal.md`](../proposal.md) — capability rationale for the reply-thread parity work.
- [`openspec/changes/reply-thread-ios-parity/design.md`](../design.md) — the shared bar, the nav-argument reply mode, and the pinned parent comment.

## RENAMED Requirements

- FROM: `### Requirement: Create-comment sheet is a dialog route with avatar + text field + send button`
- TO: `### Requirement: Create-comment sheet posts a top-level comment or a reply`

## MODIFIED Requirements

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
