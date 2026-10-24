## Why

> **See also:** [`docs/architecture.md`](../../../docs/architecture.md) for the MVVM + Repository + UDF contract, `AsyncValue<T>`, `Channel<NavEvent>`, module boundaries, and Hilt scopes.

The reply thread does not show a reply the user just posted.

The thread's replies are read through `LoopCommentRepository.observeReplies(...)`, which is a **one-shot** `get()` wrapped in a `flow { }` — it emits the list once and completes. `ReplyViewModel` subscribes to it in `init` and never reads again, so when the user posts a reply from the create-comment sheet — a *different* nav destination, with its own ViewModel — the write lands in Firestore and nothing tells the thread about it. The thread comes back to the user showing the same stale list it had before the reply was posted.

The `loop-comments` spec already requires the behaviour ("the thread's reply list shows the new reply"), so this is an implementation gap, not a missing requirement. iOS closes the same gap with the `.refreshLoopDetail` notification: `CreateCommentViewModel.sendNotification()` posts it after a successful create, and both `LoopReplyViewModel` and `LoopDetailViewModel` observe it and re-fetch. The Android port has no equivalent.

The same defect sits under the loop detail's comment list: a top-level comment posted from the sheet never appears there either.

## What Changes

- **The comment queries become live.** `LoopCommentFirestoreApi.observeComments` and `observeReplies` are backed by a **Firestore snapshot listener** (`addSnapshotListener` → `callbackFlow`) instead of a one-shot `get()`. The stream stays open and re-emits whenever the queried documents change — the write from the sheet, a delete, another user's reply, another device.
- **The parent comment becomes live too.** `LoopCommentFirestoreApi.observeComment(commentId)` observes the single document the thread pins above its list, so the parent's `repliesCount` (maintained by the backend's Cloud Function) follows the replies posted under it.
- **The screens re-compose only when the source changed.** A snapshot that carries the same comments as the previous one — the local write, then the server's acknowledgement of it — is skipped, so the duplicate snapshot does not cost another three batched reads.
- **The reply list is keyed by comment id.** The rows are keyed the way the loop detail's rows already are, so a row that shifts down when a reply arrives keeps its own swipe state.

## Capabilities

### New Capabilities

<!-- None — this change closes a gap against an existing requirement. -->

### Modified Capabilities

- `loop-comments`: the reply thread's and the loop detail's comment lists are specified as **live** — the comment stream SHALL re-emit when the underlying documents change, so a comment posted from the create-comment sheet appears in the list without a manual refresh.

## Impact

**Modules touched**

| Module | Change |
|---|---|
| `core/network` | `LoopCommentFirestoreApi` — `observeComments` / `observeReplies` become snapshot-listener streams; `observeComment` is added |
| `feature/loops` | `data/LoopCommentRepository` — `observeComment` passthrough; `reply/ReplyViewModel` — the parent and the replies are observed, not read once; `detail/LoopDetailViewModel` — the comment list is observed; `reply/ReplyContent` — the rows are keyed by comment id |

**No new Gradle module.** `callbackFlow` / `awaitClose` come from `kotlinx-coroutines-core`, which is already a dependency.

**No new third-party dependency.**

**Backend impact**

- None. The `LoopComments` documents are written exactly as before — a reply is still `parentId = <comment id>`, `userId = <codeUser>`, `active = true`. Only the *reading* of them changes.

**Risks**

- The comment queries now hold a Firestore listener open for as long as the screen is alive, instead of closing it after one read. The listener is detached in `awaitClose` when the collector goes away, so it lives exactly as long as the ViewModel.
- The listener's own failure ends the stream (a Firestore listener does not recover from an error). The screens already render that failure through `AsyncValue.Error`.
- The query needs the same composite index the one-shot read needed — the listener changes *how* the query is read, not *what* it asks for.

**Out of scope**

- Reply-thread pagination (iOS loads all replies at once; Android already does too).
- The `commentsCount` / `repliesCount` counters — the backend's Cloud Functions maintain them.
