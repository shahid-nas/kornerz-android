# home-your-kornerz Specification

## Purpose
TBD - created by archiving change phase-2-home-tab-shell. Update Purpose after archive.
## Requirements
### Requirement: Your Kornerz renders the signed-in user's memberships
The Your Kornerz section SHALL render a horizontal carousel of Kornerz the signed-in user is a member of, mirroring `SwiftUI/Dashboard/HomeTab/Sections/YourKornerz/YourKornerzSectionView.swift`. Items SHALL be `Community` records (id, name, themeColor, coverUrl, isMember). Each tile SHALL render the community's cover image (Coil-loaded), the community name in Satoshi Bold 14sp overlaying the cover, and the community's theme color as the tile's accent strip.

#### Scenario: Carousel renders user memberships
- **WHEN** the signed-in user is a member of 4 Kornerz
- **THEN** the section renders 4 tiles in a horizontal scrollable row
- **AND** each tile is tappable and emits `HomeNavEvent.NavigateToKornerDetail(kornerId)`

#### Scenario: Section hides when user has no memberships
- **WHEN** the signed-in user is not a member of any Korner
- **THEN** the section is hidden
- **AND** the home feed composer skips it entirely

### Requirement: Your Kornerz reuses the shared Kornerz carousel component
The Your Kornerz section SHALL reuse `KornerzCarousel` (the `home-kornerz-carousel` shared component) so the carousel pattern is identical to other Kornerz carousels across the app.

#### Scenario: Component is shared
- **WHEN** another tab later needs the same carousel (e.g. P3 Kornerz tab)
- **THEN** that tab can compose `KornerzCarousel(items = communities, onClick = …)` with the same signature

### Requirement: Home Your Kornerz tile tap resolves to the Kornerz detail destination
The Home tab's `HomeViewModel` SHALL emit `HomeNavEvent.NavigateToKornerDetail(kornerId)` when a tile in the "Your Kornerz" carousel is tapped (this emission is unchanged from P2). The shell's nav-event handler SHALL resolve `HomeNavEvent.NavigateToKornerDetail(kornerId)` by switching to the Kornerz tab and pushing the `kornerz/{kornerId}` destination inside the Kornerz tab's nested `NavHost`. The shell SHALL no longer emit a `Coming in Phase 3` snackbar for this event.

#### Scenario: Tap a Your Kornerz tile navigates to Korner detail
- **WHEN** the user taps a tile in the Home tab's "Your Kornerz" carousel with `kornerId = "abc"`
- **THEN** the root NavHost switches to the Kornerz tab
- **AND** the Kornerz tab's nested NavHost pushes the `kornerz/abc` destination
- **AND** the bottom navigation bar remains visible
- **AND** no snackbar is shown

#### Scenario: No more Phase 3 placeholder snackbar from Home
- **WHEN** any `HomeNavEvent.NavigateToKornerDetail(kornerId)` event is observed by the shell
- **THEN** the shell routes to the real `kornerz/{kornerId}` destination
- **AND** the user never sees the `Coming in Phase 3` snackbar from the Home carousel in P3+

