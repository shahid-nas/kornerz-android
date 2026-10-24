## Context

The Android reply thread lives in `KornerzAndroid/feature/loops/src/main/kotlin/com/kornerz/feature/loops/reply/`, is registered on the Loops tab's nested `NavHost` at `loops/{loopId}/reply/{commentId}` (`AppShell.kt`), and is reached from a comment row's reply affordance on the loop detail screen.

Its iOS counterpart is `kornerz/Kornerz/SwiftUI/Dashboard/Loops/Comments/ReplyView/` — a SwiftUI view (`LoopReplyScreen.swift`) hosted in a `UIKit` view controller (`LoopReplyController.swift`) that drives it through a coordinator (`LoopReplyCoordinator.swift`) and a view model (`LoopReplyViewModel.swift`).

The two differ in shape:

- iOS puts a **pinned add-comment bar** under the thread (`safeAreaInset(edge: .bottom)`) — the same bar the loop detail screen uses — and tapping it opens the create-comment sheet in **reply mode**. Android puts an editable text field there instead, so the thread owns a second, divergent compose-a-comment path.
- iOS pins the **parent comment** as a sticky header section above the replies. Android renders it as the first item of the scrolling list.
- iOS's create-comment sheet has two modes (`CreateCommentType.newComment` / `.replyToComment`); Android's sheet has one.

Constraints carried from earlier phases:

- `docs/architecture.md` is canonical and frozen. The UDF contract (stateless content composables, `AsyncValue<T>` state, `Channel<NavEvent>` for one-shot effects) applies verbatim. This design introduces **no** new library.
- `core/model` stays Compose-free; `core/network` is the only place that talks to Firestore; `feature/*` is imported by `app` only.
- The real `kornerz-72fba` backend, no mock servers. English only.

## Goals / Non-Goals

**Goals:**

- Make the thread look and behave like iOS's: the shared add-comment bar at the bottom, the parent comment pinned, the replies scrolling beneath.
- Make the reply flow go through **one** sheet — the create-comment sheet — in a reply mode, so a reply written from the thread and a comment written from the detail share the same screen, the same validation, and the same write path.
- Make the thread's writes run against the signed-in user instead of an empty string.

**Non-Goals:**

- Rewriting the loop detail screen or the comment row — the `loop-detail-ios-parity` change owns both.
- Reply-thread pagination, the reply row's swipe-to-delete, and pt-BR localisation.
- Changing the shape of any Firestore document.

## Decisions

### Decision 1 — The bar is a shared component, not a detail-screen private

**Choice.** Move the bar to `feature/loops/components/LoopAddCommentBar.kt` and render it from both `LoopDetailScreen`'s and `ReplyScreen`'s `Scaffold.bottomBar`.

**Alternatives.**

- *Import `detail.LoopDetailAddCommentBar` from the reply package.* Rejected: a screen package is not a component package, and `feature/loops` had no cross-screen imports before this change. The bar is now genuinely shared, so it belongs in `components/` next to `LoopCommentView`.
- *Copy the bar into the reply package.* Rejected: two copies of a pixel-exact component drift the moment one of them is touched — and the requirement is that the two bars be *the same*.

**Consequence.** `detail/LoopDetailAddCommentBar.kt` is deleted; `LoopDetailScreen` and the reply screen both import `components.LoopAddCommentBar`.

### Decision 2 — The sheet's mode travels as a nav argument

**Choice.** `LoopsRoute.Comment` grows an optional `parentCommentId` argument. The sheet reads it from the `SavedStateHandle` in `CreateCommentViewModel` and derives `isReply` from it.

**Alternatives.**

- *A separate route for the reply sheet* (`loops/{loopId}/comment/{commentId}`). Rejected: it duplicates the destination, the arguments, and the sheet's registration, and it splits the "one sheet, two modes" model iOS uses (`CreateCommentType`).
- *A boolean "isReply" argument plus the comment id.* Rejected: two arguments that must agree; one argument cannot be wrong.
- *Passing the resolved `LoopCommentInfo` through the route.* Rejected: the route carries strings, and the comment would have to be re-resolved anyway to be shown with its author and like state.

**Consequence.** The route pattern is `loops/{loopId}/comment?parentCommentId={parentCommentId}`; the argument defaults to the empty string, so the loop detail's existing navigation is unchanged.

### Decision 3 — The parent comment is pinned, not scrolled

**Choice.** `ReplyContent` renders the parent comment (and a divider) **above** the `LazyColumn`, outside the scroll.

**Rationale.** iOS puts it in a `Section` header, which SwiftUI pins. Keeping it in the list means the thread's context scrolls off the screen after two replies — and the sheet's header (which shows the same comment) then refers to something the user can no longer see.

**Consequence.** `ReplyContent` is a `Column` of header → parent comment → divider → replies list. The loading and error states of the parent comment render in the pinned slot.

### Decision 4 — The sheet shows the parent comment and a "Reply" title

**Choice.** In reply mode the sheet's top bar reads "Reply" and the content's header is the parent comment (`LoopCommentView`, reply affordance disabled) instead of the loop summary.

**Rationale.** iOS's `commentInfoView` switches on `viewModel.type` and renders `LoopCommentView(commentInfo:)` for `.replyToComment`. The title is the one addition: iOS's sheet has no title, but Android's top bar has an empty `title` slot, and the user has to be able to tell a reply from a new comment at a glance.

**Consequence.** `CreateCommentState.parentComment` is resolved by the ViewModel on open — through `LoopCommentRepository.fetchCommentWithAuthor(parentCommentId)`, the comment + author join the row needs — and the parent comment's row borrows the loop's accent colour from `LoopDetailInfo.accentColorHex`. The join lives in the repository rather than in the ViewModel so the sheet's constructor keeps its existing dependency set.

### Decision 5 — Back is one path

**Choice.** `ReplyScreen` keeps a `BackHandler` that raises `ReplyEvent.BackTapped`; the ViewModel turns it into `ReplyNavEvent.Pop`; the shell pops the nested back stack.

**Rationale.** The earlier revision armed a `BackHandler` whose body was a no-op and relied on "the shell NavController will pop" — which never happened, so neither the header's back arrow nor the system gesture closed the thread. Routing both through the ViewModel keeps one source of truth for "the user wants to leave", and lets the shell own the actual navigation (architecture §10 — the screen never touches a `NavController`).

**Consequence.** `ReplyScreen` exposes `onNavEvent: (ReplyNavEvent) -> Unit` instead of a bespoke `onDismiss`, matching `LoopDetailScreen` and `NookDetailScreen`.

## Risks / Trade-offs

- The create-comment destination's route pattern changed. The new argument has an empty-string default so the loop detail's navigation is unaffected, but any deep link targeting the old pattern has to be rewritten.
- The thread now depends on the create-comment sheet for composing a reply. If the sheet is ever removed, the thread loses its only write path — the same coupling iOS has (`LoopReplyCoordinator.showAddCommentSheet`).
- The like toggle on a reply now performs a real Firestore write. It was a silent no-op before, so the observable behaviour changes from "nothing happens" to "the heart turns red".
