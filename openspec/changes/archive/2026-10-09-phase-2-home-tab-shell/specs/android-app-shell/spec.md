## MODIFIED Requirements

### Requirement: Each tab renders an empty-state placeholder
The Home tab in `feature/shell` SHALL render `HomeScreen` (the populated Home tab introduced in P2) instead of the P0 placeholder. Until P3 / P4 / P5 / P10 ship their full destinations, the Kornerz, Nooks, Loops, and Klouders tabs SHALL continue to render their P0 empty-state placeholders with the text "Coming in Phase N" where N is the phase number that will fill that tab.

#### Scenario: Home tab shows populated content
- **WHEN** a signed-in user opens the Home tab in P2+
- **THEN** the Home tab content area displays `HomeScreen` with five sections (Header, Loop of the day, Upcoming nooks, Your Kornerz, Recent loops) populated from real data
- **AND** no "Coming in Phase 2" text is shown

#### Scenario: Empty state in non-Home tabs until their phase ships
- **WHEN** user opens any tab other than Home before that tab's phase ships
- **THEN** the tab content shows the placeholder empty state with the correct phase label
- **AND** no real data, network calls, or repositories are invoked

## References

- [`docs/architecture.md`](../../../docs/architecture.md) — module boundaries (§2.5).
- `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/HomeTabView.swift` — iOS source-of-truth for the populated Home tab.
- `docs/phases.md` §"Phase 2 — Home Tab & Tab Shell".
- `docs/screen-inventory.md` §"3.1 Home tab root".