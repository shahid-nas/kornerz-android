# gamification-events Specification

## References
- [`docs/architecture.md`](../../../../docs/architecture.md) — MVVM + Repository + UDF, repository return types, `AsyncValue<T>`, Hilt scopes, the "no libraries outside §3" rule.
- `kornerz/Kornerz/FirestoreService/GamificationService.swift` — iOS source-of-truth for the `addPoints` write path and the `Gamefication` collection.
- `kornerz/Kornerz/Models/Gamification.swift` — iOS source-of-truth for the document's field names.
- `kornerz/Kornerz/SwiftUI/Dashboard/Loops/LoopDetail/LoopDetailController.swift` — iOS source-of-truth for the `ACCESS_LOOP` award on loop open.
- `kornerz/Kornerz/Extensions/UIViewController+Ext.swift` — iOS source-of-truth for the `ACCESS_REPLAYS` award on video play.
- `kornerz/Kornerz/SwiftUI/Dashboard/Loops/Comments/Create/CreateCommentViewModel.swift` — iOS source-of-truth for the `ACCESS_COMMENT` award on comment creation.
- `openspec/changes/phase-10-ranking-deeplinks-polish/specs/ranking-leaderboard/spec.md` — the `Gamification` document model and the `Gamefication` collection spelling established by P10.
- [`openspec/changes/loop-detail-ios-parity/proposal.md`](../proposal.md) — capability rationale for the iOS parity work.

## ADDED Requirements

### Requirement: GamificationRepository can write a Gamefication document
`core/network` SHALL expose a write path for Klouders points. `GamificationRepository.addPoints(action: String, points: Int, referenceId: String)` SHALL write one document to the **`Gamefication`** collection — the backend's misspelled spelling, read and written verbatim — using a randomly generated document id. The document SHALL carry the field names iOS uses:

| Kotlin parameter | Firestore field |
|---|---|
| `action` | `gameficationAction` |
| `points` | `gameficationQtdPts` |
| now | `gameficationDateHora` |
| the signed-in user's `codeUser` | `gameficationUserId` |
| `referenceId` | `gameficationReferenceId` |

The method SHALL return `Result<Unit>` and SHALL **never throw** — a failed write SHALL be reported as `Result.failure`. The method SHALL be a no-op returning `Result.success(Unit)` when no signed-in user with a `codeUser` resolves, so an anonymous session never writes a `Gamefication` document with an empty user id.

The write SHALL NOT change the shape of the collection: the document it writes SHALL be readable by the existing P10 read path (`getUserPoints`, `getRanking`), which reads `gameficationQtdPts` and aggregates per `gameficationUserId`.

#### Scenario: A point is written with the iOS field names
- **WHEN** `addPoints(action = "ACCESS_LOOP", points = 1, referenceId = "l1")` is invoked for a user with `codeUser = "u1"`
- **THEN** a document is created under `Gamefication/{generatedId}`
- **AND** the document body is `{"gameficationAction": "ACCESS_LOOP", "gameficationQtdPts": 1, "gameficationDateHora": <ts>, "gameficationUserId": "u1", "gameficationReferenceId": "l1"}`

#### Scenario: No document is written for an anonymous session
- **WHEN** no signed-in user with a `codeUser` resolves
- **THEN** `addPoints(...)` returns `Result.success(Unit)`
- **AND** no `Gamefication` document is created

#### Scenario: A failed write is reported, not thrown
- **WHEN** the Firestore write fails
- **THEN** `addPoints(...)` returns `Result.failure`
- **AND** no exception escapes the method

#### Scenario: The written document is readable by the leaderboard
- **WHEN** a `Gamefication` document is written by `addPoints` and later read by `getRanking`
- **THEN** the document's `gameficationQtdPts` contributes to the row for `gameficationUserId`

### Requirement: Loop engagement awards Klouders points on the three iOS triggers
The Android app SHALL award Klouders points on the same three triggers iOS does, using the iOS action names and point values:

| Trigger | `gameficationAction` | `gameficationQtdPts` | `gameficationReferenceId` |
|---|---|---|---|
| A loop detail is opened | `ACCESS_LOOP` | 1 | the loop id |
| A loop's video is played | `ACCESS_REPLAYS` | 1 | the video URL |
| A comment is posted | `ACCESS_COMMENT` | 3 | the loop id |

The loop-open award SHALL fire **once per opening of the detail screen**, alongside the `viewCounts` increment, and SHALL NOT re-fire on a configuration change. The video award SHALL fire each time the modal player is opened for a video loop, and SHALL NOT fire for a still-image loop. The comment award SHALL fire once per successfully created comment.

None of the three awards SHALL block the user-facing action it accompanies: the loop still renders, the video still plays, and the comment is still created when the `Gamefication` write fails.

#### Scenario: Opening a loop awards an ACCESS_LOOP point
- **WHEN** a signed-in user opens the loop detail for `loopId = "l1"`
- **THEN** a `Gamefication` document is written with `gameficationAction = "ACCESS_LOOP"`, `gameficationQtdPts = 1`, and `gameficationReferenceId = "l1"`

#### Scenario: The loop-open award fires only once per opening
- **WHEN** the loop detail rotates after the first award
- **THEN** no second `ACCESS_LOOP` document is written for that opening

#### Scenario: Playing a loop video awards an ACCESS_REPLAYS point
- **WHEN** the user opens the video player for a loop with `firebaseVideoUrl = "https://cdn.example.com/v.mp4"`
- **THEN** a `Gamefication` document is written with `gameficationAction = "ACCESS_REPLAYS"`, `gameficationQtdPts = 1`, and `gameficationReferenceId = "https://cdn.example.com/v.mp4"`

#### Scenario: A still-image loop awards no replay point
- **WHEN** the user taps the media area of a loop with `isVideo = false`
- **THEN** no `ACCESS_REPLAYS` document is written

#### Scenario: Posting a comment awards an ACCESS_COMMENT point
- **WHEN** a signed-in user successfully posts a comment on `loopId = "l1"`
- **THEN** a `Gamefication` document is written with `gameficationAction = "ACCESS_COMMENT"`, `gameficationQtdPts = 3`, and `gameficationReferenceId = "l1"`

#### Scenario: A failed award does not fail the action
- **WHEN** the `Gamefication` write fails while the user is posting a comment
- **THEN** the comment is still created and still appears in the comment list
- **AND** the failure is not surfaced as a screen error
