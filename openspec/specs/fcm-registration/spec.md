# fcm-registration Specification

## Purpose
TBD - created by archiving change phase-1-auth-onboarding. Update Purpose after archive.
## Requirements
### Requirement: FCM token is registered on every authenticated app start
The app SHALL, on every `Application.onCreate()` after a valid session is restored, request a Firebase Cloud Messaging token via `FirebaseMessaging.getInstance().token` and, when a token is obtained, call `UserProfileRepository.updateFcmToken(token)` which writes `Users/{userCode}.tokenPush = token` via `setData(["tokenPush": token], merge: true)`. If the token is `null` (FCM not yet ready), the registrar SHALL retry up to 3 times with exponential backoff.

(modified to switch the write path from `updateFcmToken` Cloud Function to direct Firestore `Users/{userCode}.tokenPush` set, mirroring iOS `FireStoreServiceManager.saveUserToken` lines 52–55)

#### Scenario: Token obtained on cold start
- **WHEN** the app launches with a valid session and FCM returns a token
- **THEN** `UserProfileRepository.updateFcmToken(token)` writes `Users/{userCode}.tokenPush = token` via `setData(["tokenPush": token], merge: true)`
- **AND** the write succeeds silently (no UI is shown)

#### Scenario: FCM not ready on cold start
- **WHEN** the app launches and `FirebaseMessaging.getInstance().token` returns `null` initially
- **THEN** the registrar retries up to 3 times
- **AND** gives up silently after the 3rd failure (logged as a warning)

### Requirement: FCM token is refreshed on token rotation
The app SHALL subscribe to `FirebaseMessaging.getInstance().token` (which is a Flow on the Firebase KTX API) and call `UserProfileRepository.updateFcmToken(newToken)` whenever the token value changes while a session is active.

(modified to use direct Firestore write instead of Cloud Function)

#### Scenario: Token rotates while signed in
- **WHEN** Firebase rotates the device token and the user has a valid session
- **THEN** `UserProfileRepository.updateFcmToken(newToken)` writes `Users/{userCode}.tokenPush = newToken`

### Requirement: FCM token is cleared on logout
The app SHALL, on logout (and on account deletion), call `UserProfileRepository.removeFcmToken()` which deletes `Users/{userCode}.tokenPush` from Firestore before `SessionStorage.clear()` runs so the server stops targeting this device.

(modified to switch the clear path from `clearFcmToken` Cloud Function to direct Firestore `Users/{userCode}.tokenPush` delete)

#### Scenario: Logout clears token
- **WHEN** the user logs out
- **THEN** `UserProfileRepository.removeFcmToken()` deletes `Users/{userCode}.tokenPush`
- **AND** `SessionStorage.clear()` runs immediately after

#### Scenario: Account deletion clears token
- **WHEN** the user confirms account deletion and `deleteUserAccount` Cloud Function returns success
- **THEN** `UserProfileRepository.removeFcmToken()` deletes `Users/{userCode}.tokenPush`
- **AND** `SessionStorage.clear()` runs immediately after
- **AND** the shell routes back to Login

### Requirement: No POST_NOTIFICATIONS prompt in P1
The app SHALL NOT request the `android.permission.POST_NOTIFICATIONS` runtime permission in P1. The notification permission flow is added in P6 when notifications UI ships.

#### Scenario: No permission prompt at signup or login
- **WHEN** the user completes signup or login
- **THEN** no system permission dialog is displayed

### Requirement: POST_NOTIFICATIONS permission requested on Android 13+ at first sign-in
The app SHALL request `android.permission.POST_NOTIFICATIONS` at first sign-in (i.e., the first time `SessionStorage.userCode` transitions from `null` to a non-null value after a fresh install or after account deletion). On Android 13+ the request SHALL use Accompanist `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` and SHALL NOT block sign-in if the user denies. On Android < 13 the request is implicit and no UI is shown.

(modified to add the Android 13+ permission flow that P1 deferred)

#### Scenario: First sign-in on Android 13+ prompts for permission
- **WHEN** user signs in for the first time on an Android 13+ device
- **THEN** the system permission dialog for `POST_NOTIFICATIONS` appears

#### Scenario: First sign-in on Android < 13 skips dialog
- **WHEN** user signs in for the first time on an Android < 13 device
- **THEN** no permission dialog is shown (implicit grant)

#### Scenario: Permission denied does not block sign-in
- **WHEN** user denies `POST_NOTIFICATIONS`
- **THEN** sign-in completes, the user lands on the home tab, and FCM messages are silently dropped at the system level (no in-app error)

### Requirement: KornerzFcmService displays notification messages via the kornerz_notifications channel
The app SHALL register `KornerzFcmService` (a `FirebaseMessagingService` subclass) in `AndroidManifest.xml` with the canonical FCM service intent-filter. The service SHALL handle `onMessageReceived(remoteMessage: RemoteMessage)` by displaying a system tray notification via `NotificationManagerCompat.notify` when `remoteMessage.notification != null`, using the `kornerz_notifications` channel (created at app startup with `IMPORTANCE_HIGH`, lights, sound, vibration). Tapping a tray notification SHALL open the main activity; tap-to-deep-link routing is P10.

(new requirement added in P6, `notifications-push-display` capability)

#### Scenario: Notification message displays in tray
- **WHEN** FCM dispatches a message with `notification.title = "New comment"` and `notification.body = "Alice commented on your loop"` and the `kornerz_notifications` channel exists
- **THEN** a system tray notification appears with the title and body text

#### Scenario: Notification posted on Android 13+ requires permission
- **WHEN** FCM dispatches a notification message on an Android 13+ device where `POST_NOTIFICATIONS` is denied
- **THEN** the notification does not appear in the tray and no crash occurs

### Requirement: KornerzFcmService parses data payloads into UserNotification and pushes them to the in-memory cache
When `onMessageReceived(remoteMessage)` is called with `remoteMessage.data.isNotEmpty()`, `KornerzFcmService` SHALL parse the `data` map into a `UserNotification` instance using the CodingKeys mapping documented in the `user-notification-domain` spec and SHALL push it to the in-memory cache held in `NotificationRepository`. The cache SHALL be read by `NotificationsViewModel` on next page fetch so new items appear at the top of the list.

(new requirement added in P6)

#### Scenario: Data message parsed and cached
- **WHEN** FCM dispatches a data message with `{notificationType: "loopComment", notificationReferenceId: "loop-123", notificationUserId: "user-456", notificationTitle: "New comment", notificationDescription: "Alice replied", notificationContent: "LoopName", dateNotification: "1730000000", notificationCommunityId: "comm-789", notificationCommunityImage: "QmHash"}`
- **THEN** a `UserNotification` instance is created and prepended to `NotificationRepository.recentDataMessages` so the next `NotificationsScreen` open shows it at the top of the list

### Requirement: kornerz_notifications channel created at app startup
`MainActivity.onCreate` (or an `Application.onCreate` hook) SHALL call `NotificationChannelCompat.Builder("kornerz_notifications", NotificationManagerCompat.IMPORTANCE_HIGH).setName("Kornerz").setDescription("Notifications for comments, replies, community events").setLightsEnabled(true).setVibrationEnabled(true).build()` and SHALL pass the channel to `NotificationManagerCompat.createNotificationChannel(channel)`. The channel SHALL only be created on Android 8+ (system requirement); on older versions the channel argument is a no-op.

(new requirement added in P6)

#### Scenario: Channel exists at startup
- **WHEN** the app launches on Android 8+
- **THEN** `kornerz_notifications` channel is registered with the system and FCM notification messages route to it

