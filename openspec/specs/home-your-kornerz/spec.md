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

