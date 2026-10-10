## MODIFIED Requirements

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
