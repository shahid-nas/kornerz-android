## MODIFIED Requirements

### Requirement: Each tab renders an empty-state placeholder
The Home tab in `feature/shell` SHALL render `HomeScreen` (the populated Home tab introduced in P2) instead of the P0 placeholder. The Kornerz tab in `feature/shell` SHALL render `KornerzScreen` (the populated Kornerz tab introduced in P3) instead of the P0 "Coming in Phase 3" placeholder; the Kornerz tab hosts a nested `NavHost` with the list and detail destinations (see `kornerz-tab` and `korner-detail`). Until P4 / P5 / P10 ship their full destinations, the Nooks, Loops, and Klouders tabs SHALL continue to render their P0 empty-state placeholders with the text "Coming in Phase N" where N is the phase number that will fill that tab.

#### Scenario: Home tab shows populated content
- **WHEN** a signed-in user opens the Home tab in P2+
- **THEN** the Home tab content area displays `HomeScreen` with five sections (Header, Loop of the day, Upcoming nooks, Your Kornerz, Recent loops) populated from real data
- **AND** no "Coming in Phase 2" text is shown

#### Scenario: Kornerz tab shows populated content
- **WHEN** a signed-in user opens the Kornerz tab in P3+
- **THEN** the Kornerz tab content area displays `KornerzScreen` with the list of `Korner` items populated from real data
- **AND** the bottom navigation bar remains visible across the nested list and detail destinations
- **AND** no "Coming in Phase 3" text is shown

#### Scenario: Empty state in non-populated tabs until their phase ships
- **WHEN** user opens any tab other than Home and Kornerz before that tab's phase ships
- **THEN** the tab content shows the placeholder empty state with the correct phase label
- **AND** no real data, network calls, or repositories are invoked