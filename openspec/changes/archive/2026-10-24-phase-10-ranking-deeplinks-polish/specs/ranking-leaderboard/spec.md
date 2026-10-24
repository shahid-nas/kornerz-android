# ranking-leaderboard Specification

## ADDED Requirements

### Requirement: Gamification document model mirrors the iOS field names
`core/model` SHALL declare a `Gamification` `@Serializable` data class modelling one `Gamefication/{id}` Firestore document, with the field names taken verbatim from iOS `Models/Gamification.swift:19-26`:

| Kotlin property | `@SerialName` | iOS `CodingKeys` |
|---|---|---|
| `id: String?` | `gameficationId` | `id = "gameficationId"` |
| `action: String?` | `gameficationAction` | `action = "gameficationAction"` |
| `date: Instant?` | `gameficationDateHora` | `date = "gameficationDateHora"` |
| `points: Int?` | `gameficationQtdPts` | `points = "gameficationQtdPts"` |
| `userId: String?` | `gameficationUserId` | `userId = "gameficationUserId"` |
| `referenceId: String?` | `gameficationReferenceId` | `referenceId = "gameficationReferenceId"` |

The collection name SHALL remain `Gamefication` — the backend's spelling omits the `m`, and iOS reads the same misspelled collection (`GamificationService.swift:27`). Android SHALL NOT introduce a migration.

The points field SHALL be read as `gameficationQtdPts`. A document that carries only the non-iOS spelling `gameficationPoints` MAY be read as a fallback, but `gameficationQtdPts` SHALL take precedence when both are present.

#### Scenario: Model carries the iOS field names
- **WHEN** the `Gamification` class is inspected
- **THEN** its `@SerialName` values are exactly `gameficationId`, `gameficationAction`, `gameficationDateHora`, `gameficationQtdPts`, `gameficationUserId`, and `gameficationReferenceId`

#### Scenario: Points come from gameficationQtdPts
- **WHEN** a `Gamefication` document contains `{"gameficationQtdPts": 42, "gameficationUserId": "user-1"}`
- **THEN** the decoded `Gamification.points` is `42` and `userId` is `"user-1"`

#### Scenario: Malformed or absent points degrade to zero
- **WHEN** a `Gamefication` document has no readable points field
- **THEN** the decoded `Gamification.points` is `null` and the aggregation treats it as `0`

### Requirement: Rank tier enum models the leaderboard medal
`core/model` SHALL declare a top-level `Rank` enum with exactly four cases — `GOLD`, `SILVER`, `BRONZE`, `NONE` — modelling the medal tier of a leaderboard row. `Rank` SHALL replace the nested `UserRanking.Medal` enum, which SHALL be removed.

`UserRanking` SHALL keep its existing shape — `userId`, `userName`, `points` — and SHALL expose `medal: Rank` in place of `medal: Medal`. The `@SerialName("madal")` on the property SHALL be preserved, because the wire spelling is the backend's.

`Rank` SHALL be assigned by the **final position** of a row in the sorted leaderboard: the first row is `GOLD`, the second `SILVER`, the third `BRONZE`, and every row after that is `NONE`. This mirrors iOS `GamificationService.fillMadalInformation` (`:154-166`).

#### Scenario: Rank cases are exhaustive
- **WHEN** the `Rank` enum is enumerated
- **THEN** it has exactly `GOLD`, `SILVER`, `BRONZE`, and `NONE`

#### Scenario: Medal is assigned by position
- **WHEN** the leaderboard returns four rows ordered by points descending
- **THEN** the rows carry `GOLD`, `SILVER`, `BRONZE`, and `NONE` respectively

#### Scenario: UserRanking exposes the Rank tier
- **WHEN** a `UserRanking` is constructed
- **THEN** its `medal` property is a `Rank` and defaults to `Rank.NONE`

### Requirement: GamificationRepository reads the current month's points for the signed-in user
`GamificationRepository.getUserPoints()` SHALL sum the `gameficationQtdPts` field over every `Gamefication` document whose `gameficationUserId` equals the signed-in user's `codeUser` and whose `gameficationDateHora` falls within the current calendar month in the device timezone — the range `[startOfMonth, endOfMonth)`.

It SHALL return `Result.success(0)` when the session is anonymous or the user has no `codeUser`, so the tab header always renders a value.

It SHALL mirror iOS `GamificationService.getUserPoints` (`:38-68`).

#### Scenario: Points sum to the signed-in user's monthly total
- **WHEN** the signed-in user has `codeUser = "user-1"` and two `Gamefication` documents this month with `gameficationQtdPts` of `10` and `15`
- **THEN** `getUserPoints()` returns `Result.success(25)`

#### Scenario: Points outside the month are excluded
- **WHEN** a `Gamefication` document for the signed-in user has a `gameficationDateHora` in the previous month
- **THEN** that document's points are not included in the total

#### Scenario: Anonymous session yields zero
- **WHEN** the session is anonymous
- **THEN** `getUserPoints()` returns `Result.success(0)` without querying Firestore

#### Scenario: The tab header chip shows the real total
- **WHEN** the shared tab header renders for a signed-in user with 25 monthly points
- **THEN** the header's points value is `25` and not `0`

### Requirement: GamificationRepository returns the top-ranked users with resolved names
`GamificationRepository.getRanking(count)` SHALL return the current month's leaderboard, ordered by total points descending, truncated to `count` rows.

The read SHALL follow iOS `GamificationService.getRanking` (`:106-140`):

1. Query the `Gamefication` collection with `gameficationDateHora` in `[startOfMonth, endOfMonth)`, paginating with `startAfter(lastDocument)` until a page comes back empty.
2. Aggregate the `gameficationQtdPts` of every document per `gameficationUserId`.
3. Sort by total points descending and truncate to `count` rows.
4. Resolve each row's user name with **one** batched query: `Users.whereEqualTo("codeUser", in: userIds)`, assigning `User.nickname` to the matching row's `userName`.
5. Assign the `Rank` tier by final position.

A row whose `codeUser` no longer resolves to a `Users` document SHALL keep the literal fallback `"User"` — the same value iOS uses when it creates the row (`GamificationService.swift:124`).

Rows with **equal** points SHALL keep their first-seen order, because the sort SHALL be stable. iOS's own sort is not stable, so its tie order is arbitrary; Android's deterministic tie-break is a deliberate divergence, documented here so it is not "fixed" back.

The method SHALL return `Result.failure` when the underlying query fails, and SHALL NOT swallow the exception.

#### Scenario: Ranking is ordered by points descending
- **WHEN** three users have monthly totals of `40`, `10`, and `25`
- **THEN** `getRanking(10)` returns them in the order `40`, `25`, `10`

#### Scenario: User names are resolved from the Users collection
- **WHEN** the aggregated user ids are `["user-1", "user-2"]` and `Users` documents with `codeUser = "user-1"` and `codeUser = "user-2"` carry `nickname` values `"Ana"` and `"Bo"`
- **THEN** the returned rows carry `userName = "Ana"` and `userName = "Bo"` respectively

#### Scenario: Unresolvable user keeps the fallback name
- **WHEN** an aggregated user id has no matching `Users` document
- **THEN** that row's `userName` is `"User"`

#### Scenario: Ranking is truncated to the requested count
- **WHEN** twelve users have monthly points and `getRanking(10)` is called
- **THEN** exactly 10 rows are returned, the ten highest-scoring ones

#### Scenario: Ties keep a deterministic order
- **WHEN** two users have the same total points
- **THEN** the row that was read first is returned first

#### Scenario: A query failure is surfaced
- **WHEN** the `Gamefication` query fails
- **THEN** `getRanking(10)` returns `Result.failure` and does not throw

### Requirement: The Klouders tab renders the leaderboard
The Klouders tab SHALL render the leaderboard screen instead of an empty-state placeholder. The screen SHALL be the iOS `RankingScreen` (2.7) hosted by the iOS `RankingViewController` (2.6) — `kornerz/Kornerz/HomeVC/Ranking/RankingScreen.swift` and `RankingViewController.swift`.

The leaderboard SHALL be laid out as a plain list (no separators) whose header row reads `#`, `Name`, and `Points` in three columns, the outer two of fixed width 75 pt and the middle column flexible. Each row SHALL contain, in order: a leading slot of fixed width 75 pt holding either a 40 × 40 pt medal image or the 1-based rank numeral rendered at 16 pt bold in the secondary content colour; the user's name in bold; and the row's points right-aligned in a 75 pt trailing slot.

The medal images SHALL be the iOS `medal-gold`, `medal-silver`, and `medal-bronze` assets, ported from `kornerz/Kornerz/Resources/Assets.xcassets/Images/Medal/` as Android vector drawables.

The screen SHALL render three states: a loading state, an error state with a working retry, and the loaded list. The empty case (no rows) SHALL render the shared no-data view.

The screen SHALL fetch the top **10** rows on appear, matching iOS `RankingViewModel.fetchRankings()` (`:51-70`, `gamification.getRanking(10)`).

The screen title SHALL be `Klouders`, the value of iOS's `klouders` localisation key (`RankingViewController.configureNavigation`, `:27-32`).

#### Scenario: Leaderboard rows render
- **WHEN** the leaderboard resolves three rows
- **THEN** the list renders three rows, each showing a rank numeral or medal, the user's name, and the points value

#### Scenario: Podium rows show medals
- **WHEN** the leaderboard resolves at least three rows
- **THEN** the first, second, and third rows show the gold, silver, and bronze medal images respectively

#### Scenario: Non-podium rows show the rank numeral
- **WHEN** the leaderboard resolves a row at position four or later
- **THEN** that row shows its 1-based position as a numeral instead of a medal

#### Scenario: The header row labels the columns
- **WHEN** the leaderboard list is rendered
- **THEN** a header row reads `#`, `Name`, and `Points`

#### Scenario: Exactly ten rows are requested
- **WHEN** the leaderboard screen appears
- **THEN** the repository is asked for the top 10 rows

#### Scenario: An error state offers a retry
- **WHEN** the leaderboard query fails
- **THEN** the shared retry view is rendered and tapping retry re-issues the query

#### Scenario: An empty leaderboard renders the no-data view
- **WHEN** the leaderboard query succeeds with zero rows
- **THEN** the shared no-data view is rendered instead of an empty list

### Requirement: The ranking row is exposed as a single accessibility node
Each leaderboard row SHALL be exposed to accessibility services as **one** node, whose description states the position, the user's name, and the points value together. The medal image and the rank numeral SHALL NOT be announced as separate nodes.

#### Scenario: A row is announced as one fragment
- **WHEN** a screen reader focuses the third leaderboard row
- **THEN** it announces the position, the name, and the points as a single utterance

#### Scenario: The medal is not announced separately
- **WHEN** a screen reader focuses a podium row
- **THEN** the medal image contributes no separate focusable node
