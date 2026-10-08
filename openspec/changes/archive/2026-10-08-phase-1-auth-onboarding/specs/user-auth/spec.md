## References

- [`docs/architecture.md`](../../../../docs/architecture.md) — MVVM + Repository + UDF, `AsyncValue<T>`, `Channel<NavEvent>`, Hilt scopes, `kotlinx.serialization`, shared `core/ui/components/` (notably `KornerzPasswordField`), canonical library list, clean-code rules §7.
- [`docs/phases.md` §Phase 1](../../../../docs/phases.md) — source of truth for P1 scope and exit checklist.
- [`openspec/specs/design-system/spec.md`](../../../specs/design-system/spec.md) — color tokens, Satoshi font, dark/light themes applied to every new screen.
- [`openspec/specs/android-app-shell/spec.md`](../../../specs/android-app-shell/spec.md) — shell, navigation graph boundaries, modified by this change.

## ADDED Requirements

### Requirement: Login screen authenticates against custom-auth backend
The app SHALL provide a Login screen reachable as the entry point of the auth graph. The Login screen SHALL collect email and password via `KornerzTextField` and `KornerzPasswordField`, validate them locally, and call the custom-auth `login` Cloud Function through `AuthRepository.login(...)`. On success, the returned auth token and `User` SHALL be persisted in `SessionRepository`, the `LoginViewModel` SHALL emit a `NavEvent.NavigateToShell` over its `Channel<NavEvent>`, and the root NavHost SHALL switch from `auth_graph` to `shell_graph` clearing the back-stack. The ViewModel SHALL expose `state: StateFlow<LoginState>` where `submit: AsyncValue<Unit>` follows the architecture's `AsyncValue<T>` sealed interface (`Loading` / `Success` / `Error`) — Composables SHALL render exactly one of three layouts by exhaustive `when` on `submit`. On `AsyncValue.Error`, the error message SHALL be "Email or password is incorrect" for credential errors or the raw server message otherwise.

#### Scenario: Successful login
- **WHEN** the user submits a valid email and password
- **THEN** the login Cloud Function returns a token and a `User`
- **AND** `SessionRepository` persists both
- **AND** the ViewModel emits `NavEvent.NavigateToShell` over `Channel<NavEvent>`
- **AND** the root NavHost swaps to the shell graph

#### Scenario: Wrong password
- **WHEN** the user submits a valid email and an incorrect password
- **THEN** `AuthRepository.login(...)` returns `Result.failure`
- **AND** `LoginState.submit` becomes `AsyncValue.Error` with message "Email or password is incorrect"
- **AND** no session is persisted

#### Scenario: Network failure
- **WHEN** the login Cloud Function is unreachable
- **THEN** `LoginState.submit` becomes `AsyncValue.Error` with message "Connection error. Please try again."
- **AND** the form remains editable

### Requirement: Login screen offers signup and forgot-password entry points
The Login screen SHALL provide visible "Create account" and "Forgot password" actions that emit `NavEvent.NavigateToSignup` and `NavEvent.NavigateToForgotPassword` over the `LoginViewModel`'s `Channel<NavEvent>`. The Screen SHALL collect that channel and call `navController.navigate(...)` — never directly from the ViewModel.

#### Scenario: Create account entry point
- **WHEN** the user taps "Create account"
- **THEN** `LoginViewModel` emits `NavEvent.NavigateToSignup`
- **AND** the auth graph navigates to step 1 of the signup wizard

#### Scenario: Forgot password entry point
- **WHEN** the user taps "Forgot password"
- **THEN** `LoginViewModel` emits `NavEvent.NavigateToForgotPassword`
- **AND** the auth graph navigates to the forgot-password screen

### Requirement: Logout clears session and returns to Login
The app SHALL provide a logout action that emits `NavEvent.Logout` over a `Channel<NavEvent>` exposed by a small `ShellViewModel`. On `NavEvent.Logout`, the screen collector SHALL call `AuthRepository.logout()` (which clears `SessionRepository` and cancels in-flight FCM token registration) and then instruct the root NavHost to swap from `shell_graph` to `auth_graph` clearing the shell back-stack. The logout action is reachable from a placeholder Profile tab action in P1 (the full Profile screen lands in P6).

#### Scenario: Successful logout
- **WHEN** the user triggers logout from the shell
- **THEN** `ShellViewModel` emits `NavEvent.Logout` over `Channel<NavEvent>`
- **AND** `AuthRepository.logout()` clears `SessionRepository.data()` and the FCM token
- **AND** the root NavHost swaps to the auth graph with no back-stack history

### Requirement: Session persists across launches
The app SHALL restore the user's session on cold start. When a non-empty session exists in DataStore, the splash Composable SHALL route the app directly into the tab shell without showing Login.

#### Scenario: Cold start with valid session
- **WHEN** the app is launched and `SessionRepository` contains a non-expired token
- **THEN** the splash composable routes into the tab shell
- **AND** the Login screen is not displayed

#### Scenario: Cold start with no session
- **WHEN** the app is launched and `SessionRepository` is empty
- **THEN** the splash composable routes into the Login screen

#### Scenario: Expired session
- **WHEN** the app launches with a token whose `expiresAt` is in the past
- **THEN** `SessionRepository` SHALL treat the session as empty and route to Login

### Requirement: Force-update sheet blocks the app on outdated version
On every cold start, after `FirebaseRemoteConfig.fetchAndActivate()` completes, the app SHALL compare the installed `versionCode` against the Remote Config value of `android_min_supported_version`. If the installed version is below the configured minimum, the root composable SHALL overlay a non-dismissible sheet containing: a heading, the user's current version, the minimum required version, and a "Update" button that deep-links to the Kornerz Android app listing on Google Play.

#### Scenario: Outdated version blocks the app
- **WHEN** the installed `versionCode` is below `android_min_supported_version`
- **THEN** the force-update sheet is displayed over whatever screen the user would otherwise see
- **AND** the sheet cannot be dismissed by tapping outside or by back gesture

#### Scenario: Up-to-date version does not block
- **WHEN** the installed `versionCode` is greater than or equal to `android_min_supported_version`
- **THEN** the force-update sheet is not shown

#### Scenario: Remote Config fetch failure
- **WHEN** `fetchAndActivate()` throws or times out
- **THEN** the app SHALL fail open (no blocking sheet)
- **AND** log a warning