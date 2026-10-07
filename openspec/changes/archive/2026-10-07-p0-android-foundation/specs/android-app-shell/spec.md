## ADDED Requirements

### Requirement: App launches into splash screen
The app SHALL display a branded splash screen as the first screen when launched from a cold start. The splash SHALL render the Kornerz brand mark on a brand background color. The splash SHALL transition to the 5-tab shell within 1 second.

#### Scenario: Cold launch
- **WHEN** user launches the app from the device home screen with no prior process running
- **THEN** the splash screen displays the Kornerz brand
- **AND** the splash transitions to the 5-tab shell within 1 second

#### Scenario: Warm launch
- **WHEN** user resumes the app from the device recents
- **THEN** the splash is not shown
- **AND** the app resumes directly to the last selected tab

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

### Requirement: Anonymous Firebase user is provisioned at launch
The app SHALL call `FirebaseAuth.signInAnonymously()` during `Application.onCreate()` (before any UI renders). The anonymous user ID SHALL be available to P1+ for linking pre-login activity.

#### Scenario: First launch
- **WHEN** the app launches for the first time on a fresh install
- **THEN** `FirebaseAuth.signInAnonymously()` completes successfully
- **AND** the resulting anonymous user ID is cached for P1+ to use

#### Scenario: Subsequent launches
- **WHEN** the app launches with an existing anonymous user
- **THEN** the existing anonymous session is used
- **AND** no new anonymous account is created

### Requirement: No business logic in P0 shell
The P0 shell SHALL NOT contain user authentication, content fetching, push notification handling UI, or deep-link routing. The shell's only responsibilities are: render splash, render the tab bar, route between tabs, render empty-state placeholders, and apply theming.

#### Scenario: No repositories injected
- **WHEN** the shell is composed at runtime
- **THEN** no repository or use-case types are injected into shell composables

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

## References

- [Kornerz Android Architecture, Patterns & Conventions](../../../../docs/architecture.md) — canonical patterns: UDF (State + Event), `AsyncValue<T>`, `Channel<NavEvent>` for navigation, Hilt scopes, file/folder conventions.
- [Kornerz Android — Project Brief](../../../../docs/project-brief.md) — locked decisions, scope, NFRs.
- [Kornerz Android — Phased Delivery Plan](../../../../docs/phases.md) — phase boundaries; this spec is P0.