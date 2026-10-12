# android-app-shell Specification (delta)

## MODIFIED Requirements

### Requirement: Five-tab bottom navigation shell
The app SHALL render a bottom navigation bar with exactly 5 tabs in this left-to-right order: Home, Kornerz, Nooks, Loops, Klouders. Each tab SHALL be reachable by tapping its icon. The currently selected tab SHALL be visually distinguished. The bottom bar SHALL remain visible while navigating within a tab's nested graph. The Nooks tab SHALL host a nested `NavHost` with the `nooks/list` destination (introduced in P4, `nooks-tab` capability) as its start destination, `nooks/{nookId}` (introduced in P4, `nook-detail` capability) as a secondary destination reachable from any `NookCard` or `NookTile` tap, and `videocall/{sessionId}` (introduced in P9, `feature-videocall` capability) as a tertiary destination reachable from the Join button on a `LiveKornerCard` (introduced in P9, `nooks-tab` capability amendment). The shell-level tab order, selection, insets, and theming requirements are otherwise unchanged.

#### Scenario: Tab order matches iOS
- **WHEN** the app is launched on a signed-in session
- **THEN** the tabs appear in order: Home, Kornerz, Nooks, Loops, Klouders

#### Scenario: Tab selection
- **WHEN** user taps the Nooks tab
- **THEN** the Nooks tab content area displays `NooksScreen` (the populated Nooks tab introduced in P4) instead of the P0 "Coming in Phase 4" placeholder
- **AND** the Nooks tab icon shows selected state
- **AND** the previously selected tab shows unselected state

#### Scenario: Bottom bar persists within a tab
- **WHEN** user navigates from a tab root to a nested screen within that tab (e.g., from `nooks/list` to `nooks/{nookId}` inside the Nooks tab, or from `kornerz/{id}` to `nooks/{nookId}` inside the Kornerz tab)
- **THEN** the bottom navigation bar remains visible
- **AND** the originating tab remains selected

#### Scenario: Bottom bar is hidden during a video call
- **WHEN** user is on the `videocall/{sessionId}` destination inside the Nooks tab's nested `NavHost`
- **THEN** the bottom navigation bar is hidden (iOS parity — the call is a full-screen experience)
- **AND** pressing the system back button returns to `nooks/list` inside the Nooks tab
- **AND** the bottom navigation bar reappears
- **AND** the Nooks tab remains selected
