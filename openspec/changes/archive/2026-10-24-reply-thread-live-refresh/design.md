## Context

The Android reply thread (`feature/loops/.../reply/`) is reached from a comment row's reply affordance on the loop detail screen. Its bottom bar opens the **create-comment sheet in reply mode** — a *separate* nav destination with its own `CreateCommentViewModel` — and the sheet writes the reply through `LoopCommentRepository.createComment(loopId, parentId = commentId, text)`.

The thread's list is read through `LoopCommentRepository.observeReplies(loopId, commentId)`, which delegates to `LoopCommentFirestoreApi.observeReplies`. That function was a `flow { }` around a single `get()`: it emitted the list **once** and completed. `ReplyViewModel.observeReplies()` collected it in `init` and never read again, so the write that happened in the other destination never reached the thread's state.

iOS closes the same gap with a notification: `CreateCommentViewModel.createComment()` calls `sendNotification()` on success, which posts `.refreshLoopDetail`; `LoopReplyViewModel` and `LoopDetailViewModel` both observe it and re-fetch their lists (`fetchData(forceRefresh: true)`). The Android port has no equivalent of that notification, and `NotificationCenter` has no Android counterpart — so the fix has to be expressed in the data layer instead of in the UI layer.

Constraints carried from earlier phases:

- `docs/architecture.md` is canonical and frozen. The UDF contract (stateless content composables, `AsyncValue<T>` state, `Channel<NavEvent>` for one-shot effects) applies verbatim. No new library.
- `core/network` is the only place that talks to Firestore; `feature/*` is imported by `app` only.
- The real `kornerz-72fba` backend, no mock servers. English only.

## Goals / Non-Goals

**Goals:**

- Make a reply posted from the create-comment sheet appear in the thread that opened it — without the thread being told that anything was written.
- Make the loop detail's comment list behave the same way, since it is the same defect on the same read path.
- Keep the read cost of a list unchanged: one batched `Users` read and two batched `CommentLikes` reads per composed list, not per snapshot.

**Non-Goals:**

- Rewriting the create-comment sheet or the reply thread's UI — the pixels and the navigation stay exactly as they are.
- The `commentsCount` / `repliesCount` counters — the backend's Cloud Functions (`onLoopCommentCreate` / `onLoopCommentDelete`) maintain them, and the client reads them.
- Reply-thread pagination, the reply row's swipe-to-delete, and pt-BR localisation.

## Decisions

### Decision 1 — The comment query becomes a Firestore snapshot listener

**Choice.** `LoopCommentFirestoreApi.observeComments` and `observeReplies` are backed by `addSnapshotListener` (through `callbackFlow` + `awaitClose`), so the stream stays open and re-emits on every change to the queried documents.

**Alternatives.**

- *Post a refresh signal from the create-comment sheet — the Android mirror of iOS's `.refreshLoopDetail`.* Rejected as the primary mechanism: it needs a home for the signal (a `SharedFlow` on the repository, or a nav result through `savedStateHandle`), it only covers writes made by *this* app, and it has to be plumbed into every screen that renders a comment list. A listener covers writes from anywhere — the sheet, another device, the Cloud Function's counter updates — with no coordination at all.
- *Re-read the replies when the screen resumes.* Rejected: it fixes the symptom by polling, it misses a reply posted while the thread stays in the foreground, and it needs a lifecycle seam the screen does not have.
- *Have the create-comment sheet push the new `LoopComment` into the thread's state.* Rejected: it couples two ViewModels across two nav destinations, and it does not survive a process death.

**Consequence.** The flow never completes, so `ReplyViewModel.repliesJob` and `LoopDetailViewModel.commentsJob` stay alive for the life of the ViewModel. The listener is detached in `awaitClose` when the collector goes away, so it does not outlive the screen.

### Decision 2 — The listener's failure ends the stream, it does not throw

**Choice.** The listener's error callback emits `Result.failure(...)` and closes the flow; the ViewModels map it to `AsyncValue.Error`, exactly as they already do.

**Rationale.** A Firestore listener does not recover from an error, so there is nothing left to listen to — and closing with the exception instead would tear down the collecting `viewModelScope` job with an uncaught exception. The screens already render the failure state, so the user sees "Failed to load replies" rather than a crash.

**Consequence.** The stream ends after a failure; there is no automatic retry. A retry would be a new requirement, not part of this gap.

### Decision 3 — The duplicate snapshot is skipped

**Choice.** The ViewModels remember the comment list the rows were last composed from, and skip a snapshot that carries the same list.

**Rationale.** Firestore delivers a snapshot twice for a local write — once with the local state, once when the server acknowledges it. Both carry the same documents, so composing them again would produce identical rows at the cost of three batched reads. A snapshot that *does* carry a change (a new reply, a deleted reply, a counter the Cloud Function bumped) still composes.

**Consequence.** `LoopComment` is a data class, so the comparison is structural. A change to a row's *author* or *like count* — which live in other collections — is picked up by the next snapshot that differs, not by this one.

### Decision 4 — The parent comment is observed too

**Choice.** `LoopCommentFirestoreApi.observeComment(commentId)` observes the single document the thread pins above its list.

**Rationale.** The parent row carries the thread's context, and the backend's `onLoopCommentCreate` bumps its `repliesCount` when a reply is posted. Reading it once would leave the thread's parent stale — harmless today (the pinned row hides the reply count), wrong the moment it is shown.

**Consequence.** One more document listener per thread, and the parent row's like state is re-composed from the live `CommentLikes` counts whenever the parent document changes.

### Decision 5 — The rows are keyed by comment id

**Choice.** `ReplyContent` keys its `items` by the comment id, the way the loop detail's list already does.

**Rationale.** The list is live now: a reply arriving at the top shifts every row down. `LoopCommentView` holds a `rememberSwipeToDismissBoxState`, and without a key that state follows the row's *position* — so the swipe that dismisses one row would fire against another.

**Consequence.** `ReplyContent` now matches `LoopDetailContent`'s list contract.

## Risks / Trade-offs

- **A listener holds a Firestore connection open for the screen's lifetime** → it is detached in `awaitClose`, and the ViewModel is cleared when the destination is popped, so the listener's lifetime matches the screen's.
- **A failed listener ends the stream with no retry** → the screen renders `AsyncValue.Error`, the same as the one-shot read's failure did. The user can leave and re-enter the screen to retry.
- **Every snapshot that differs triggers three batched reads** → the duplicate-snapshot guard (Decision 3) keeps a single write to one compose, so the cost matches what the one-shot read cost.
- **The query now needs the listener's server-side index** → it is the same query as before, so it needs the same composite index; a missing index failed the one-shot read the same way.
