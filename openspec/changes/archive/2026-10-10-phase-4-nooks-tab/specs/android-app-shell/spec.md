## MODIFIED Requirements

### Requirement: Five-tab bottom navigation shell
The app SHALL render a bottom navigation bar with exactly 5 tabs in this left-to-right order: Home, Kornerz, Nooks, Loops, Klouders. Each tab SHALL be reachable by tapping its icon. The currently selected tab SHALL be visually distinguished. The bottom bar SHALL remain visible while navigating within a tab's nested graph. The Nooks tab SHALL host a nested `NavHost` with the `nooks/list` destination (introduced in P4, `nooks-tab` capability) as its start destination and `nooks/{nookId}` as a secondary destination reachable from any `NookCard` or `NookTile` tap (introduced in P4, `nook-detail` capability). The shell-level tab order, selection, insets, and theming requirements are otherwise unchanged from P3.

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

### Requirement: Each tab renders a populated screen or empty-state placeholder
The Home tab in `feature/shell` SHALL render `HomeScreen` (the populated Home tab introduced in P2) instead of the P0 placeholder. The Kornerz tab in `feature/shell` SHALL render `KornerzScreen` (the populated Kornerz tab introduced in P3) and SHALL continue to host its nested `NavHost` with the list and detail destinations. The Nooks tab in `feature/shell` SHALL render `NooksScreen` (the populated Nooks tab introduced in P4) instead of the P0 "Coming in Phase 4" placeholder; the Nooks tab hosts a nested `NavHost` with the list and detail destinations. Until P5 / P10 ship their full destinations, the Loops and Klouders tabs SHALL continue to render their P0 empty-state placeholders with the text "Coming in Phase N" where N is the phase number that will fill that tab.

#### Scenario: Home tab shows populated content
- **WHEN** a signed-in user opens the Home tab in P2+
- **THEN** the Home tab content area displays `HomeScreen` with five sections (Header, Loop of the day, Upcoming nooks, Your Kornerz, Recent loops) populated from real data

#### Scenario: Kornerz tab shows populated content
- **WHEN** a signed-in user opens the Kornerz tab in P3+
- **THEN** the Kornerz tab content area displays `KornerzScreen` with the list of `Korner` items populated from real data
- **AND** the bottom navigation bar remains visible across the nested list and detail destinations

#### Scenario: Nooks tab shows populated content in P4+
- **WHEN** a signed-in user opens the Nooks tab in P4+
- **THEN** the Nooks tab content area displays `NooksScreen` with the partitioned live + scheduled nooks feed populated from real data
- **AND** the bottom navigation bar remains visible across the nested list and detail destinations

#### Scenario: Empty state in non-populated tabs until their phase ships
- **WHEN** user opens any tab other than Home, Kornerz, and Nooks before that tab's phase ships
- **THEN** the tab content shows the placeholder empty state with the correct phase label
