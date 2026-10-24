## Why

> **See also:** [`docs/architecture.md`](../../../docs/architecture.md) for the MVVM + Repository + UDF contract, `AsyncValue<T>`, `Channel<NavEvent>`, module boundaries, Hilt scopes, and the canonical library list that every artifact in this change conforms to.

The Android Loops tab ships a Loop Detail screen, but it is a **stylized approximation** of the iOS screen, not a port of it. The two apps are structurally similar and behaviourally different in roughly twenty places. Put them side by side and the gap is obvious: the Android title row prints the raw `publisherId` and the raw `communityId` where iOS prints the author's nickname and a tappable, accent-coloured community name; the media placeholder is a hard-coded `#5D47ED` purple where iOS paints the per-loop accent colour and shows a 70 pt play badge; the description is plain text where iOS renders the author's HTML; the add-comment bar scrolls away with the list where iOS pins it to the bottom safe-area inset; the like button is a filled heart with a separate count where iOS uses a 36 pt `heart.circle`; and the **like button does not work at all**, because `LoopDetailViewModel.currentUserId` is the empty string, so every like, comment-like, and comment-delete is a silent no-op. Comment like counts are never fetched at all — `isLikedByUser` is hard-wired to `false` for every comment and the live `CommentLikes` count is never read.

The gap matters now because **P10 closes the phased-delivery plan with a "feature-by-feature parity demo against iOS"** (`docs/phases.md:400`). The Loop Detail screen is the single most-tapped surface in the Loops tab; it is the screen a stakeholder opens first when they compare the two apps.

The root cause is that the **spec and the code have already drifted**. `openspec/specs/loop-detail/spec.md`, written in P5, already mandates an HTML `WebView` description host, `heart_circle`/`heart_circle_fill` icons, a 55 dp publisher avatar overlay, a `timeAgoSinceDate()` timestamp, a safe-area-inset add-comment button, and `KornerzAvatar` in that button. **None of those exist in `LoopDetailContent.kt`.** The spec describes the iOS screen; the code implements something else. This change reconciles the two: the code becomes what the spec already says, and the spec is amended for the handful of places where iOS itself is the authority and P5 guessed wrong.

### iOS audit findings that scope this change

The iOS source was audited before scoping. Nine findings materially changed the plan:

1. **The screen's argument object is resolved by the caller, not by the screen.** iOS `LoopDetailViewModel` receives an already-resolved `LoopInfo` (loop + owner + community + accent colour) and **never re-fetches the loop, the owner, or the community** (`LoopsTabViewModel.swift:14-40`). Android's screen re-fetches the loop itself from the route argument, so it cannot show a nickname or a community name without two extra reads it does not perform today. This change adds the join.
2. **The accent colour is a first-class input, not a decoration.** iOS derives it from `RandomColorService.color(for: index)` over a fixed five-colour palette (`#E27D5A`, `#AD598D`, `#86BFA9`, `#68B6CC`, `#EBAA4A`) and passes it down to the description toggle, the community link, and the comment rows. Android has the same palette in `core/ui` (`KornerzCardPalette`) but hard-codes a different colour in `MediaSection` and derives a *different* colour per comment from a hash. The colour becomes a single screen-level value.
3. **iOS plays video in a modal, not inline.** iOS renders the poster image with a 70 pt `play.circle.fill` badge and opens an `AVPlayerViewController` on tap (`LoopDetailView.swift:172-200`, `UIViewController+Ext.swift` `playLoopVideo`). Android embeds an ExoPlayer directly in the media area, which changes the layout, the scroll behaviour, and the meaning of a tap. Android keeps ExoPlayer (the library catalog has no `AVPlayerViewController` equivalent) but adopts the iOS **interaction**: poster + play badge + tap-to-open.
4. **The description is HTML first.** iOS prefers `descriptionHtml` (rendered in a `WKWebView` via `ExpandableHTMLDescriptionView`) and falls back to plain `description` with an `ExpandableText` 2-line collapse. Android renders only the plain branch, so every HTML description in the backend — which is what the iOS authoring flow produces — loses its formatting.
5. **The add-comment bar is outside the scroll.** iOS uses `safeAreaInset(edge: .bottom)` (`LoopDetailView.swift:30-35`), so the bar is always visible above the keyboard and never scrolls. Android renders it as the last `item {}` of the `LazyColumn`, so it scrolls away.
6. **Comment deletion is a swipe action, not a button.** iOS attaches `.swipeActions(edge: .trailing, allowsFullSwipe: true)` with a destructive `trash` "Delete" button, shown **only** for the current user's own comments (`LoopDetailView.swift:273-308`). Android renders a permanent delete `IconButton` for every comment — which currently deletes other people's comments, because ownership is never checked.
7. **The comment row uses a diamond avatar and an icon action row.** iOS uses `DiamondImageView` (36 pt, radius 8, rotated 45°) with a 16 pt gap, an expandable body, and an action row of `message.fill` + replies count and `heart`/`heart.fill` + like count, 25 pt apart, with the heart turning **red** when liked. Android uses a 40 dp circular avatar, an 8 dp gap, a plain body, and a text "Reply" link.
8. **Engagement awards Klouders points, and Android cannot write them.** iOS writes a `Gamefication` document on three triggers: `ACCESS_LOOP` (+1) when the loop opens, `ACCESS_REPLAYS` (+1) when the video plays, `ACCESS_COMMENT` (+3) when a comment is posted (`LoopDetailController.swift`, `GamificationService.swift`, `CreateCommentViewModel.swift`). Android's `GamificationRepository` is **read-only** — `getUserPoints()` and `getRanking()` are its only public methods — so no Android user can ever earn points, and the Klouders leaderboard is permanently empty of Android contributors. This is the one place where the change touches a collection outside `Loop*`.
9. **iOS's deep-link path resolves the loop by its `id` *field*, not by its document id.** iOS `getLoopDetail(loopId)` queries `Loop.whereField("id", isEqualTo: loopId)` (`APICommunity.swift:606`), while Android's `LoopFirestoreApi.fetchLoopDetail` reads `Loop/{documentId}`. A push payload carrying a `loopId` that is the document's `id` field rather than its Firestore key resolves on iOS and 404s on Android. The same query serves both the screen and the deep link, so it is fixed once.

Two iOS artifacts are deliberately **not** ported: iOS's `shareLoop` / `reportLoop` coordinator methods are dead code — they are declared, implemented, and never called from `LoopDetailView` — and Android ships no share sheet or report affordance for the loop. iOS's `icon_back` asset (a dark circle with a white X, i.e. a **close** button, not a chevron) is ported as a vector drawable, but Android keeps the platform back affordance in the system bar and does not reproduce the iOS 26 / pre-iOS 26 conditional back-button behaviour.

## What Changes

### A — Domain + data layer: the join the screen actually needs

- Add `LoopDetailInfo` to `core/model` — the Android counterpart of iOS's `LoopInfo` (`LoopsTabViewModel.swift:14-40`). It carries the `Loop`, the resolved owner `User?`, the resolved community `Community?`, and the accent colour as a plain hex `String`, so that `core/model` stays Compose-free per the module rules.
- Add `LoopDetailRepository` to `core/network` — resolves a loop id into a `LoopDetailInfo` in **three reads**, mirroring iOS's join: `Loop` by the `id` **field** (`whereEqualTo("id", loopId)`, with a `document(loopId)` fallback for documents that omit the field), `Users` by `codeUser in [publisherId]`, and `Communities` by `communityId in [communityId]`.
- Extend `UsersRepository` with a **batched** `fetchUsersByCodes(codes)` returning `Map<String, User>` — the Android port currently exposes only `fetchUsersByCodes` returning `UserSnippet`s, and the loop screen resolves comments one user at a time in a loop, producing N reads for N comments.
- Add `LoopCommentEngagementRepository` reads to the existing `CommentLikeRepository` — a **batched** per-comment like-count query (`CommentLikes where commentId in [...]`) and a **batched** liked-by-me query (`CommentLikes where userId == currentUserId and commentId in [...]`), so a 40-comment loop costs 2 reads instead of 80.
- Fix `LoopFirestoreApi.fetchLoopDetail` to resolve by the `id` field first (iOS parity, finding 9), keeping the document-id read as a fallback so existing callers are unaffected.
- Add `GamificationRepository.addPoints(action, points, referenceId)` — the write path iOS has and Android does not. It writes a `Gamefication/{randomId}` document with `gameficationAction`, `gameficationQtdPts`, `gameficationDateHora`, `gameficationUserId`, and `gameficationReferenceId`, using the backend's misspelled collection name verbatim.
- Wire the **real** current user. `LoopDetailViewModel.currentUserId` becomes a `Flow<String?>` derived from `SessionRepository.data` → `SessionState.Authenticated.user.userCode`, not the empty-string placeholder. Until this lands, every write action on the screen is a no-op.

### B — Shared UI: the iOS primitives Android was missing

- Add `KornerzExpandableText` to `core/ui/components/` — the Android counterpart of iOS `Components/ExpandableText.swift`: 2-line collapsed, a "View More" / "View Less" toggle in the accent colour, and iOS's exact overflow heuristic (`text.count / 50 + newlineCount > lineLimit`), not Android's `text.length > 120`.
- Add `KornerzHtmlText` to `core/ui/components/` — the Android counterpart of iOS `Components/ExpandableHTMLDescriptionView.swift` + `WebViewRepresentable.swift`: an `AndroidView` hosting a `WebView` with file access disabled, URL clicks intercepted, a 2-line collapsed plain-text preview, and a "See More" / "Show Less" toggle that swaps the preview for the live WebView.
- Add `KornerzTriangleImage` to `core/ui/components/` — the Android counterpart of iOS `SwiftUI/Components/TriangleImageView.swift` + `TriangleClippedShape.swift`, for the publisher avatar overlay on the media area. `KornerzDiamondImage` already exists for the comment rows and is reused as-is.
- Port the iOS `icon_back` SVG (`kornerz/Kornerz/Resources/Assets.xcassets/Home/icon_back.imageset/icon_back.svg` — a `#2E2F3E` circle with a white X) as an Android vector drawable, for parity with the iOS close affordance.
- Add the iOS accent palette to `core/ui` as the single source of truth (`KornerzCardPalette` already carries the exact five iOS colours; the loops module's `RandomColorService` carries eight different ones — the loops module's palette is retired in favour of `KornerzCardPalette`).
- Add `KornerzTimeAgo` — a shared `timeAgoSinceDate()`-equivalent formatter (`Extensions/Date+Extension.swift`) so the title row and the comment rows stop rolling their own relative-time strings.

### C — The Loop Detail screen itself: layout parity

All in `feature/loops/.../detail/`, following `docs/architecture.md` §10 exactly (`State` / `Event` / `NavEvent` / `Repository` / `ViewModel` / `Screen` / `Content` / `components/`).

- **Media area** — 250 dp, full-bleed, poster image via Coil with the **accent-colour rectangle** as the placeholder, a **70 dp** centred `play.circle.fill` badge for video loops, and a **55 dp** triangle-clipped publisher avatar at the bottom-leading corner with 16 dp padding. The avatar shows the resolved owner's thumbnail, not `null`.
- **Title row** — title in Satoshi Medium 20; beneath it a 4 dp-spaced meta row reading *owner nickname* · "in" · **tappable accent-coloured community name** · *time-ago* in gray. The community name is a link that routes to the Korner detail. The trailing control is a **36 dp `heart.circle` / `heart.circle.fill`** button that becomes a spinner while the write is in flight — not a filled heart with a separate count.
- **Description** — the HTML branch when `descriptionHtml` is non-empty, the expandable plain-text branch otherwise, both with the accent-coloured toggle.
- **Stats row** — edge-to-edge, `Divider` above and below, 16 dp padding, **25 dp** gaps, 16 dp icons (eye / message / heart), Satoshi 12 in gray. The heart is filled when the user has liked the loop.
- **Comments header** — "Comments" in Satoshi Medium 20, full-width padding, `Divider` below.
- **Comment rows** — diamond avatar 36 dp (radius 8) with a 16 dp gap, nickname in Satoshi Medium 12, time-ago in Satoshi 10 gray, an **expandable** body, and an action row of `message.fill` + replies count and `heart`/`heart.fill` + like count, 25 dp apart, the heart **red** when liked. Deletion becomes a **trailing swipe action**, destructive, `trash` icon, full-swipe — offered **only** for the current user's own comments.
- **Empty-comments text** — "No comments yet. Be the first!", matching the iOS localization key `no_comment_message`.
- **Add-comment bar** — moved **out of the `LazyColumn`** into a bottom bar above the system inset, holding the current user's diamond avatar and a capsule "Leave a comment" field in the iOS `#EBE1D3` fill.
- **Pull-to-refresh** — `PullToRefreshBox` re-runs the fetch, matching iOS `.refreshable`.
- **Loading / error** — the shimmer redacts the media, title, and stats sections only, exactly as iOS `.redacted(reason: .placeholder)` does; the error surface is the shared `KornerzErrorState`.

### D — ViewModel + wiring

- `LoopDetailState` gains the resolved owner, community, accent colour, comment engagement state, and the per-comment like state; `LoopDetailEvent` gains `DescriptionExpanded`, `CommentExpanded`, `CommentDeleteSwiped`, and `VideoPlayed`; `LoopDetailNavEvent` keeps its three cases.
- The ViewModel performs the **one-shot** fetch iOS performs (loop + owner + community in one `fetchData`), then the comment fetch, then the like-state fetch — with the like state resolved from the **real** current user id.
- `incrementViewCount` remains guarded to fire exactly once per session, and now runs against the loop's document id.
- **Gamification** — the screen awards `ACCESS_LOOP` (+1) on open, and `ACCESS_REPLAYS` (+1) when the video is played. The create-comment flow awards `ACCESS_COMMENT` (+3).
- **`feature/shell`** — wire `LoopDetailNavEvent.NavigateToKornerDetail`, which is currently a literal no-op (`AppShell.kt:757` — `/* P3 korner deep link */`), to the existing `kornerz/{communityId}` destination; add the loop-detail route to the **bottom-bar suppression** rule so the bar hides on the detail screen, matching iOS's `hidesBottomBarWhenPushed`; and route the `newLoop` / `loopComment` deep links (which P10 mints) to this screen.
- **Tests** — `LoopDetailViewModelTest` (state transitions, like toggle, view-count guard, gamification write, real-user-id gating), `LoopDetailRepositoryTest` (the three-read join, the `id`-field lookup, the batched comment-engagement reads), and `LoopDetailContentTest` (Compose UI assertions for the title row, the stats row, the empty state, and the pinned add-comment bar).

## Capabilities

### New Capabilities

- `gamification-events`: writing `Gamefication` documents to award Klouders points for loop engagement (open a loop, replay a video, post a comment) — the write path iOS has and Android currently cannot perform at all.

### Modified Capabilities

- `loop-detail`: the screen's layout, interaction, and data-fetch contract is rewritten to match iOS — media + play badge + triangle avatar overlay, the publisher/community/time-ago title row with a `heart.circle` toggle, the HTML description branch, the 25 dp stats row, the pinned add-comment bar, the swipe-to-delete comment action, pull-to-refresh, and the real current-user identity behind every write.
- `loop-comments`: the shared comment row changes from a circular avatar + text "Reply" link + permanent delete button to iOS's diamond avatar + expandable body + icon action row + trailing swipe-to-delete.
- `loop-domain`: `core/model` gains `LoopDetailInfo` (loop + owner + community + accent), and `LoopCommentInfo` gains the reply count and the red-heart like state the row needs.
- `android-app-shell`: the loop-detail route suppresses the bottom bar, the Korner-detail navigation event is wired instead of dropped, and the `newLoop` / `loopComment` deep links land on this screen.
- `design-system`: `core/ui` gains the iOS-parity shared components (`KornerzExpandableText`, `KornerzHtmlText`, `KornerzTriangleImage`, `KornerzTimeAgo`) and the single iOS accent palette.

## Impact

**Modules touched**

| Module | Change |
|---|---|
| `core/model` | +`LoopDetailInfo`; `LoopCommentInfo` gains `repliesCount` + per-comment like state |
| `core/network` | +`LoopDetailRepository`; `UsersRepository` batched lookup; `CommentLikeRepository` batched engagement reads; `LoopFirestoreApi` `id`-field lookup; +`GamificationRepository.addPoints` |
| `core/ui` | +`KornerzExpandableText`, `KornerzHtmlText`, `KornerzTriangleImage`, `KornerzTimeAgo`; accent palette consolidated; +`icon_back` drawable |
| `feature/loops` | `detail/` rewritten to iOS parity; `components/LoopCommentView.kt` restyled; `components/RandomColorService.kt` retired in favour of `KornerzCardPalette`; `comment/` awards `ACCESS_COMMENT` |
| `feature/shell` | bottom-bar suppression for the detail route; `NavigateToKornerDetail` wired; `newLoop` / `loopComment` deep links routed |

**No new Gradle module.** The screen stays in `feature/loops`, which already owns the destination — this is a parity change, not a new vertical.

**No new third-party dependency.** The HTML description uses the platform `android.webkit.WebView` through `AndroidView` — the catalog already ships `KornerzRichTextEditor` (a `WebView` host) for the article editor in P7, so the pattern is established; `docs/architecture.md` §3 needs no amendment.

**Backend impact**

- `Loop` and `LoopComments` are read with new query shapes: `Loop` by the `id` field, `Users` and `Communities` by `in` queries, `CommentLikes` by `commentId in [...]`. **The existing composite indexes cover these** (`LoopComments` on `loopId + parentId + active + creationDate`; `CommentLikes` on `commentId` and on `userId + commentId`; `Users` on `codeUser`; `Communities` on `communityId`). The `in` variant of `CommentLikes` needs a `commentId` + `userId` index that already exists for the two-field equality query.
- `Gamefication` gains **writes** from the Android client. The collection and its field spellings already exist (P10's `Gamification` model read them); this change only writes documents of the same shape. No schema change.
- `Loop.viewCounts` continues to be incremented on open. `Loop.likesCount` and `LoopComments.likesCount` continue to be incremented by the like APIs.

**Risks**

- The `Loop` document-id vs. `id`-field ambiguity is a **live** discrepancy: reading by the `id` field first can return a *different* document than `document(loopId)` would if the backend has duplicates. The change resolves by the `id` field first and falls back to the document id, and the fallback keeps the existing P5 call sites working.
- The `Gamefication` write path is new behaviour on a shared collection. If the iOS app and the Android app award points for the same loop open, the leaderboard counts both — which is correct, since the points are per user id, and the Android app is writing under the Android user's `codeUser`.

**Out of scope**

- Sharing, reporting, saving/bookmarking, and loop deletion for the owner — iOS's `shareLoop` / `reportLoop` are dead code and the detail screen has no delete affordance at all.
- The reply thread screen (`loops/{loopId}/reply/{commentId}`), which already exists from P5 and is a separate surface.
- pt-BR localisation, per `docs/project-brief.md`.
