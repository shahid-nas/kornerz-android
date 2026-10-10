# nook-card Specification

## Purpose
The `NookCard` and `ScheduledNookCard` composables are the shared nook row components used by the Nooks tab list and re-used by the Korner detail's Current Nook and Nooks list sections. They are the Android port of iOS `SwiftUI/Dashboard/Nooks/Views/NookView.swift` (5.2) and `ScheduledNookView.swift` (5.3) and ship in Phase 4.

## References
- [`docs/architecture.md`](../../../docs/architecture.md) — composable conventions, state hoisting.
- [`docs/phases.md`](../../../docs/phases.md) Phase 4 — codename *Nooks*.
- [`docs/screen-inventory.md`](../../../docs/screen-inventory.md) §5 — Nooks Tab (screens 5.2, 5.3).
- `openspec/changes/phase-4-nooks-tab/proposal.md` — capability rationale.
- `openspec/changes/phase-4-nooks-tab/design.md` — shared module dependency rationale.

## Requirements

### Requirement: NookCard renders live nook rows consistently across all surfaces
The `NookCard` composable, hosted at `feature/nooks/.../components/NookCard.kt`, SHALL render a single live nook row used by the Nooks tab "Live now" section and by `feature/kornerz`'s `CurrentNookSection`. Both surfaces SHALL render identical card chrome via the same composable with a shared `NookCardData` projection (id, title, coverUrl, startDate, isLive, parentKornerName, parentKornerThemeColor). Tapping a `NookCard` SHALL emit an `onClick(nookId)` callback that the caller routes to `nooks/{nookId}`. Mirrors iOS `NookView.swift`.

#### Scenario: NookCard renders the cover, title, parent Korner name, and live indicator
- **WHEN** `NookCard` is composed with a `NookCardData(id = "n1", title = "Sunday Sessions", coverUrl = "https://...", startDate = 2026-10-12T18:00Z, isLive = true, parentKornerName = "Indie Music", parentKornerThemeColor = "#FF6B6B")`
- **THEN** the row renders the nook cover on the leading edge (16:9 aspect, 96dp width)
- **AND** the nook title appears in Satoshi Bold 16sp
- **AND** the parent Korner name appears in Satoshi Regular 13sp in the `parentKornerThemeColor`
- **AND** a red "LIVE" indicator (10dp diameter circle) appears next to the start date when `isLive = true`

#### Scenario: Same NookCard data renders identically from both surfaces
- **WHEN** the same `NookCardData` is rendered from the Nooks tab "Live now" section AND from `feature/kornerz`'s `CurrentNookSection`
- **THEN** both rendered trees are pixel-equivalent (cover size, padding, typography, color)

#### Scenario: Tap on NookCard emits a click callback
- **WHEN** the user taps a `NookCard` rendered from any caller
- **THEN** the composable's `onClick(nookId)` lambda is invoked once with the nook `id`
- **AND** the caller routes into the `nooks/{nookId}` destination

### Requirement: ScheduledNookCard renders scheduled nook rows consistently across all surfaces
The `ScheduledNookCard` composable, hosted at `feature/nooks/.../components/ScheduledNookCard.kt`, SHALL render a single scheduled nook row used by the Nooks tab "Upcoming" section and by `feature/kornerz`'s `NooksListSection`. Both surfaces SHALL render identical card chrome via the same composable with the same `NookCardData` projection. The "scheduled" variant SHALL omit the live indicator and SHALL render the `startDate` formatted as a localized date string ("MMM d, yyyy" via `kotlinx-datetime` already pinned in P0). Tapping a `ScheduledNookCard` SHALL emit an `onClick(nookId)` callback routed to `nooks/{nookId}`. Mirrors iOS `ScheduledNookView.swift`.

#### Scenario: ScheduledNookCard renders the cover, title, parent Korner name, and scheduled date
- **WHEN** `ScheduledNookCard` is composed with a `NookCardData(id = "n1", title = "Open Mic Night", coverUrl = "https://...", startDate = 2026-10-15T20:00Z, isLive = false, parentKornerName = "Indie Music", parentKornerThemeColor = "#FF6B6B")`
- **THEN** the row renders the nook cover on the leading edge (16:9 aspect, 96dp width)
- **AND** the nook title appears in Satoshi Bold 16sp
- **AND** the parent Korner name appears in Satoshi Regular 13sp in the `parentKornerThemeColor`
- **AND** the start date appears in Satoshi Regular 13sp formatted as "Oct 15, 2026"
- **AND** no live indicator is rendered

#### Scenario: Same ScheduledNookCard data renders identically from both surfaces
- **WHEN** the same `NookCardData` is rendered from the Nooks tab "Upcoming" section AND from `feature/kornerz`'s `NooksListSection`
- **THEN** both rendered trees are pixel-equivalent (cover size, padding, typography, color)

#### Scenario: Tap on ScheduledNookCard emits a click callback
- **WHEN** the user taps a `ScheduledNookCard` rendered from any caller
- **THEN** the composable's `onClick(nookId)` lambda is invoked once with the nook `id`
- **AND** the caller routes into the `nooks/{nookId}` destination

### Requirement: Card composables support Coil with shimmer placeholders
Both `NookCard` and `ScheduledNookCard` SHALL render the nook cover via Coil with a `ShimmerPlaceholder` matching the card's cover dimensions. Cover images SHALL honor the network image cache (in-memory + disk) established in P0.

#### Scenario: Cover image renders a shimmer while loading
- **WHEN** `NookCard` is composed and the cover URL has not yet been resolved by Coil
- **THEN** the cover area renders the `ShimmerPlaceholder` from `design-system`
- **AND** once Coil resolves the image, the cover transitions to the loaded bitmap without a layout shift