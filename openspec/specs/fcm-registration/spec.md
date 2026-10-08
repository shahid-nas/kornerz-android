# fcm-registration Specification

## Purpose
TBD - created by archiving change phase-1-auth-onboarding. Update Purpose after archive.
## Requirements
### Requirement: FCM token is registered on every authenticated app start
The app SHALL, on every `Application.onCreate()` after a valid session is restored, request a Firebase Cloud Messaging token via `FirebaseMessaging.getInstance().token` and, when a token is obtained, call the `updateFcmToken` Cloud Function with the token and the current user's ID. If the token is `null` (FCM not yet ready), the registrar SHALL retry up to 3 times with exponential backoff.

#### Scenario: Token obtained on cold start
- **WHEN** the app launches with a valid session and FCM returns a token
- **THEN** the `updateFcmToken` Cloud Function is called once with the token
- **AND** the call succeeds silently (no UI is shown)

#### Scenario: FCM not ready on cold start
- **WHEN** the app launches and `FirebaseMessaging.getInstance().token` returns `null` initially
- **THEN** the registrar retries up to 3 times
- **AND** gives up silently after the 3rd failure (logged as a warning)

### Requirement: FCM token is refreshed on token rotation
The app SHALL subscribe to `FirebaseMessaging.getInstance().token` (which is a Flow on the Firebase KTX API) and call `updateFcmToken` whenever the token value changes while a session is active.

#### Scenario: Token rotates while signed in
- **WHEN** Firebase rotates the device token and the user has a valid session
- **THEN** the `updateFcmToken` Cloud Function is called with the new token

### Requirement: FCM token is cleared on logout
The app SHALL, on logout, call the `clearFcmToken` Cloud Function (passing the current token) before `SessionRepository.clearSession()` so the server stops targeting this device.

#### Scenario: Logout clears token
- **WHEN** the user logs out
- **THEN** `clearFcmToken` is called with the last known token
- **AND** `SessionRepository.clearSession()` runs immediately after

### Requirement: No POST_NOTIFICATIONS prompt in P1
The app SHALL NOT request the `android.permission.POST_NOTIFICATIONS` runtime permission in P1. The notification permission flow is added in P6 when notifications UI ships.

#### Scenario: No permission prompt at signup or login
- **WHEN** the user completes signup or login
- **THEN** no system permission dialog is displayed
