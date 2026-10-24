## Why

> **See also:** [`docs/architecture.md`](../../../docs/architecture.md) for the MVVM + Repository + UDF contract, `AsyncValue<T>`, `Channel<NavEvent>`, module boundaries, and Hilt scopes.

The Android reply thread (`loops/{loopId}/reply/{commentId}`) does not look like the iOS screen it ports, and — more importantly — it does not *behave* like it.

iOS `LoopReplyScreen.swift` closes the screen with a **pinned add-comment bar** — a divider, the signed-in user's diamond avatar and the "Leave a comment" capsule — in a `safeAreaInset(edge: .bottom)`, outside the scroll. Tapping that bar opens the **create-comment sheet in reply mode** (`LoopReplyCoordinator.showAddCommentSheet(type: .replyToComment(loopId, commentInfo))`); the sheet renders the parent comment above the text box and posts with `parentId = comment.id`. The parent comment itself is a **sticky header section** — the comment plus a divider — so it stays pinned while the replies scroll.

Android renders a **text field + Send button** in the same place. The thread therefore writes its own replies inline instead of going through the create-comment sheet, the parent comment scrolls away with the list instead of staying pinned, and the sheet the user reaches from the loop detail has no reply mode at all — so a reply written from the thread and a reply written from the sheet are different code paths.

Underneath the drift there is a **defect**: `ReplyViewModel.currentUserId` was the empty string, so every write the thread offered — the like toggle on a reply — was a silent no-op.

## What Changes

### A — The thread's bottom bar is the loop detail's bar

- Move `LoopDetailAddCommentBar` out of the `detail` package into `feature/loops/components/LoopAddCommentBar.kt`, so the bar is a **shared** component rather than a detail-screen private one. The pixels are unchanged; the loop detail screen renders the same composable.
- `ReplyScreen` owns a `Scaffold` whose `bottomBar` is that bar — the Android equivalent of iOS's `safeAreaInset(edge: .bottom)`. The bar shows the signed-in user's diamond avatar and the "Leave a comment" capsule.
- The thread's inline text field + Send button row is **deleted**. The reply is composed in the create-comment sheet, exactly as on iOS.

### B — The create-comment sheet gains a reply mode

- `LoopsRoute.Comment` grows an optional `parentCommentId` nav argument: `loops/{loopId}/comment?parentCommentId={parentCommentId}`. Empty ⇒ a top-level comment (the loop detail's path, unchanged); set ⇒ a reply to that comment.
- `CreateCommentState` gains `parentCommentId` and the resolved `parentComment: LoopCommentInfo?`; `isReply` is derived from the former.
- `LoopCommentRepository` gains `fetchCommentWithAuthor(commentId)` — the comment + author join the sheet's header needs, mirroring the one iOS performs while assembling a `LoopCommentInfo`.
- `CreateCommentViewModel` resolves the parent comment on open, and posts with `parentId = parentCommentId` instead of `null`.
- `CreateCommentContentNew` renders a **"Reply"** title in the top bar in reply mode, and shows the **parent comment** (`LoopCommentView`, reply affordance disabled) above the text box instead of the loop summary — iOS `commentInfoView`.

### C — The thread's content matches iOS's layout

- The parent comment moves **out of the scrolling list** and is pinned above it, with a divider, mirroring iOS's sticky header section.
- The empty state renders "No comments yet. Be the first!" (iOS `no_comment_message`).

### D — The thread's writes run against the real user

- `ReplyViewModel` observes the session: `currentUserCode` gates the like toggle, and `currentUserAvatarUrl` feeds the add-comment bar's avatar. The hard-coded empty-string `currentUserId` is gone.

### E — Navigation is wired through nav events

- `ReplyEvent.AddCommentTapped` raises `ReplyNavEvent.OpenCreateComment(loopId, parentCommentId)`; the shell routes it to the create-comment destination with the parent id.
- `ReplyNavEvent.Pop` is handled by the shell (`nested.popBackStack()`), and the thread's own `BackHandler` raises the same event — so the header's back arrow and the system back gesture both dismiss the thread through one path.

## Capabilities

### Modified Capabilities

- `loop-comments`: the reply thread's bottom bar becomes the shared add-comment bar and opens the create-comment sheet in reply mode; the parent comment is pinned; the sheet gains a reply mode carried by a nav argument; the thread's writes run against the signed-in user.

## Impact

**Modules touched**

| Module | Change |
|---|---|
| `feature/loops` | `components/LoopAddCommentBar.kt` (moved out of `detail/`); `reply/` rewritten — screen, content, ViewModel, state, events; `comment/` gains reply mode (state, ViewModel, content); `LoopsRoute.Comment` gains the `parentCommentId` argument |
| `feature/shell` | the reply route handles `ReplyNavEvent.Pop` and `ReplyNavEvent.OpenCreateComment` |

**No new Gradle module.** The thread and the sheet both live in `feature/loops`.

**No new third-party dependency.** The bar is the existing Compose row; the reply mode is a nav argument.

**Backend impact**

- None. `LoopComments` documents are written exactly as before — a reply is still `parentId = <comment id>`, `userId = <codeUser>`, `active = true`. The only change is *which* screen writes it.

**Risks**

- The create-comment destination's route pattern changed (`loops/{loopId}/comment` → `loops/{loopId}/comment?parentCommentId={parentCommentId}`). The argument has an empty-string default, so the loop detail's existing navigation keeps working, but any deep link that targets the old pattern must be updated.

**Out of scope**

- Reply-thread pagination (iOS loads all replies at once; Android already does too).
- The swipe-to-delete affordance on a reply — the row carries the action, but `canDelete` stays `false` on this surface.
- pt-BR localisation.
