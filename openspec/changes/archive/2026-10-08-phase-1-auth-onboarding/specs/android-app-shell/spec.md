## References

- [`docs/architecture.md`](../../../../docs/architecture.md) — MVVM + Repository + UDF, `AsyncValue<T>`, `Channel<NavEvent>`, Hilt scopes (`@Singleton` for repositories), Navigation-Compose nested graphs, module-boundary rules §2.5, clean-code rules §7.
- [`docs/phases.md` §Phase 1](../../../../docs/phases.md) — source of truth for P1 scope and exit checklist.
- [`openspec/specs/design-system/spec.md`](../../../specs/design-system/spec.md) — branded splash colors.

## MODIFIED Requirements

### Requirement: App launches into splash screen
The app SHALL display a branded splash screen as the first screen when launched from a cold start. The splash SHALL render the Kornerz brand mark on a brand background color. After `SessionRepository.data()` emits a non-`Loading` value AND `FirebaseRemoteConfig.fetchAndActivate()` completes, the splash SHALL route to the Login screen if the session is `Anonymous` or to the 5-tab shell if the session is `Authenticated`. The splash SHALL be dismissed within 2 seconds of session state being available.

#### Scenario: Cold launch, signed out
- **WHEN** user launches the app from the device home screen with no prior process running and no persisted session
- **THEN** the splash screen displays the Kornerz brand
- **AND** the splash routes to the Login screen once session state resolves to `Anonymous`

#### Scenario: Cold launch, signed in
- **WHEN** user launches the app from the device home screen with a valid persisted session
- **THEN** the splash screen displays the Kornerz brand
- **AND** the splash routes directly to the Home tab of the 5-tab shell

#### Scenario: Warm launch
- **WHEN** user resumes the app from the device recents
- **THEN** the splash is not shown
- **AND** the app resumes directly to the last selected tab

### Requirement: Auth and shell graphs are mutually exclusive
The app's root `NavHost` SHALL host exactly one of two child graphs at a time, selected by `SessionRepository.data()`: the auth graph (Login, Signup wizard, Forgot Password, OTP Confirm, Change Password) when session is `Anonymous`, and the shell graph (5-tab bottom-nav) when session is `Authenticated`. Navigating from auth to shell SHALL clear the auth back-stack. Navigating from shell to auth (logout, session expiry) SHALL clear the shell back-stack.

#### Scenario: Sign-in transitions to shell
- **WHEN** the user completes login or signup
- **THEN** the root NavHost switches from the auth graph to the shell graph
- **AND** pressing the system back button from the Home tab exits the app instead of returning to Login

#### Scenario: Logout transitions to auth
- **WHEN** the user logs out from the shell
- **THEN** the root NavHost switches from the shell graph to the auth graph
- **AND** the Login screen is shown with an empty back-stack

### Requirement: No anonymous Firebase user at launch in P1
The app SHALL NOT call `FirebaseAuth.signInAnonymously()` during `Application.onCreate()`. Authentication is handled exclusively by the custom-auth Cloud Function client introduced in P1; no implicit Firebase Auth state is created at app launch.

#### Scenario: Fresh install does not provision an anonymous user
- **WHEN** the app is launched on a fresh install
- **THEN** no `FirebaseAuth.signInAnonymously()` call is made
- **AND** the user is unauthenticated in Firebase Auth until they complete signup or login

### Requirement: Shell composables may inject repositories
The shell SHALL be allowed to inject repositories (specifically `SessionRepository` and the FCM registrar) because P1 introduces session-gated routing. Shell composables SHALL NOT yet inject content repositories (Home, Loops, Kornerz, Nooks, Profile) — those are introduced by their respective phases.

#### Scenario: Session-aware shell
- **WHEN** the shell composable is composed at runtime
- **THEN** `SessionRepository` MAY be injected
- **AND** content repositories (HomeRepository, LoopRepository, etc.) SHALL NOT be injected