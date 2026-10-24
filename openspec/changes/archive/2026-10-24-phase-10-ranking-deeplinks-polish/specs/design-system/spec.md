# design-system Specification

## ADDED Requirements

### Requirement: The shared component set gains the three iOS-named state views
`core/ui/components/` SHALL expose three additional shared components introduced in P10, each mirroring its iOS counterpart's structure:

| Component | iOS counterpart | Structural contract |
|---|---|---|
| `KornerzRetryView` | `kornerz/Kornerz/Components/RetryView.swift` | centred 12-pt error message above a grey-tinted capsule button labelled `Retry again` |
| `KornerzNoDataView` | `kornerz/Kornerz/Components/NoDataView.swift` | a 75 × 75 dp illustration above a title in the Kornerz body type, with 50 dp of vertical padding |
| `KornerzLoadingMoreView` | `kornerz/Kornerz/Components/LoadingMoreView.swift` | a centred, full-width row with a progress indicator and, 16 dp beside it, the label `Loading more items` |

These components join `KornerzEmptyState`, `KornerzErrorState`, `KornerzLoadingView`, `KornerzButton`, `KornerzTextField`, `KornerzPasswordField`, `KornerzAvatar`, `KornerzDiamondImage`, and `HomeShimmerPlaceholder` in the shared set. The `app-polish` capability holds the behavioural requirements; this requirement records the components' membership in the design system.

The three new components SHALL NOT duplicate an existing shared component's job: `KornerzEmptyState` (generic empty state) and `KornerzNoDataView` (the iOS-named empty state) SHALL both remain available, and `KornerzErrorState` SHALL remain available alongside `KornerzRetryView`.

Every new component SHALL be themed from `KornerzColors` and `KornerzTypography`. No component SHALL hard-code a hex colour.

#### Scenario: The three components exist
- **WHEN** `core/ui/components/` is enumerated
- **THEN** `KornerzRetryView`, `KornerzNoDataView`, and `KornerzLoadingMoreView` are all present

#### Scenario: The components are themed, not hard-coded
- **WHEN** the three components are inspected
- **THEN** none of them declares a literal hex colour

#### Scenario: The existing components are not removed
- **WHEN** `core/ui/components/` is enumerated
- **THEN** `KornerzEmptyState`, `KornerzErrorState`, and `KornerzLoadingView` are all still present
