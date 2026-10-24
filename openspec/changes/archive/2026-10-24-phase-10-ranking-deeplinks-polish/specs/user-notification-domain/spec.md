# user-notification-domain Specification

## MODIFIED Requirements

### Requirement: UserNotificationDeeplinkType enum mirrors iOS deeplinkType raw values
`UserNotificationDeeplinkType` SHALL be an enum with cases `.newEvent`, `.newArticle`, `.newForum`, `.videoCall`, `.newComment`, `.newLoop`, `.loopComment`, parsing from `UserNotification.type` raw value.

The raw values it parses SHALL be iOS's `DeeplinkType` raw values (`kornerz/Kornerz/DeepLinks/DeeplinkManager.swift:56-64`): `NewEvent`, `NewArticle`, `NewForum`, `VideoCall`, `NewComment`, `NewLoop`, `LoopComment`. The comparison SHALL be **case-insensitive**, so the lower-camel spellings are also accepted.

Mirrors iOS `UserNotification.deeplinkType` (lines 46–48), which is `DeeplinkType(rawValue: type ?? "")`.

#### Scenario: Unknown type returns null
- **WHEN** `UserNotification.type = "someUnknownType"`
- **THEN** `UserNotification.deeplinkType = null`

#### Scenario: Known type parses
- **WHEN** `UserNotification.type = "NewLoop"`
- **THEN** `UserNotification.deeplinkType = .newLoop`

#### Scenario: The capitalised server value parses
- **WHEN** `UserNotification.type = "NewEvent"`
- **THEN** `UserNotification.deeplinkType = .newEvent`

#### Scenario: The lower-camel spelling still parses
- **WHEN** `UserNotification.type = "newLoop"`
- **THEN** `UserNotification.deeplinkType = .newLoop`

#### Scenario: Every iOS raw value is covered
- **WHEN** the seven iOS `DeeplinkType` raw values are parsed one by one
- **THEN** each yields its matching enum case and none yields `UNKNOWN`

### Requirement: UserNotification.deeplink(with: Community?) resolves a DeepLink
`UserNotification.deeplink(with: Community?): DeepLink?` SHALL switch on `deeplinkType` and return:
- `.newEvent` → requires `reference != null` and a resolvable community id → `DeepLink.NewEvent(eventId = reference, communityId = communityId)`
- `.newArticle` → requires `reference != null` and a resolvable community id → `DeepLink.NewArticle(articleId = reference, communityId = communityId)`
- `.newForum` → requires `reference != null` and a resolvable community id → `DeepLink.NewForum(forumId = reference, communityId = communityId)`
- `.videoCall` → requires a resolvable community id → `DeepLink.VideoCall(communityId = communityId)`
- `.newComment` → requires `reference != null` → `DeepLink.NewComment(forumId = reference)`
- `.newLoop` → requires `reference != null` and a resolvable community id → `DeepLink.NewLoop(communityId = communityId, loopId = reference)`
- `.newLoopComment` → requires `reference != null` → `DeepLink.NewLoopComment(loopId = reference)`

The **resolvable community id** SHALL be `community.communityId` when it is non-blank, falling back to `community.id` when it is null or blank. The repositories populate `community.communityId` alongside `community.id`, so the fallback only matters for a community assembled outside the repositories.

The method SHALL return `null` when the community is `null` and the case requires a community id.

**This method SHALL NOT port iOS's `newComment` bug.** iOS `Models/UserNotification.swift:65-67` returns `.videoCall(...)` for the `.newComment` case; that branch is unreachable in iOS because `NotificationsViewModel.notificationOpened` short-circuits `.newComment` before calling `deeplink(with:)`. Android resolves `.newComment` to `DeepLink.NewComment`.

Mirrors iOS `UserNotification.deeplink(with:)` lines 50–75, minus the unreachable bug.

#### Scenario: newLoop deeplink resolves
- **WHEN** `notification.deeplinkType = .newLoop`, `notification.reference = "loop-123"`, and `community.communityId = "comm-456"`
- **THEN** `deeplink(with: community)` returns `DeepLink.NewLoop(communityId = "comm-456", loopId = "loop-123")`

#### Scenario: Missing reference returns null
- **WHEN** `notification.deeplinkType = .newLoop` and `notification.reference = null`
- **THEN** `deeplink(with: community)` returns `null`

#### Scenario: Missing community returns null
- **WHEN** `notification.deeplinkType = .videoCall` and `community` is `null`
- **THEN** `deeplink(with: community)` returns `null`

#### Scenario: newComment resolves to a forum link, not a video call
- **WHEN** `notification.deeplinkType = .newComment` and `notification.reference = "forum-1"`
- **THEN** `deeplink(with: community)` returns `DeepLink.NewComment(forumId = "forum-1")`

#### Scenario: loopComment needs no community
- **WHEN** `notification.deeplinkType = .newLoopComment`, `notification.reference = "loop-1"`, and `community` is `null`
- **THEN** `deeplink(with: community)` returns `DeepLink.NewLoopComment(loopId = "loop-1")`

## ADDED Requirements

### Requirement: The Community model names both community identifiers
`core/model`'s `Community` SHALL expose a `communityId` property mapped to the `communityId` Firestore field, so the community's public identifier is nameable separately from the model's `id`.

The property SHALL be nullable with a `null` default, so no existing construction site is required to change.

The `communityId` field and the Firestore document id are **different** values on the same document. iOS resolves a community by the `communityId` field and then overwrites the model's id with the document id (`kornerz/Kornerz/FirestoreService/APICommunity.swift:475`, `:483`).

#### Scenario: The property exists and defaults to null
- **WHEN** a `Community` is constructed without a `communityId`
- **THEN** its `communityId` is `null`

#### Scenario: The property is populated from the field
- **WHEN** a community is read from a document whose `communityId` field is `community-1`
- **THEN** the `Community.communityId` is `community-1`
