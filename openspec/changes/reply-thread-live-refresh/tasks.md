## 1. `core/network` — `LoopCommentFirestoreApi`

- [x] 1.1 Replace the one-shot `get()` in `observeComments` with a Firestore snapshot listener (`addSnapshotListener` → `callbackFlow` + `awaitClose`), keeping the query and its `creationDate` desc ordering
- [x] 1.2 Replace the one-shot `get()` in `observeReplies` the same way, keeping the `parentId == commentId` query
- [x] 1.3 Add `observeComment(commentId)` — the document-level stream for the thread's pinned parent comment
- [x] 1.4 Emit the listener's own failure as `Result.failure` and end the stream, instead of throwing it into the collecting scope
- [x] 1.5 Detach the listener in `awaitClose`, so it lives exactly as long as its collector

## 2. `feature/loops` — the repository

- [x] 2.1 `LoopCommentRepository.observeComment(commentId)` — the passthrough for the new document stream

## 3. `feature/loops` — the reply thread

- [x] 3.1 `ReplyViewModel.observeParent()` — observe the parent comment instead of reading it once, so its `repliesCount` follows the replies posted under it
- [x] 3.2 `ReplyViewModel.observeReplies()` — skip a snapshot that carries the same comments as the previous one, so the duplicate snapshot of a local write does not cost another three batched reads
- [x] 3.3 `ReplyContent` — key the reply rows by their comment id, the way `LoopDetailContent` keys its rows, so a row that shifts down keeps its own swipe state

## 4. `feature/loops` — the loop detail

- [x] 4.1 `LoopDetailViewModel.fetchComments()` — skip a snapshot that carries the same comments as the previous one

## 5. Verification

- [x] 5.1 `./gradlew :core:network:compileDebugKotlin :feature:loops:compileDebugKotlin :feature:shell:compileDebugKotlin :app:compileDevDebugKotlin --offline` — all modules compile
- [ ] 5.2 On device: open a loop, open a comment's thread, post a reply from the sheet, and confirm the reply appears in the thread when the sheet closes
- [ ] 5.3 On device: post a top-level comment from the loop detail's sheet and confirm it appears in the comment list
