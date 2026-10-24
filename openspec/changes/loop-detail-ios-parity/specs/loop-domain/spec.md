# loop-domain Specification

## References
- [`docs/architecture.md`](../../../../docs/architecture.md) — MVVM + Repository + UDF, module boundaries, `core/model` staying Compose-free.
- `kornerz/Kornerz/Models/Loop.swift` — iOS source-of-truth for the `Loop` model and its fallback chains.
- `kornerz/Kornerz/SwiftUI/Dashboard/Loops/LoopsTab/LoopsTabViewModel.swift` — iOS source-of-truth for the `LoopInfo` struct that this capability's `LoopDetailInfo` mirrors.
- `kornerz/Kornerz/Models/LoopLike.swift`, `kornerz/Kornerz/Models/CommentLike.swift` — iOS source-of-truth for the like documents and their collections.
- [`openspec/changes/loop-detail-ios-parity/proposal.md`](../proposal.md) — capability rationale for the iOS parity work.
- [`openspec/changes/loop-detail-ios-parity/design.md`](../design.md) — the `LoopDetailInfo` projection and the collection-name decisions.

## MODIFIED Requirements

### Requirement: LoopLike and CommentLike models match iOS shape
The `LoopLike` `@Serializable` data class SHALL live at `core/model/LoopLike.kt` and SHALL expose: `id: String?`, `userId: String`, `loopId: String`, `creationDate: kotlinx.datetime.Instant?`. The `userId` and `loopId` fields SHALL be required (non-nullable) — matches iOS `LoopLike.swift` lines 12–16. The `CommentLike` data class SHALL live at `core/model/CommentLike.kt` and SHALL expose: `id: String?`, `userId: String`, `commentId: String`, `creationDate: kotlinx.datetime.Instant?` with the same required-field shape.

Both documents SHALL be written to the **top-level** collections `LoopLikes` and `CommentLikes` respectively — matching iOS `LoopLikeRepository` / `CommentLikeRepository`, which query `db.collection("LoopLikes")` and `db.collection("CommentLikes")` with `whereField("userId", …)` and `whereField("loopId" | "commentId", …)`. The like documents are **not** sub-collections of `Loop/{id}` or `LoopComments/{id}`; a like document SHALL carry the fields `userId`, `loopId` or `commentId`, and `creationDate`.

#### Scenario: LoopLike encodes the composite key in the top-level collection
- **WHEN** a `LoopLike(userId = "u1", loopId = "l1", creationDate = Instant.DISTANT_PAST)` is written
- **THEN** the Firestore document path is `LoopLikes/{generatedId}`
- **AND** the document body contains `{"userId": "u1", "loopId": "l1", "creationDate": <ts>}`

#### Scenario: CommentLike encodes the comment-scoped key in the top-level collection
- **WHEN** a `CommentLike(userId = "u1", commentId = "c1", creationDate = Instant.DISTANT_PAST)` is written
- **THEN** the Firestore document path is `CommentLikes/{generatedId}`
- **AND** the document body contains `{"userId": "u1", "commentId": "c1", "creationDate": <ts>}`

### Requirement: LoopCommentInfo projects LoopComment + User + like state for the UI layer
The `LoopCommentInfo` data class SHALL live at `core/model/LoopCommentInfo.kt` and SHALL expose: `comment: LoopComment`, `user: User?`, `isLikedByUser: Boolean`, `likesCount: Int`, `repliesCount: Int`. The default `isLikedByUser` SHALL be `false`, `likesCount` SHALL be `0`, and `repliesCount` SHALL be `0`. Mirrors iOS `LoopCommentInfo` struct (`LoopDetailViewModel.swift` lines 13–37).

The `likesCount` on a row SHALL hold the **live** count read from the `CommentLikes` collection, replacing the denormalized `LoopComment.likesCount` — matching iOS, where `LoopCommentInfo.setLikeCount(_:)` overwrites the stored count with the fetched one.

#### Scenario: LoopCommentInfo composes comment + user + like state for rendering
- **WHEN** the loop detail VM resolves a list of comments, a batched user fetch, and a batched comment-engagement fetch
- **THEN** it composes each row into `LoopCommentInfo(comment = ..., user = ..., isLikedByUser = ..., likesCount = ..., repliesCount = ...)`
- **AND** the UI layer consumes `LoopCommentInfo` instead of raw `LoopComment`

#### Scenario: The live like count replaces the denormalized count
- **WHEN** a `LoopComment` document carries `likesCount = 2` but the `CommentLikes` collection holds 5 like documents for that comment
- **THEN** the composed `LoopCommentInfo.likesCount` is `5`

## ADDED Requirements

### Requirement: LoopDetailInfo projects the Loop with its owner and community for the detail screen
`core/model` SHALL declare a `LoopDetailInfo` data class — the Android counterpart of iOS's `LoopInfo` struct (`LoopsTabViewModel.swift` lines 14–40) — carrying: `loop: Loop`, `owner: User?`, `community: Community?`, and `accentColorHex: String`. `owner` and `community` SHALL default to `null` so a partially-resolved detail is representable.

`accentColorHex` SHALL be the **per-loop accent colour** as a `#RRGGBB` hex string, drawn from the iOS palette `#E27D5A`, `#AD598D`, `#86BFA9`, `#68B6CC`, `#EBAA4A` and cycled by an index derived from the loop id — matching iOS `RandomColorService.color(for:)`. `core/model` SHALL NOT hold a Compose `Color`; the hex-to-`Color` conversion happens in the UI layer.

`core/model` SHALL remain free of Compose and Android framework types, so `LoopDetailInfo` SHALL NOT reference `androidx.compose.ui.graphics.Color` or any `android.*` type.

#### Scenario: LoopDetailInfo carries the three resolved pieces
- **WHEN** a `LoopDetailInfo` is constructed for a loop whose owner and community both resolved
- **THEN** it carries the `Loop`, the owner `User`, and the community `Community`

#### Scenario: An unresolved owner or community is representable
- **WHEN** a `LoopDetailInfo` is constructed with no owner and no community
- **THEN** the construction compiles without additional arguments
- **AND** `owner` and `community` are `null`

#### Scenario: The accent colour is a hex string, not a Compose Color
- **WHEN** the `LoopDetailInfo` class is inspected
- **THEN** `accentColorHex` is a `String` matching the `#RRGGBB` shape
- **AND** the class carries no import of `androidx.compose.ui.graphics.Color`

#### Scenario: The accent colour cycles deterministically
- **WHEN** the same loop id is resolved twice
- **THEN** the accent colour is identical
- **WHEN** five consecutive loop ids are resolved
- **THEN** the five accent colours are the five iOS palette entries, in order
