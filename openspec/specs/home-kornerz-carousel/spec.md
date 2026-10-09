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
Each tile SHALL be a 135dp square (matches iOS `KornerzSquareView(size: 135)`) with 16dp outer corner radius, 5dp inner padding, a 13dp corner-radius triangle clipped from the top-right corner (mirrors `TriangleClippedShape.swift`), the cover image filling the tile, and the community name overlaid in white Satoshi Bold 12sp at the bottom-leading edge. The accent strip uses `community.themeColor` (mapped to the iOS palette when missing).

#### Scenario: Tile dimensions match iOS
- **WHEN** a tile is rendered
- **THEN** its size is 135dp × 135dp
- **AND** the outer corner radius is 16dp
- **AND** the top-right triangle clip uses a 13dp radius

