## Context

The Android Loops tab has a Loop Detail screen (`KornerzAndroid/feature/loops/src/main/kotlin/com/kornerz/feature/loops/detail/`), a `LoopDetailRepository` seam in `feature/loops/data/`, and the Firestore plumbing in `core/network/.../loops/`. The screen renders, the route is registered in the shell's nested NavHost (`AppShell.kt:743-761`), and the create-comment and reply destinations hang off it.

It is also the least faithful screen in the app. The iOS source (`kornerz/Kornerz/SwiftUI/Dashboard/Loops/LoopDetail/`) is a SwiftUI view hosted in a `UIKit` view controller, and its layout is materially different from Android's: iOS resolves the loop, its owner, and its community **before** pushing the screen and passes them in as a `LoopInfo` argument (`LoopsTabViewModel.swift:14-40`); Android re-fetches only the loop and therefore cannot show a nickname or a community name; iOS threads a **per-loop accent colour** through the description toggle, the community link, and the comment rows, while Android hard-codes `#5D47ED` in the media area and derives a *different* colour per comment from a hash; iOS's add-comment bar is a `safeAreaInset(edge: .bottom)` outside the scroll, while Android renders it as the last `LazyColumn` item; iOS's description prefers `descriptionHtml` rendered in a `WKWebView`, while Android renders only the plain branch.

Three defects are more serious than cosmetic drift:

1. **`LoopDetailViewModel.currentUserId` is the empty string.** Every write the screen offers — like, unlike, comment-like, comment-unlike — is gated on that value and is therefore a **silent no-op**. The like button flips optimistically and then reverts, because the Firestore write never happens.
2. **Comment engagement is never fetched.** `isLikedByUser` is hard-wired to `false` on every row and the live `CommentLikes` count is never read; the denormalized `LoopComment.likesCount` is used instead, which the iOS screen explicitly overwrites with the live count (`LoopDetailViewModel.swift` `setLikeCount`).
3. **`LoopDetailNavEvent.NavigateToKornerDetail` is dropped by the shell** (`AppShell.kt:757` — `/* P3 korner deep link */`), so the community link is inert.

Two more parity gaps sit outside the screen but shape it: iOS's `Gamefication` write path (three point-awarding triggers that Android's read-only `GamificationRepository` cannot perform), and the shell's bottom-bar behaviour for the detail route.

Constraints carried from earlier phases:

- `docs/architecture.md` is canonical and frozen. The UDF contract (`AsyncValue<T>` state, `Channel<NavEvent>` for one-shot effects, stateless composables), the module-boundary rule, the Hilt scope table, and the §3 library catalog all apply verbatim. A new library requires an OpenSpec amendment to that document — **this design introduces none**.
- `core/model` is Compose-free. `core/network` is the only place that talks to Firestore.
- `feature/*` is imported by `app` only (in practice via `feature/shell`'s tab-graph seam), enforced by the P10 ArchUnit test (`app/src/test/kotlin/com/kornerz/kornerz/ModuleBoundaryArchTest.kt`).
- Testing is JVM-unit (`mockk`, `turbine`) plus `androidTest` Compose UI tests. There is no Robolectric and no Hilt test harness.
- The real `kornerz-72fba` backend, no mock servers. English only.

## Goals / Non-Goals

**Goals:**

- Make the Android Loop Detail screen a **1:1 functional and visual port** of the iOS screen — same layout order, same metrics, same affordances, same behaviour.
- Make every write on the screen work against the **real signed-in user**, resolved from the session.
- Close the three behavioural defects above (dead like button, dead comment engagement, dropped navigation event).
- Add the Klouders point awards iOS performs on loop open, video replay, and comment creation, so an Android user can appear on the leaderboard P10 shipped.
- Keep the Android architecture exactly as `docs/architecture.md` mandates — the iOS screen's *shape* changes; the iOS screen's *patterns* do not.

**Non-Goals:**

- Sharing, reporting, saving, and owner-side loop deletion. iOS's `shareLoop` / `reportLoop` are declared, implemented, and never called; iOS has no delete affordance on the detail screen.
- Rewriting the reply thread (`loops/{loopId}/reply/{commentId}`) or the create-comment dialog. They exist from P5 and are separate surfaces; only the *comment row* they share is restyled.
- pt-BR localisation.
- Comment pagination. iOS loads all top-level comments in one query and Android will do the same.
- The `Gamefication` **read** path. P10 owns it and this change does not alter it.
- Any change to the `Loop`, `LoopComments`, `Users`, `Communities`, `CommentLikes`, or `Gamefication` document shapes.

## Decisions

### Decision 1 — The screen resolves its own data through a new `LoopDetailRepository`

**Choice.** Add `LoopDetailRepository` to `core/network`, exposing `suspend fun fetchLoopDetail(loopId: String): Result<LoopDetailInfo>`. It performs three reads in sequence: the `Loop` document, the owner's `Users` document, and the community's `Communities` document. `LoopDetailInfo` (in `core/model`) carries the `Loop`, the `owner: User?`, the `community: Community?`, and the `accentColorHex: String`.

**Alternatives.**

- *Pass the resolved bundle through the nav argument.* This is what iOS does, and it is the "purest" port. Rejected on Android for two reasons. First, a nav argument is a `String`; the bundle would have to be serialised into the route, which means the screen would depend on the *caller* having resolved the owner and community — and the callers are the Loops list, the Home carousel, the Korner detail, and the deep-link handler, each of which would need the same join, duplicating it four times. Second, iOS gets away with it because every caller already has the `LoopInfo` in hand; Android's Loops list has a `LoopCardData` projection that deliberately does not carry the owner `User` or the community `Community`, and the deep-link handler has only an id. The screen is the only place where all three ids are known.
- *Fetch the owner and community lazily, only when the title row needs them.* Rejected: the title row needs them on the first frame, so a lazy fetch would show a placeholder and then pop. iOS shows them immediately.
- *Extend the existing `LoopRepository` in `feature/loops/data`.* Rejected: that repository is a thin wrapper over `LoopFirestoreApi` with a loops-only responsibility. The three-collection join belongs in `core/network`, next to the Firestore APIs, per the layering in `docs/architecture.md` §1.

**Consequence.** `feature/loops/detail/LoopDetailViewModel` injects `LoopDetailRepository` instead of `LoopRepository` + `UsersRepository` + `CommunitiesRepository` for the detail path. `LoopRepository` stays for `incrementViewCount` and the list path.

### Decision 2 — The `Loop` document is resolved by its `id` field, with a document-id fallback

**Choice.** `LoopDetailRepository` reads the `Loop` collection with `whereEqualTo("id", loopId)` first, and falls back to `document(loopId)` when the field query returns nothing. The same resolution is applied inside `LoopFirestoreApi.fetchLoopDetail`, so the list path and the detail path agree.

**Rationale.** iOS's deep-link handler resolves the loop with `Loop.whereField("id", isEqualTo: loopId)` (`APICommunity.swift:606`) — a query on a **field**, not on the document key. Android's `LoopFirestoreApi.fetchLoopDetail` reads `collection.document(loopId)`. If the backend's `Loop` documents have an `id` field that differs from the Firestore document key, a push payload carrying that `id` resolves on iOS and 404s on Android. The `id`-field-first order matches iOS; the fallback keeps every existing caller working, because a document that omits the `id` field is still reachable by its key.

**Consequence.** Two Firestore reads in the worst case instead of one. The composite index on `Loop.id` is single-field and already exists.

### Decision 3 — The accent colour is a screen-level value, resolved once and threaded down

**Choice.** `LoopDetailInfo.accentColorHex` carries the per-loop colour as a `#RRGGBB` string, chosen deterministically from the loop id by indexing the five-colour iOS palette (`#E27D5A`, `#AD598D`, `#86BFA9`, `#68B6CC`, `#EBAA4A`). The UI layer converts the hex to a Compose `Color` once, at the top of `LoopDetailContent`, and passes it down to the media placeholder, the community-name text, the description toggle, and the comment rows.

**Alternatives.**

- *Keep the loops module's `RandomColorService`.* It carries eight colours that are not iOS's five. Porting iOS's palette means the two platforms show the same colour for the same loop; keeping the existing one means they never will. `RandomColorService` is deleted and `core/ui/theme/KornerzCardPalette.kt` — which already holds the exact five iOS colours — becomes the single source of truth.
- *Derive the colour from the loop's index in the list.* Rejected: the colour would change as the list is paginated or reordered, and the detail screen reached by deep link would have no index at all.
- *Store the colour on the `Loop` document.* Rejected: the backend has no such field, and this change does not alter document shapes.

**Consequence.** `core/model` stays Compose-free (hence the hex string rather than a `Color`), and `core/ui` owns the palette.

### Decision 4 — Video plays in a modal, not inline

**Choice.** The media area renders the **poster** image with a 70 dp centred `play.circle.fill` badge. Tapping it opens a **modal** full-screen player — a `Dialog` with `usePlatformDefaultWidth = false` hosting the existing `VideoPlayerView` (Media3 ExoPlayer).

**Alternatives.**

- *Keep the inline ExoPlayer.* Rejected: iOS does not embed a player. The inline player changes the layout (the media area is a fixed 250 dp, and an inline player inside it is a cropped video), changes the scroll behaviour (a native player surface fights a scrolling parent), and makes a tap ambiguous — iOS's tap has exactly one meaning, "open the video".
- *Use a separate Activity.* Rejected: it would need an Activity registration in the manifest and a new module entry, and the app's whole navigation surface is Compose Navigation (`docs/architecture.md` §2.4). A `Dialog` keeps the player inside the screen's own state, so the ViewModel can still observe play/pause.

**Consequence.** `VideoPlayerView` is reused unchanged; the modal is a new `LoopDetailContent`-private composable plus a `LoopDetailEvent.VideoTapped` / `LoopDetailEvent.VideoDismissed` pair. The `loop-video` capability's inline-player requirement is superseded for the detail screen — its `PlayerViewModel` requirement is untouched, and the spec is amended in the `loop-detail` delta rather than in `loop-video`.

### Decision 5 — The HTML description uses the platform `WebView` through `AndroidView`

**Choice.** Add `KornerzHtmlText` to `core/ui/components/`: an `AndroidView` hosting an `android.webkit.WebView` with `allowFileAccess = false`, `shouldOverrideUrlLoading` returning `true`, a collapsed tag-stripped plain-text preview, and a "See More" / "Show Less" toggle.

**Alternatives.**

- *Strip the HTML and render the text.* Rejected: iOS renders the HTML, and the HTML is what the iOS authoring flow produces. Stripping loses exactly the formatting the author intended — the thing the user would notice in the parity demo.
- *Use a third-party HTML renderer.* Rejected: `docs/architecture.md` §3 lists no such library, and adding one requires an amendment to a frozen document. The platform `WebView` needs no dependency.
- *Reuse `KornerzRichTextEditor`.* Rejected: that is the P7 article editor — an editable `WebView` host with a JavaScript bridge for the editor toolbar. The detail screen needs a read-only fragment.

**Consequence.** `core/ui` gains a `WebView` host. `core/ui` already hosts `KornerzRichTextEditor` (P7), so the pattern and the platform dependency are established; `docs/architecture.md` §3 needs no amendment.

### Decision 6 — The add-comment bar moves out of the scroll into the `Scaffold`'s `bottomBar`

**Choice.** `LoopDetailScreen` composes a `Scaffold` whose `bottomBar` is the add-comment bar: a `Divider`, a `Row` with the current-user diamond avatar (50 dp, radius 10) and a `#EBE1D3` capsule carrying "Leave a comment", with 16 dp horizontal and 8 dp vertical padding. The `LazyColumn` renders only the scrollable content.

**Rationale.** iOS uses `safeAreaInset(edge: .bottom)`, which places the bar outside the scroll, above the keyboard, always visible. Android's `Scaffold.bottomBar` is the exact equivalent and additionally gives the correct window-inset handling for free.

**Consequence.** `LoopDetailContent` splits into a scrollable `LoopDetailContent` and a `LoopDetailAddCommentBar` composable; the `Scaffold` composes them. The `Scaffold` also gives the screen a natural home for the video modal's dismissal.

### Decision 7 — The current user comes from `SessionRepository`, resolved as a `Flow`

**Choice.** `LoopDetailViewModel` injects `SessionRepository` and derives `currentUserCode: Flow<String?>` from `sessionRepository.data` → `SessionState.Authenticated.user.userCode`. Every write path awaits the first non-null value before writing, and no-ops when none exists.

**Alternatives.**

- *`FirebaseAuth.currentUser?.uid`.* Rejected: iOS's identity for a user is `codeUser` (the `Users` document's `codeUser` field), not the Firebase Auth uid. The `LoopLikes` and `CommentLikes` documents are keyed by `userId = codeUser` on both platforms. `LoopFirestoreApi` uses `auth.currentUser?.uid` for the *publisher* filter, which is a different identifier — this change does not touch that, but it is why the uid cannot be reused here.
- *Read the user from the `Users` collection on demand.* Rejected: an extra read per action, and the session already carries the user.

**Consequence.** The ViewModel gains a `currentUserCode` flow and a small `withUserCode { … }` helper that suspends until the code is available. The create-comment flow gets the same treatment: its `userId` becomes the session's `codeUser` instead of whatever it uses today.

### Decision 8 — Comment engagement is read in two batched queries

**Choice.** `CommentLikeRepository` gains `suspend fun fetchLikeCounts(commentIds: List<String>): Result<Map<String, Int>>` (`CommentLikes where commentId in [...]`, counting documents per id) and `suspend fun fetchLikedCommentIds(userId: String, commentIds: List<String>): Result<Set<String>>` (`CommentLikes where userId == userId and commentId in [...]`). The detail ViewModel issues both once per comment fetch and composes the results into each `LoopCommentInfo`.

**Rationale.** iOS issues **two queries per comment** inside its `DispatchGroup` fan-out (`LoopDetailViewModel.fetchAllComments`). A 40-comment loop therefore costs 80 reads on iOS and would cost 80 on a naive Android port. The batched form costs **two** reads and produces the same data. The `in` query is bounded by Firestore's 30-value limit for `in` clauses, so the implementation chunks the ids into groups of 30 — which for 40 comments is two reads per query, still vastly better than 80.

**Consequence.** The `CommentLikes` collection needs a composite index on `commentId` for the `in` + count read and on `userId` + `commentId` for the liked-by-me read. The `userId` + `commentId` index already exists (iOS's `checkIfUserLiked` uses the same equality pair); the `commentId` single-field index also already exists (iOS's `fetchLikesForComment` uses `commentId` + `creationDate`). Both are already deployed, per the iOS audit.

### Decision 9 — The Klouders write path is added to the existing `GamificationRepository`

**Choice.** `GamificationRepository.addPoints(action: String, points: Int, referenceId: String): Result<Unit>` writes one `Gamefication/{generatedId}` document with `gameficationAction`, `gameficationQtdPts`, `gameficationDateHora`, `gameficationUserId`, and `gameficationReferenceId`.

**Alternatives.**

- *A new `GamificationWriter` class.* Rejected: the repository already owns the collection and its field spellings; splitting the read and write halves of one collection across two classes invites exactly the kind of field drift the P10 leaderboard had to undo.
- *A Cloud Function.* Rejected: iOS writes the document directly from the client, and introducing a callable function would need a backend change plus a `FirebaseFunctions` dependency in the loops module — out of scope and not iOS parity.

**Consequence.** The repository stays `@Singleton` and stays in `core/network`. The three call sites are the loop detail's open, the video player's open, and the create-comment submit. None of them waits on the write.

### Decision 10 — The bottom-bar decision is lifted from the nested NavHost to the root `Scaffold`

**Choice.** The `loops/{loopId}` route gains a `hideBottomBar: Boolean` nav argument defaulting to `false`. The nested graph reads the current back-stack entry's argument and lifts it to `AppShell` through the same seam the shell already uses for the pending Korner id. The root `Scaffold`'s visibility predicate becomes `routeAllowsBar && !hideBottomBar`.

**Rationale.** iOS sets `hidesBottomBarWhenPushed = true` for the loop detail **only** when it is pushed from the Home tab, the Korner detail, or a deep link — not from the Loops tab (`LoopsTabCoordinator` does not set it). Android's root `Scaffold` can only see the **root** back-stack route, which is `loops` for the whole Loops tab, so the root route cannot express the per-entry-point decision. The nested graph can, because it sees its own entry's arguments.

**Consequence.** The shell's cross-tab seam (`pendingLoopId`) becomes `pendingLoopTarget`, carrying the id and the flag. The Loops tab's own entry point passes `hideBottomBar = false`; the Home carousel, the Korner detail, and the deep-link handler pass `true`.

### Decision 11 — Comment deletion becomes a Material 3 swipe action

**Choice.** `LoopCommentView` wraps its content in a `SwipeToDismissBox` with a trailing `SwipeToDismissBoxValue.EndToStart` state, whose background is a destructive red surface carrying a `trash` icon. The affordance is enabled **only** when `canDelete = true`, which the ViewModel computes as `comment.userId == currentUserCode`.

**Alternatives.**

- *Keep the visible delete `IconButton`.* Rejected on two grounds. iOS uses a swipe action, and the Android button is currently shown for **every** comment regardless of ownership — it is a live "delete someone else's comment" button.
- *A confirmation `Dialog` after the swipe.* Rejected: iOS's swipe is immediate (no confirmation), and a full-swipe destructive action is the platform idiom on both platforms.

**Consequence.** The P5 `loop-comments` requirement that described a trailing trash `IconButton` plus a confirmation dialog is superseded; the delta spec replaces it.

### Decision 12 — Tests follow the repo's two-tier pattern, with no new test harness

**Choice.** `feature/loops` gains a `src/test` source set (it already has the `junit` / `mockk` / `turbine` / `coroutines-test` dependencies) and an `src/androidTest` source set (the `androidx.compose.ui.test.junit4` stack `feature/ranking` uses). JVM tests cover the ViewModel and the repositories with `mockk` at the wrapper boundary; `androidTest` covers the composable assertions.

**Rationale.** The repo has no Robolectric and no Hilt test harness, so an `androidTest` cannot easily exercise a real `ViewModel` against a real Firestore. The established pattern — `RankingViewModelTest` (JVM, repository mocked) alongside `RankingScreenTest` (`androidTest`, state fed in directly) — is copied exactly.

**Consequence.** CI builds `assembleDevDebug` only and provisions no emulator, so the `androidTest` cases are written and `@Ignore`d, exactly as `feature/ranking` does. The JVM tests are the ones that actually gate.

## Risks / Trade-offs

- **The `id`-field lookup can return a different document than the document-id read.** If the backend ever holds two `Loop` documents with the same `id` field, the detail screen would resolve the first. → Mitigation: the lookup order matches iOS, so both platforms pick the same document; and the existing document-id callers are unaffected because the fallback only runs when the field query is empty.

- **`SwipeToDismissBox` and a `LazyColumn` interact badly when nested.** A swipeable row inside a vertically-scrolling list can steal the vertical drag. → Mitigation: `SwipeToDismissBox` only claims the horizontal axis by default; and the delete affordance is disabled for comments the user does not own, so the swipeable is inert for most rows.

- **The `WebView` in `KornerzHtmlText` is a heavyweight view.** Creating one per expanded description costs memory and a JNI bridge. → Mitigation: the `WebView` is only attached when expanded — the collapsed state is a plain `Text` — and it is released via `AndroidView`'s `onRelease` when the component leaves composition.

- **The `in` query is capped at 30 values.** A loop with more than 30 comments needs chunking. → Mitigation: chunking is implemented in the repository, and the count is derived per chunk and merged; the alternative (one query per comment) is what iOS does and is what we are explicitly avoiding.

- **Writing `Gamefication` documents from Android changes a shared collection.** A user active on both platforms will now accumulate points from both, which is correct — the points are per `gameficationUserId` — but it does change the leaderboard's numbers for existing users. → Mitigation: the write path is strictly additive and uses the same field names the existing read path already understands; no migration and no read-path change is needed.

- **The `Loop` collection is read with a new query shape.** The `whereEqualTo("id", …)` read adds a field lookup the list path does not use. → Mitigation: `id` is a single-field index, already deployed, and the cost is one extra read in the fallback case only.

- **The change touches six capabilities at once.** `loop-detail`, `loop-comments`, `loop-domain`, `android-app-shell`, `design-system`, and the new `gamification-events`. → Mitigation: the tasks are ordered so that the shared `core/*` work lands and is tested before the screen consumes it, and the shell wiring lands last so it can be reverted independently.

## Migration Plan

No data migration. The change is code-only against the existing backend.

1. Land `core/model` (`LoopDetailInfo`, `LoopCommentInfo` fields) and `core/ui` (the four new shared components, the palette consolidation) — additive, no behavioural change to existing screens.
2. Land `core/network` (`LoopDetailRepository`, the batched engagement reads, `GamificationRepository.addPoints`) — additive.
3. Land `feature/loops` (the screen rewrite, the comment-row restyle) behind the existing route, so the shell wiring is unchanged until step 4.
4. Land `feature/shell` (the `hideBottomBar` argument, the `NavigateToKornerDetail` wiring) — the only change that affects navigation.

**Rollback.** Every step is a separate commit. Reverting step 4 restores the previous navigation behaviour without touching the screen; reverting steps 1–2 removes the new shared components, which nothing else depends on. The `Gamefication` write path is the only one with a lasting side effect: points already awarded are not rolled back, because they are legitimate engagement records. Reverting it stops new awards only.

## Open Questions

- **Should the reply thread inherit the diamond-avatar comment row?** The reply screen renders the same `LoopCommentView`, so the restyle reaches it for free. iOS's `LoopReplyScreen` uses the same `LoopCommentView` too. → Treated as in scope by the shared component, and verified in the parity demo rather than gated here.
- **Does the Korner detail need a bottom-bar change too?** iOS hides the bottom bar on the Korner detail (`hidesBottomBarWhenPushed = true`), and Android's Korner detail lives in the Kornerz tab's nested graph with the same root-route limitation. → Out of scope here; flagged for the next shell-level parity pass, since the same lifting mechanism introduced by Decision 10 will apply to it.
- **Should the `icon_back` close affordance replace the system back button?** iOS uses a close (X-in-circle) glyph on pre-iOS 26 and the system default on iOS 26+. Android's system back button is the platform idiom and the P10 change already standardises on it. → The drawable is shipped for parity but the system affordance is kept; the demo will show the platform difference.
