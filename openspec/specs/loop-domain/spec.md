# loop-domain Specification

## Purpose
The `loop-domain` capability defines the `Loop`, `LoopComment`, `LoopLike`, `CommentLike`, `LoopCommentInfo`, and `LoopsFilter` data models that every other loop-related capability builds on. These models live in `core/model` and are the Android port of `kornerz/Kornerz/Models/Loop.swift`, `LoopComment.swift`, `LoopLike.swift`, and the iOS `CommentLike` model. Every field, every enum case, and every computed-property fallback chain in iOS maps to a Kotlin field on the `@Serializable` data class below — the Android port is field-for-field equivalent so a Firestore document decoded by iOS `FirestoreServiceManager` and the Android `LoopFirestoreApi` produces the same data.

## Requirements

### Requirement: Loop model exposes every iOS field with the same fallback semantics
The `Loop` `@Serializable` data class SHALL live at `core/model/Loop.kt` and SHALL expose the following fields, every one of which is nullable to match iOS: `id: String?`, `communityId: String?`, `publisherId: String?`, `active: Boolean?`, `title: String?`, `description: String?`, `descriptionHtml: String?`, `imageHash: String?`, `thumbnailHash: String?`, `videoHash: String?`, `firebaseImageUrl: String?`, `firebaseThumbnailUrl: String?`, `firebaseVideoUrl: String?`, `viewCounts: Int?`, `commentsCount: Int?`, `likesCount: Int?`, `theme: String?`, `creationDate: kotlinx.datetime.Instant?`. The computed properties `thumbnailUrl: String?`, `imageUrl: String?`, `videoUrl: String`, `isVideo: Boolean`, and `videoColorHex: String` SHALL resolve exactly as in iOS `Loop.swift` (Firebase URL first, then `IPFS.baseUrl + <hash>` fallback; `isVideo` is `firebaseVideoUrl` non-empty; `videoColorHex` is `#FFA500` when `isVideo`, `#000000` otherwise).

#### Scenario: Loop deserializes from a Firestore document with every field present
- **WHEN** a Firestore `loops/{loopId}` document is decoded into a `Loop`
- **THEN** every non-nullable Firebase field populates the matching Kotlin property
- **AND** every missing Firebase field remains null

#### Scenario: Loop.thumbnailUrl fallback chain matches iOS
- **WHEN** a `Loop` has `firebaseThumbnailUrl = "https://cdn.example.com/t.jpg"` non-null
- **THEN** `thumbnailUrl` returns `"https://cdn.example.com/t.jpg"`
- **WHEN** a `Loop` has `firebaseThumbnailUrl = null` and `firebaseImageUrl = "https://cdn.example.com/i.jpg"` non-null
- **THEN** `thumbnailUrl` returns `"https://cdn.example.com/i.jpg"`
- **WHEN** a `Loop` has both Firebase URLs null and `thumbnailHash = "Qm..."` non-empty
- **THEN** `thumbnailUrl` returns `IPFS.baseUrl + "Qm..."`
- **WHEN** a `Loop` has both Firebase URLs null and `imageHash = "Qm..."` non-empty (no `thumbnailHash`)
- **THEN** `thumbnailUrl` returns `IPFS.baseUrl + "Qm..."`
- **WHEN** a `Loop` has every URL and hash empty
- **THEN** `thumbnailUrl` returns `null`

#### Scenario: Loop.isVideo is true only when firebaseVideoUrl is non-empty
- **WHEN** a `Loop` has `firebaseVideoUrl = "https://cdn.example.com/v.mp4"` non-empty
- **THEN** `isVideo` returns `true`
- **WHEN** a `Loop` has `firebaseVideoUrl` null
- **THEN** `isVideo` returns `false`

#### Scenario: Loop.videoUrl fallback
- **WHEN** a `Loop` has `firebaseVideoUrl = "https://cdn.example.com/v.mp4"` non-empty
- **THEN** `videoUrl` returns `"https://cdn.example.com/v.mp4"`
- **WHEN** a `Loop` has `firebaseVideoUrl` null and `videoHash = "Qm..."` non-empty
- **THEN** `videoUrl` returns `IPFS.baseUrl + "Qm..."`

### Requirement: LoopComment model mirrors iOS fields and nullability
The `LoopComment` `@Serializable` data class SHALL live at `core/model/LoopComment.kt` and SHALL expose: `id: String?`, `loopId: String?`, `parentId: String?`, `userId: String?`, `active: Boolean?`, `comment: String?`, `creationDate: kotlinx.datetime.Instant?`, `repliesCount: Int?`, `likesCount: Int?`. The `parentId` field SHALL be encoded as `null` in Firestore (not omitted) when missing — matches iOS `LoopComment.encode(to:)` lines 60–65 where the iOS code calls `encodeNil` instead of skipping the field.

#### Scenario: LoopComment preserves null parentId for top-level comments
- **WHEN** a top-level comment is encoded with `parentId = null`
- **THEN** the Firestore document stores the `parentId` field as `null` (not omitted)
- **WHEN** a reply is encoded with `parentId = "parent-1"` non-null
- **THEN** the Firestore document stores `"parentId": "parent-1"`

#### Scenario: LoopComment deserializes from a Firestore document
- **WHEN** a Firestore `loopComments/{commentId}` document has every field present
- **THEN** every Kotlin property is populated
- **AND** a document with `parentId: null` deserializes to `LoopComment.parentId = null`
- **AND** a document without a `parentId` field deserializes to `LoopComment.parentId = null`

### Requirement: LoopLike and CommentLike models match iOS shape
The `LoopLike` `@Serializable` data class SHALL live at `core/model/LoopLike.kt` and SHALL expose: `id: String?`, `userId: String`, `loopId: String`, `creationDate: kotlinx.datetime.Instant?`. The `userId` and `loopId` fields SHALL be required (non-nullable) — matches iOS `LoopLike.swift` lines 12–16. The `CommentLike` data class SHALL live at `core/model/CommentLike.kt` and SHALL expose: `id: String?`, `userId: String`, `commentId: String`, `creationDate: kotlinx.datetime.Instant?` with the same required-field shape.

#### Scenario: LoopLike encodes the composite primary key
- **WHEN** a `LoopLike(userId = "u1", loopId = "l1", creationDate = Instant.DISTANT_PAST)` is encoded
- **THEN** the Firestore document path is `users/u1/likedLoops/l1`
- **AND** the document body contains `{"creationDate": <ts>}`
- **WHEN** the same key already exists in Firestore
- **THEN** the repository overwrites the document without error

#### Scenario: CommentLike encodes the comment-scoped primary key
- **WHEN** a `CommentLike(userId = "u1", commentId = "c1", creationDate = Instant.DISTANT_PAST)` is encoded
- **THEN** the Firestore document path is `loopComments/c1/likes/u1`

### Requirement: LoopCommentInfo projects LoopComment + User + like state for the UI layer
The `LoopCommentInfo` data class SHALL live at `core/model/LoopCommentInfo.kt` and SHALL expose: `comment: LoopComment`, `user: User?`, `isLikedByUser: Boolean`, `likesCount: Int`. The default `isLikedByUser` SHALL be `false` and `likesCount` SHALL be `0`. Mirrors iOS `LoopCommentInfo` struct.

#### Scenario: LoopCommentInfo composes comment + user + like state for rendering
- **WHEN** the loop detail VM resolves a list of comments and a parallel user + like-state fetch
- **THEN** it composes each row into `LoopCommentInfo(comment = ..., user = ..., isLikedByUser = ..., likesCount = ...)`
- **AND** the UI layer consumes `LoopCommentInfo` instead of raw `LoopComment`

### Requirement: LoopsFilter enum mirrors iOS LoopsFilterType cases
The `LoopsFilter` enum SHALL live at `core/model/LoopsFilter.kt` and SHALL expose exactly two cases: `ALL` and `YOUR_LOOPS` — matches iOS `LoopsFilterType.allCases` in `LoopsFilterView.swift` lines 10–13. The enum SHALL have a `displayName: String` property for the chip label: `ALL → "All"`, `YOUR_LOOPS → "Your Loops"`.

#### Scenario: LoopsFilter exposes both iOS cases with the same display names
- **WHEN** the Loops tab renders the filter chip row
- **THEN** `LoopsFilter.ALL` renders the chip "All"
- **AND** `LoopsFilter.YOUR_LOOPS` renders the chip "Your Loops"
- **AND** no other cases exist
