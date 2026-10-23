# notifications-list Specification

## MODIFIED Requirements

### Requirement: Tapping a notification opens its deeplink or fallback alert
Tapping a `NotificationItem` SHALL invoke `NotificationsViewModel.onNotificationOpened(notification)`. The ViewModel SHALL:
1. If `notification.deeplinkType` is `NewLoop` or `NewLoopComment` and `notification.reference != null`, emit `NotificationsNavEvent.OpenLoopComment(loopId = reference)`.
2. If `notification.deeplinkType` is `NewComment` and `notification.reference != null`, emit `NotificationsNavEvent.OpenForumComment(forumId = reference)`.
3. Otherwise fetch the `Community` (by `communityId` if present, else by `content` name) and resolve the deeplink via `UserNotification.deeplink(with: Community)`. If a deeplink resolves, emit `NotificationsNavEvent.Open(deeplink)` carrying the typed `DeepLink`. If the Community lookup fails or no deeplink resolves, emit `NotificationsNavEvent.ShowAlert(message = "Community not found")`.

The `Open(deeplink)` event SHALL be handled by the shell by routing the deep link through the `DeepLinkResolver` (P10, `deep-link-routing` capability) instead of showing a placeholder. A deep link whose target screen is excluded from the Android port SHALL surface the shared "Failed to load deeplink. Will navigate back to home screen" alert and return the user to the Home tab.

The `"Deeplink coming in Phase 10"` placeholder SHALL be removed.

Mirrors iOS `NotificationsViewModel.notificationOpened` lines 121–151.

#### Scenario: Loop-comment tap opens the loop
- **WHEN** user taps an item with `deeplinkType = NewLoopComment` and `reference = "loop-123"`
- **THEN** `NotificationsNavEvent.OpenLoopComment(loopId = "loop-123")` is emitted and the shell routes to `loops/loop-123`

#### Scenario: A new-loop tap opens the loop
- **WHEN** user taps an item with `deeplinkType = NewLoop` and `reference = "loop-123"`
- **THEN** `NotificationsNavEvent.OpenLoopComment(loopId = "loop-123")` is emitted and the shell routes to `loops/loop-123`

#### Scenario: A video-call tap routes to the Korner detail
- **WHEN** user taps an item with `deeplinkType = VideoCall` and a resolvable community
- **THEN** a typed video-call deep link is emitted and the shell routes to the Korner detail for that community

#### Scenario: A tap on an excluded target surfaces the alert
- **WHEN** user taps an item whose type targets a screen excluded from the Android port (Event Detail, Post Detail, Forum Detail)
- **THEN** the shared "Failed to load deeplink. Will navigate back to home screen" alert is shown and the user is returned to the Home tab

#### Scenario: A missing community surfaces the alert
- **WHEN** user taps an item whose community cannot be resolved
- **THEN** `NotificationsNavEvent.ShowAlert(message = "Community not found")` is emitted

#### Scenario: No placeholder snackbar remains
- **WHEN** the notifications screen's deep-link handler is inspected
- **THEN** it contains no "Deeplink coming in Phase 10" message
