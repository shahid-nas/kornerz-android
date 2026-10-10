# korner-detail Specification

## Purpose
TBD - created by archiving change phase-3-kornerz-tab. Update Purpose after archive.
## Requirements
### Requirement: Korner detail screen is reachable from the Kornerz tab and from Home
The Korner detail destination `kornerz/{kornerId}` SHALL be reachable from (a) tapping a row in the Kornerz tab list, (b) tapping a tile in the Home tab's "Your Kornerz" carousel, and (c) tapping a tile in the shared `KornerzCarousel` composable. The destination SHALL receive `kornerId` as a nav-arg via `SavedStateHandle`. The destination SHALL be hosted inside the Kornerz tab's nested `NavHost` and SHALL preserve the bottom navigation bar visibility rule from `android-app-shell`.

#### Scenario: Open from Kornerz tab
- **WHEN** the user taps a row in the Kornerz tab list with `kornerId = "abc"`
- **THEN** the Korner detail screen renders for `kornerId = "abc"`
- **AND** the bottom navigation bar remains visible
- **AND** the Kornerz tab remains selected

#### Scenario: Open from Home "Your Kornerz" carousel
- **WHEN** the user taps a tile in the Home tab's "Your Kornerz" carousel with `kornerId = "abc"`
- **THEN** the root NavHost navigates to the Kornerz tab and pushes the `kornerz/{kornerId}` destination for `kornerId = "abc"`
- **AND** the bottom navigation bar remains visible
- **AND** the Kornerz tab is now selected (matching iOS tab-switch behavior in `AppTabController.swift`)

#### Scenario: System back returns to caller
- **WHEN** the user is on the Korner detail screen and presses the system back button
- **THEN** the nested NavHost pops to the `kornerz/list` destination if entered from the Kornerz tab
- **OR** the root NavHost returns to the Home tab if entered from the Home tab

### Requirement: Korner detail header renders cover, title, and CTA
The Korner detail screen SHALL render a `KornerDetailHeader` composable at the top, mirroring `SwiftUI/Dashboard/Korner/KornerDetailView.swift` `header` section. The header SHALL display the Korner cover image as a 16:9 banner (Coil-loaded, parallax-scroll optional this phase) with the Korner name overlaid in Satoshi Bold 24sp white at the bottom-leading edge, and the Korner's theme color as a 4dp bottom accent strip. Below the banner, the header SHALL render a "Join" CTA button (`FilledTonalButton`, full width) when the user is not a member of the Korner, or a "Joined" disabled state button when they are. Tapping "Join" SHALL emit a `Coming soon` snackbar (join flow is deferred per `proposal.md` Non-Goals).

#### Scenario: Non-member sees Join CTA
- **WHEN** the Korner detail screen renders for a Korner the signed-in user is not a member of
- **THEN** the header shows an active "Join" `FilledTonalButton`
- **AND** tapping "Join" emits a `Coming soon` snackbar
- **AND** no network call is made

#### Scenario: Member sees Joined CTA
- **WHEN** the Korner detail screen renders for a Korner the signed-in user is a member of
- **THEN** the header shows a disabled "Joined" `FilledTonalButton`
- **AND** tapping "Joined" does nothing

### Requirement: Korner detail About section renders the Korner description
The Korner detail screen SHALL render an `AboutSection` composable below the header, mirroring `SwiftUI/Dashboard/Korner/About/AboutView.swift`. The section SHALL display the title "About" in Satoshi Bold 18sp and the Korner's `description` text body in Satoshi Regular 14sp with a max of 6 lines that expand to full text on tap. The section SHALL be hidden if the Korner's `description` is null or empty.

#### Scenario: About renders with description
- **WHEN** the Korner detail screen renders for a Korner with a non-empty `description`
- **THEN** the section title "About" is shown
- **AND** the first 6 lines of the description are shown
- **AND** tapping the body text expands to show the full description

#### Scenario: About is hidden when description is empty
- **WHEN** the Korner detail screen renders for a Korner with a null or empty `description`
- **THEN** the About section is omitted from the layout entirely

### Requirement: Korner detail Current Nook section renders the live nook
The Korner detail screen SHALL render a `CurrentNookSection` composable when the Korner has a nook with `isLive = true`, mirroring `SwiftUI/Dashboard/Korner/CurrentNook/CurrentNookView.swift`. The section SHALL display the title "Current Nook" in Satoshi Bold 18sp and a single `NookCard` from `feature/nooks/.../components/NookCard.kt` for the live nook. The card is composed with the same `NookCardData` projection used by the Nooks tab "Live now" section, so both surfaces render pixel-equivalent chrome. Tapping the card SHALL emit `KornerDetailNavEvent.NavigateToNookDetail(nookId)` which resolves to the new `nooks/{nookId}` destination via the Kornerz tab's nested `NavHost` (nook detail destination ships in P4, see `nook-detail` capability). The section SHALL be hidden if no nook has `isLive = true`.

#### Scenario: Current Nook renders for live nook
- **WHEN** the Korner detail screen renders for a Korner with a live nook
- **THEN** the section title "Current Nook" is shown above a single `NookCard`
- **AND** the card is tappable and emits `KornerDetailNavEvent.NavigateToNookDetail(nookId)` which routes into the `nooks/{nookId}` destination inside the Kornerz tab's nested NavHost
- **AND** the bottom navigation bar remains visible and the Kornerz tab remains selected after the navigation

#### Scenario: Current Nook is hidden when no live nook exists
- **WHEN** the Korner detail screen renders for a Korner with no `isLive = true` nook
- **THEN** the Current Nook section is omitted from the layout entirely

### Requirement: Korner detail Nooks list renders scheduled nooks as tiles
The Korner detail screen SHALL render a `NooksListSection` composable, mirroring `SwiftUI/Dashboard/Korner/Nooks/NooksListView.swift`. The section SHALL display the title "Nooks" in Satoshi Bold 18sp followed by a vertical list of `ScheduledNookCard` rows from `feature/nooks/.../components/ScheduledNookCard.kt` for every scheduled (non-live) nook, ordered by `startDate` ascending. Each card SHALL render the nook's cover image (Coil-loaded), the nook title, the parent Korner name and theme color, and the nook's `startDate` formatted as a localized date string ("MMM d, yyyy" via `kotlinx-datetime`). The card is composed with the same `NookCardData` projection used by the Nooks tab "Upcoming" section, so both surfaces render pixel-equivalent chrome. Tapping a card SHALL emit `KornerDetailNavEvent.NavigateToNookDetail(nookId)` which resolves to the new `nooks/{nookId}` destination via the Kornerz tab's nested `NavHost`. The section SHALL be hidden if no scheduled nooks exist.

#### Scenario: Nooks list renders 3 scheduled nooks
- **WHEN** the Korner detail screen renders for a Korner with 3 scheduled nooks
- **THEN** the section title "Nooks" is shown above 3 `ScheduledNookCard` rows
- **AND** rows appear in `startDate` ascending order
- **AND** each card is tappable and emits `KornerDetailNavEvent.NavigateToNookDetail(nookId)` which routes into `nooks/{nookId}`

#### Scenario: Nooks list is hidden when empty
- **WHEN** the Korner detail screen renders for a Korner with no scheduled nooks
- **THEN** the Nooks list section is omitted from the layout entirely

### Requirement: Korner detail Members section renders the member list
The Korner detail screen SHALL render a `MembersSection` composable, mirroring `SwiftUI/Dashboard/Korner/Members/MembersView.swift`. The section SHALL display the title "Members" in Satoshi Bold 18sp followed by a horizontally-scrolling `LazyRow` of circular avatar tiles (40dp diameter, Coil-loaded, `KornerzDiamondImage` not used — members use a plain `CircleShape`). Each tile SHALL show the member's avatar and a name label below in Satoshi Regular 11sp, capped to the first 50 members (matches iOS `MembersView` cap). Tapping a tile SHALL emit `KornerDetailNavEvent.NavigateToMemberProfile(userId)` which resolves to a `Coming in Phase 6` snackbar (member profile is deferred per `proposal.md` Non-Goals). The section SHALL be hidden if the member list is empty.

#### Scenario: Members renders up to 50 avatars
- **WHEN** the Korner detail screen renders for a Korner with 60 members
- **THEN** the section title "Members" is shown above a horizontal `LazyRow` of 50 circular avatar tiles
- **AND** each tile is tappable and emits the navigation event

#### Scenario: Members is hidden when empty
- **WHEN** the Korner detail screen renders for a Korner with no members
- **THEN** the Members section is omitted from the layout entirely

### Requirement: Korner detail view model exposes AsyncValue state and navigation events
`KornerDetailViewModel` SHALL be a `@HiltViewModel` constructed with a `SavedStateHandle` that exposes `kornerId: String`. It SHALL expose `state: StateFlow<KornerDetailState>` where `KornerDetailState` contains an `AsyncValue<KornerDetail>` aggregate and a `navEvents: Channel<KornerDetailNavEvent>` for navigation. `KornerDetailNavEvent.NavigateToNookDetail(nookId)` and `KornerDetailNavEvent.NavigateToMemberProfile(userId)` SHALL be emitted on tile taps. The view model SHALL initiate `KornerzRepository.fetchKornerDetail(kornerId)` on construction.

#### Scenario: Initial composition triggers fetch
- **WHEN** the Korner detail composable first enters the composition with `kornerId = "abc"`
- **THEN** `KornerDetailViewModel` initiates `fetchKornerDetail("abc")`
- **AND** `state` transitions from `AsyncValue.Uninitialized` to `AsyncValue.Loading`

#### Scenario: Successful fetch populates state
- **WHEN** `fetchKornerDetail("abc")` resolves with a non-null `KornerDetail`
- **THEN** `state` transitions to `AsyncValue.Success(kornerDetail)`
- **AND** all five sections (Header, About, Current Nook, Nooks list, Members) reflect the data

#### Scenario: Failed fetch renders retry
- **WHEN** `fetchKornerDetail("abc")` resolves to an error
- **THEN** `state` transitions to `AsyncValue.Error`
- **AND** the screen renders a retry view with a "Try again" button that invokes `KornerDetailViewModel.refresh()`

