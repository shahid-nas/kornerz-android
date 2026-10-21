# user-notification-domain Specification

## Purpose
TBD - created by archiving change phase-6-profile-and-notifications. Update Purpose after archive.
## Requirements
### Requirement: UserNotification domain model mirrors iOS field-for-field
`UserNotification` SHALL be a `@Serializable` data class in `core/model` with these fields, exactly matching iOS `Models/UserNotification.swift` lines 11–42:
- `id: String` — Firestore document id (iOS CodingKeys: `notificationId`)
- `title: String?` — (CodingKeys: `notificationTitle`)
- `description: String?` — (CodingKeys: `notificationDescription`)
- `content: String?` — community name (CodingKeys: `notificationContent`)
- `image: String?` — IPFS hash (CodingKeys: `notificationCommunityImage`)
- `date: Timestamp?` — (CodingKeys: `dateNotification`)
- `reference: String?` — (CodingKeys: `notificationReferenceId`)
- `type: String?` — (CodingKeys: `notificationType`)
- `userId: String?` — (CodingKeys: `notificationUserId`)
- `communityId: String?` — (CodingKeys: `notificationCommunityId`)

Plus a derived `val thumbnailURL: String?` that returns `null` on Android (the v4 Firebase Storage migration makes the IPFS-based URL computation unreachable). iOS uses `IPFS.baseUrl + image` (line 38) — Android mirrors the structure but returns null per the migration note.

#### Scenario: Decoded from Firestore document
- **WHEN** a Firestore document with `{notificationId, notificationTitle, notificationDescription, notificationContent, notificationCommunityImage, dateNotification, notificationReferenceId, notificationType, notificationUserId, notificationCommunityId}` is deserialized via the `FirestoreRepository` helper
- **THEN** the `UserNotification` instance has all fields populated per the CodingKeys mapping

#### Scenario: Null thumbnail
- **WHEN** `UserNotification.image = "QmHash"` and Firebase Storage v4 migration is active
- **THEN** `UserNotification.thumbnailURL = null` (Android no longer resolves IPFS URLs)

### Requirement: UserNotificationDeeplinkType enum mirrors iOS deeplinkType raw values
`UserNotificationDeeplinkType` SHALL be an enum with cases `.newEvent`, `.newArticle`, `.newForum`, `.videoCall`, `.newComment`, `.newLoop`, `.loopComment`, parsing from `UserNotification.type` raw value. Mirrors iOS `UserNotification.deeplinkType` (lines 46–48).

#### Scenario: Unknown type returns null
- **WHEN** `UserNotification.type = "someUnknownType"`
- **THEN** `UserNotification.deeplinkType = null`

#### Scenario: Known type parses
- **WHEN** `UserNotification.type = "newLoop"`
- **THEN** `UserNotification.deeplinkType = .newLoop`

### Requirement: UserNotification.deeplink(with: Community?) resolves a DeepLink
`UserNotification.deeplink(with: Community?): DeepLink?` SHALL switch on `deeplinkType` and return:
- `.newEvent` → requires `reference != null && community.communityId != null` → `.newEvent(EventRef(eventId, communityId))`
- `.newArticle` → similar → `.newArticle(ArticleRef(articleId, communityId))`
- `.newForum` → similar → `.newForum(ForumRef(forumId, communityId))`
- `.videoCall` → requires `community.communityId != null` → `.videoCall(VideoCallRef(communityId))`
- `.newComment` → requires `community.communityId != null` (iOS line 65–67 has a known iOS bug routing newComment to videoCall — Android mirrors the iOS behavior verbatim for behavioral parity, but flags this as a known iOS bug; P10 polish will correct it)
- `.newLoop` → requires `reference != null && community.communityId != null` → `.newLoop(LoopRef(communityId, loopId))`
- `.loopComment` → requires `reference != null` → `.newLoopComment(LoopCommentRef(loopId))`

Mirrors iOS `UserNotification.deeplink(with:)` lines 50–75.

#### Scenario: newLoop deeplink resolves
- **WHEN** `notification.deeplinkType = .newLoop`, `notification.reference = "loop-123"`, and `community.communityId = "comm-456"`
- **THEN** `deeplink(with: community)` returns `.newLoop(LoopRef("comm-456", "loop-123"))`

#### Scenario: Missing reference returns null
- **WHEN** `notification.deeplinkType = .newLoop` and `notification.reference = null`
- **THEN** `deeplink(with: community)` returns `null`

