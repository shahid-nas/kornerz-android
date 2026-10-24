## 1. Shared bar — `feature/loops/components`

- [x] 1.1 Move `feature/loops/.../detail/LoopDetailAddCommentBar.kt` to `feature/loops/.../components/LoopAddCommentBar.kt`, renaming the composable to `LoopAddCommentBar` and keeping the pixels identical (divider, 50 dp `KornerzDiamondImage`, `#EBE1D3` capsule carrying "Leave a comment")
- [x] 1.2 Update `detail/LoopDetailScreen.kt` to render `LoopAddCommentBar`
- [x] 1.3 ~~Update the `androidTest` skeleton `feature/loops/src/androidTest/.../detail/LoopDetailContentTest.kt` to the new name~~ — **removed:** the project carries no automated test suite, so the `androidTest` source set no longer exists
- [x] 1.4 Delete `detail/LoopDetailAddCommentBar.kt`

## 2. Route — `feature/loops/LoopsRoute.kt`

- [x] 2.1 Add `LoopsRoute.Comment.ARG_PARENT_COMMENT_ID` and change the pattern to `loops/{loopId}/comment?parentCommentId={parentCommentId}`; `build(loopId, parentCommentId = "")`
- [x] 2.2 Register the argument in `loopsCommentNavArguments` with an empty-string default

## 3. Create-comment sheet — `feature/loops/comment`

- [x] 3.1 Extend `CreateCommentState` with `parentCommentId`, `parentComment: LoopCommentInfo?` and the derived `isReply`
- [x] 3.2 `CreateCommentViewModel` — read `parentCommentId` from the `SavedStateHandle`, seed it into the initial state, resolve the parent comment on open, and post with `parentId = parentCommentId.ifBlank { null }`
- [x] 3.3 `feature/loops/data/LoopCommentRepository` — add `fetchCommentWithAuthor(commentId)`, the comment + author join the sheet's header renders
- [x] 3.4 `CreateCommentContentNew` — render a "Reply" title in the top bar in reply mode, and render the parent comment (`LoopCommentView`, reply affordance disabled) instead of the loop summary

## 4. Reply thread — `feature/loops/reply`

- [x] 4.1 `ReplyState` — drop `text` / `isSubmitting`, add `currentUserCode` and `currentUserAvatarUrl`
- [x] 4.2 `ReplyEvent` — drop `TextChanged` / `SendTapped`, add `AddCommentTapped`
- [x] 4.3 `ReplyNavEvent` — add `OpenCreateComment(loopId, parentCommentId)`
- [x] 4.4 `ReplyViewModel` — observe the session, replace the empty-string `currentUserId` with the resolved `currentUserCode`, raise `OpenCreateComment` from `AddCommentTapped`
- [x] 4.5 `ReplyContent` — pin the parent comment above the list with a divider, render the "No comments yet. Be the first!" empty state, delete the inline text-input row
- [x] 4.6 `ReplyScreen` — own a `Scaffold` whose `bottomBar` is `LoopAddCommentBar`, expose `onNavEvent`, and keep the `BackHandler` raising `ReplyEvent.BackTapped`

## 5. Shell — `feature/shell`

- [x] 5.1 Handle `ReplyNavEvent.Pop` with `nested.popBackStack()` and `ReplyNavEvent.OpenCreateComment` with `nested.navigate(LoopsRoute.Comment.build(loopId, parentCommentId))`

## 6. Verification

- [x] 6.1 `./gradlew :feature:loops:compileDebugKotlin :feature:shell:compileDebugKotlin :app:compileDevDebugKotlin` — all modules compile
