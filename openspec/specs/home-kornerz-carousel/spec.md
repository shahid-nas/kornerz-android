# home-kornerz-carousel Specification

## Purpose
TBD - created by archiving change phase-2-home-tab-shell. Update Purpose after archive.
## Requirements
### Requirement: KornerzCarousel is a reusable shared component
`KornerzCarousel` SHALL be a `core/ui/components/` Composable that renders a list of `Community` items in a horizontal `LazyRow` with the iOS `CommunitiesCarouselView` look — square tiles with a corner-clipped triangle (1:1 port of iOS `TriangleClippedShape`), the community cover image, the community name, and the community's theme color. The Composable SHALL accept `items: List<Community>`, `onItemClick: (Community) -> Unit`, and a `Modifier` last per arch §4 conventions.

#### Scenario: Empty items list renders nothing
- **WHEN** `items` is empty
- **THEN** the Composable renders an empty `Box` (no placeholder, no spinner)

#### Scenario: Items render in order
- **WHEN** `items` contains 5 communities
- **THEN** the LazyRow renders 5 tiles in the supplied order
- **AND** each tile is tappable and invokes `onItemClick(community)`

### Requirement: KornerzCarousel matches iOS visual design
Each tile SHALL be a 145dp square card — iOS `KornerzSquareView` renders a 135dp cover with 5dp of padding around it — filled with the community's accent colour and rounded with a 16dp corner radius. The cover SHALL be inset 5dp inside that card, clipped by the top-right triangle at a 13dp corner radius, and drawn over a neutral grey placeholder while it loads. The community name SHALL be overlaid in white Satoshi Bold 12sp at the bottom-leading edge with an 8dp inset and a width of two thirds of the cover. The accent strip uses `community.themeColor` (mapped to the iOS palette when missing).

#### Scenario: Tile dimensions match iOS
- **WHEN** a tile is rendered
- **THEN** its size is 145dp × 145dp
- **AND** the cover inside it is 135dp × 135dp with a 5dp inset
- **AND** the outer corner radius is 16dp
- **AND** the top-right triangle clip uses a 13dp radius

### Requirement: KornerzCarousel tile tap resolves to the Kornerz detail destination
`KornerzCarousel`'s `onItemClick(community: Community)` callback SHALL continue to be invoked when a tile is tapped (this contract is unchanged from P2). Every caller of `KornerzCarousel` (currently Home's "Your Kornerz" and the shared Home carousel row; in P3+ also the Kornerz tab list and Korner detail's Members row may compose it) SHALL route the `onItemClick` callback to the `kornerz/{community.id}` destination inside the Kornerz tab's nested `NavHost`. The shell SHALL no longer emit a `Coming in Phase 3` snackbar for `KornerzCarousel` tile taps in P3+.

#### Scenario: Tile tap navigates to Korner detail
- **WHEN** any caller of `KornerzCarousel` composes it with `onItemClick = { community -> navigateToKornerDetail(community.id) }` and a user taps a tile
- **THEN** the navigation call resolves to the `kornerz/{community.id}` destination inside the Kornerz tab's nested `NavHost`
- **AND** no snackbar is shown

#### Scenario: Caller is responsible for destination wiring
- **WHEN** a future caller composes `KornerzCarousel` without providing a navigation callback
- **THEN** the composable renders normally and tile taps invoke the (possibly no-op) `onItemClick` lambda
- **AND** the composable does not own navigation itself

