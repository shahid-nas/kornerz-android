# android-app-shell Specification

## Purpose
TBD - created by archiving change p0-android-foundation. Update Purpose after archive.
## Requirements
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

### Requirement: Five-tab bottom navigation shell
The app SHALL render a bottom navigation bar with exactly 5 tabs in this left-to-right order: Home, Kornerz, Nooks, Loops, Klouders. Each tab SHALL be reachable by tapping its icon. The currently selected tab SHALL be visually distinguished. The bottom bar SHALL remain visible while navigating within a tab's nested graph.

#### Scenario: Tab order matches iOS
- **WHEN** the shell renders for the first time
- **THEN** the tabs appear in order: Home, Kornerz, Nooks, Loops, Klouders

#### Scenario: Tab selection
- **WHEN** user taps the Nooks tab
- **THEN** the Nooks tab content area displays
- **AND** the Nooks tab icon shows selected state
- **AND** the previously selected tab shows unselected state

#### Scenario: Bottom bar persists within a tab
- **WHEN** user navigates from a tab root to a nested screen within that tab
- **THEN** the bottom navigation bar remains visible

### Requirement: Each tab renders an empty-state placeholder
Until P2+, each of the 5 tabs SHALL render an empty-state placeholder screen with: the tab's title, a centered illustration placeholder, and the text "Coming in Phase N" where N is the phase number that will fill that tab. Tabs MUST NOT show real content in P0.

#### Scenario: Empty state in P0
- **WHEN** user opens any tab
- **THEN** the tab content shows the placeholder empty state
- **AND** no real data, network calls, or repositories are invoked

### Requirement: Shell theming follows system dark/light mode
The app SHALL observe the system dark/light mode setting and apply the matching Material 3 ColorScheme. The user SHALL NOT have a manual theme toggle in P0.

#### Scenario: System in light mode
- **WHEN** device system theme is set to light
- **THEN** the app renders with the Kornerz light color scheme

#### Scenario: System in dark mode
- **WHEN** device system theme is set to dark
- **THEN** the app renders with the Kornerz dark color scheme

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

### Requirement: Shell respects system insets
The 5-tab shell SHALL respect system status bar, navigation bar, and display cutout insets. Content SHALL NOT be obscured by system bars or notches in any orientation.

#### Scenario: Portrait, notch present
- **WHEN** user holds the device in portrait with a notch
- **THEN** the bottom bar sits above the gesture navigation indicator
- **AND** the tab content does not overlap the notch

#### Scenario: Landscape
- **WHEN** user rotates to landscape
- **THEN** the shell relayouts correctly
- **AND** no content is clipped
