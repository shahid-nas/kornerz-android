# notifications-push-display Specification

## Purpose
TBD - created by archiving change phase-6-profile-and-notifications. Update Purpose after archive.
## Requirements
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

### Requirement: KornerzFcmService displays notification messages via channel
When `onMessageReceived` is called with `remoteMessage.notification != null`, the service SHALL build a `NotificationCompat.Builder` with:
- channelId = `kornerz_notifications`
- smallIcon = `R.drawable.ic_stat_kornerz` (or fallback)
- contentTitle = `remoteMessage.notification.title`
- contentText = `remoteMessage.notification.body`
- autoCancel = true
- contentIntent = PendingIntent to launch the splash activity

And SHALL call `NotificationManagerCompat.notify(notificationId.hashCode(), builder.build())`. Mirrors iOS `UNUserNotificationCenter` tray notification display.

#### Scenario: Notification message displays in tray
- **WHEN** FCM dispatches a message with `notification.title = "New comment"` and `notification.body = "Alice commented on your loop"` and the `kornerz_notifications` channel exists
- **THEN** a system tray notification appears with the title and body text

#### Scenario: Notification posted on Android 13+ requires permission
- **WHEN** FCM dispatches a notification message on an Android 13+ device where `POST_NOTIFICATIONS` is denied
- **THEN** the notification does not appear in the tray and no crash occurs

### Requirement: KornerzFcmService parses data payloads into UserNotification
When `onMessageReceived` is called with `remoteMessage.data.isNotEmpty()`, the service SHALL parse the `data` map into a `UserNotification` using the CodingKeys mapping (e.g., `data["notificationType"]` → `type`, `data["notificationReferenceId"]` → `reference`, etc.) and SHALL push it to the in-memory cache held in `NotificationRepository`. The cache SHALL be read by `NotificationsViewModel` on next page fetch to inject new items at the top. Mirrors iOS `NotificationsViewModel` reading from the local cache (iOS does not persist; the Android port keeps the no-firmware surface for persistence but inlines an in-memory cache).

#### Scenario: Data message parsed and cached
- **WHEN** FCM dispatches a data message with `{notificationType: "loopComment", notificationReferenceId: "loop-123", notificationUserId: "user-456", notificationTitle: "New comment", notificationDescription: "Alice replied", notificationContent: "LoopName", dateNotification: "1730000000", notificationCommunityId: "comm-789", notificationCommunityImage: "QmHash"}`
- **THEN** a `UserNotification` instance is created and prepended to `NotificationRepository.recentDataMessages` so the next `NotificationsScreen` open shows it at the top of the list

### Requirement: kornerz_notifications channel created at app startup
`MainActivity.onCreate` (or an `Application.onCreate` hook) SHALL call `NotificationChannelCompat.Builder("kornerz_notifications", NotificationManagerCompat.IMPORTANCE_HIGH).setName("Kornerz").setDescription("Notifications for comments, replies, community events").setLightsEnabled(true).setVibrationEnabled(true).build()` and SHALL pass the channel to `NotificationManagerCompat.createNotificationChannel(channel)`. The channel SHALL only be created on Android 8+ (system requirement); on older versions the channel argument is a no-op.

#### Scenario: Channel exists at startup
- **WHEN** the app launches on Android 8+
- **THEN** `kornerz_notifications` channel is registered with the system and FCM notification messages route to it

### Requirement: Android 13+ POST_NOTIFICATIONS permission requested at first session
When `SessionStorage.userCode` transitions from `null` to a non-null value for the first time (i.e., the user signs in for the first time), the app SHALL request `android.permission.POST_NOTIFICATIONS` via Accompanist `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`. On Android < 13 the permission is implicitly granted and no UI is shown. Mirrors the iOS implicit push-permission flow (iOS prompts the first time the app calls `registerForRemoteNotifications`; Android 13+ mirrors that UX with a runtime-permission dialog).

#### Scenario: First sign-in on Android 13+ prompts for permission
- **WHEN** user signs in for the first time on an Android 13+ device
- **THEN** the system permission dialog for `POST_NOTIFICATIONS` appears

#### Scenario: First sign-in on Android < 13 skips dialog
- **WHEN** user signs in for the first time on an Android < 13 device
- **THEN** no permission dialog is shown (implicit grant)

#### Scenario: Permission denied does not block sign-in
- **WHEN** user denies `POST_NOTIFICATIONS`
- **THEN** sign-in completes, the user lands on the home tab, and FCM messages are silently dropped at the system level (no in-app error)

