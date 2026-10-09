## ADDED Requirements

### Requirement: Upcoming nooks renders a horizontal carousel of scheduled nooks
The Upcoming nooks section SHALL render a horizontal carousel mirroring `SwiftUI/Dashboard/HomeTab/Sections/UpcomingNooks/UpcomingNooksView.swift`. Items SHALL be `Nook` records (id, communityId, title, startDate, isLive) from Kornerz the signed-in user is a member of, filtered to those with `startDate` within the next seven days (mirrors iOS `getOpenEventsListForNextSevenDaysForCommunities`). Each tile SHALL render the nook title, the start date formatted with `kotlinx-datetime` (e.g. "Thu, Oct 9 · 6:00 PM" — locale-aware), the host community's color accent, and a "LIVE" badge when `isLive == true`.

#### Scenario: Nooks within seven days render in the carousel
- **WHEN** the upcoming nooks fetch returns 3 nooks within the next 7 days
- **THEN** the section renders 3 tiles in a horizontal scrollable row
- **AND** each tile is tappable and emits `HomeNavEvent.NavigateToEventDetail(nookId)`

#### Scenario: Live nook renders with LIVE badge
- **WHEN** a tile represents a nook whose `startDate` is in the past and `isLive == true`
- **THEN** the tile displays a "LIVE" badge in the top-right corner with red background

#### Scenario: Section hides when no upcoming nooks
- **WHEN** the fetch returns zero nooks within the next 7 days
- **THEN** the section is hidden

### Requirement: Upcoming nooks sources nooks from the user's memberships
The Upcoming nooks fetch SHALL query nooks whose `communityId` is in the set of communities the signed-in user is a member of (matches `AppHomeViewModel.getOpenEventsForNextSevenDays` which derives `communityIds = communities.compactMap { $0.communityId }`).

#### Scenario: Nooks only render for member communities
- **WHEN** the signed-in user is a member of Korner A and Korner B
- **THEN** nooks from Korner A or Korner B are included
- **AND** nooks from Korner C are excluded

## References

- [`docs/architecture.md`](../../../docs/architecture.md) — MVVM (§1), `AsyncValue<T>` (§2.2), `Channel<NavEvent>` (§2.4).
- `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/Sections/UpcomingNooks/UpcomingNooksView.swift` — iOS source-of-truth.
- `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/Sections/UpcomingNooks/UpcomingNooksViewModel.swift` — iOS fetch logic.
- `kornerz/Kornerz/HomeVC/AppHomeVC/RegisterToAppCell/AppHomeViewModel.swift` `getOpenEventsForNextSevenDays` — iOS repository call mirrored.
- `docs/screen-inventory.md` §"3.7 Upcoming nooks".