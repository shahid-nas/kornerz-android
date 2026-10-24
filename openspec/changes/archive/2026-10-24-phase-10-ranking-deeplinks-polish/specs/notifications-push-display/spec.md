# notifications-push-display Specification

## MODIFIED Requirements

### Requirement: KornerzFcmService extends FirebaseMessagingService
`KornerzFcmService` SHALL extend `com.google.firebase.messaging.FirebaseMessagingService` and SHALL be registered in `AndroidManifest.xml` with an intent-filter for `com.google.firebase.MESSAGING_EVENT` (canonical FCM service registration). The service SHALL handle three intents:

1. `onNewToken(token: String)` — calls `UserProfileRepository.updateFcmToken(token)` which writes `Users/{userCode}.tokenPush = token` via `setData(["tokenPush": token], merge: true)`. Mirrors iOS `FireStoreServiceManager.saveUserToken` lines 52–55.
2. `onMessageReceived(remoteMessage: RemoteMessage)` — for `remoteMessage.notification != null`, displays a system tray notification via `NotificationManagerCompat.notify` using the `kornerz_notifications` channel. For `remoteMessage.data.isNotEmpty()`, parses the payload into a `UserNotification` and stores it in the in-memory `NotificationRepository` cache.
3. Tapping a system tray notification SHALL carry the deep link derived from the push payload as the `PendingIntent`'s intent data, so the tap opens the app **and** routes to the resolved destination. The deep link SHALL be built from the push dialect iOS reads in `DeepLinksManager.handleDeepLink(info:)` — `pushType` plus `communityId` / `eventId` / `articleId` / `forumId` / `loopId` — not from the launch intent. A payload with no recognisable `pushType` SHALL fall back to the plain launch intent.

Mirrors iOS `AppDelegate.userNotificationCenter(_:didReceive:)` lines 170–175, which passes the tapped notification's `userInfo` straight to `DeepLinksManager.shared.handleDeepLink(info:)`.

#### Scenario: New token writes to Firestore
- **WHEN** FCM dispatches `onNewToken("fcm-token-xyz")` and `SessionStorage.userCode = "user-123"`
- **THEN** `UserProfileRepository.updateFcmToken("fcm-token-xyz")` writes `Users/user-123` with `tokenPush = "fcm-token-xyz"` and returns `Success(Unit)`

#### Scenario: No active session skips token write
- **WHEN** FCM dispatches `onNewToken("fcm-token-xyz")` and no user is signed in (`SessionStorage.userCode = null`)
- **THEN** `UserProfileRepository.updateFcmToken` short-circuits and returns `Success(Unit)` without writing to Firestore (the next sign-in re-registers)

#### Scenario: A video-call push carries its deep link
- **WHEN** FCM delivers a data payload with `pushType = "VideoCall"` and `communityId = "community-1"`
- **THEN** the tray notification's `PendingIntent` carries `kornerz://community/community-1/call` as its intent data

#### Scenario: A loop-comment push carries its deep link
- **WHEN** FCM delivers a data payload with `pushType = "LoopComment"` and `loopId = "loop-1"`
- **THEN** the tray notification's `PendingIntent` carries `kornerz://loop/loop-1`

#### Scenario: An unrecognised push type falls back to the launch intent
- **WHEN** FCM delivers a data payload whose `pushType` is missing or unknown
- **THEN** the tray notification's `PendingIntent` opens the app without a deep link, and the service does not throw

#### Scenario: The notification still displays on the channel
- **WHEN** FCM delivers a notification payload
- **THEN** the tray notification is shown on the `kornerz_notifications` channel with its title and body
