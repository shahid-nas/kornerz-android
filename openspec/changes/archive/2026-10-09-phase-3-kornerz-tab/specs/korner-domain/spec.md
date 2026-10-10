# korner-domain Specification

## Purpose
TBD - created by archiving change phase-3-kornerz-tab. Update Purpose after archive.
## ADDED Requirements
### Requirement: Korner model exists in core/model
The `core/model` module SHALL define a `Korner` data class annotated with `@Serializable` matching the public subset of iOS `Model/Community.swift` consumed by the Kornerz tab and Korner detail screens. The data class SHALL contain exactly these fields: `id: String` (Firestore document id), `name: String`, `themeColor: String` (hex string, mapped to the iOS palette when missing — same fallback as `home-kornerz-carousel`), `coverUrl: String?`, `description: String?`, `membersCount: Int`, `isLive: Boolean` (true when at least one nook has `isLive = true`), and `isJoined: Boolean` (true when the signed-in user is a member).

#### Scenario: Korner is constructed from a Firestore document
- **WHEN** `KornerzRepository.fetchAllKornerz()` maps a Firestore `communities/{id}` document to a `Korner`
- **THEN** every documented field is populated from the corresponding Firestore field
- **AND** missing optional fields (`coverUrl`, `description`) default to `null`
- **AND** the resulting `Korner` is a valid `@Serializable` value

### Requirement: KornerDetail aggregate is composed from Korner, nooks, and members
The `core/model` module SHALL define a `KornerDetail` data class annotated with `@Serializable` that aggregates the data needed by the Korner detail screen. The data class SHALL contain exactly these fields: `korner: Korner`, `nooks: List<Nook>`, and `members: List<User>` (where `User` is the existing model from P1). The data class SHALL provide no behavior beyond data holding.

#### Scenario: KornerDetail is constructed from parallel reads
- **WHEN** `KornerzRepository.fetchKornerDetail(kornerId)` resolves three Firestore reads into a `KornerDetail`
- **THEN** `korner` is populated from the `communities/{kornerId}` document
- **AND** `nooks` is populated from the `communities/{kornerId}/events` collection
- **AND** `members` is populated from the `communities/{kornerId}/members` collection (capped at 50)

### Requirement: Nook model exists in core/model
The `core/model` module SHALL define a `Nook` data class annotated with `@Serializable` matching the public subset of iOS `Model/Event.swift` consumed by the Korner detail "Current Nook" and "Nooks list" sections. The data class SHALL contain exactly these fields: `id: String`, `communityId: String`, `title: String`, `coverUrl: String?`, `startDate: Instant` (kotlinx-datetime, mapped from Firestore `Timestamp`), and `isLive: Boolean`.

#### Scenario: Nook is constructed from a Firestore event document
- **WHEN** `KornerzRepository.fetchKornerDetail(kornerId)` maps a Firestore `communities/{id}/events/{eventId}` document to a `Nook`
- **THEN** every documented field is populated from the corresponding Firestore field
- **AND** `startDate` is converted from Firestore `Timestamp` to `kotlinx.datetime.Instant`

### Requirement: User model from P1 is reused for members
The Korner detail Members section SHALL reuse the existing `User` data class from P1 (`core/model/User.kt`) without modification. No new fields are added in P3.

#### Scenario: Members list renders existing User instances
- **WHEN** `KornerzRepository.fetchKornerDetail(kornerId)` maps the `communities/{id}/members` collection to a list of `User`
- **THEN** each member is a `User` instance with the fields populated by the existing P1 mapper
- **AND** the Korner detail Members section renders them via the existing avatar + name components