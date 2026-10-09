## ADDED Requirements

### Requirement: KornerzDiamondImage shared component
The `core/ui/components/` module SHALL provide a `KornerzDiamondImage` Composable that renders an image clipped into a 45°-rotated rounded square (1:1 port of iOS `DiamondImageView`). The Composable SHALL accept `imageUrl: String?`, `fallbackAsset: String?`, `size: Dp`, `cornerRadius: Dp`, `modifier: Modifier = Modifier` (last per arch §4). When `imageUrl` is null or fails to load, the Composable SHALL fall back to the supplied asset (or a default "user" mark).

#### Scenario: Diamond renders URL image
- **WHEN** `imageUrl` is non-null and Coil loads it successfully
- **THEN** the image is rendered clipped into the diamond shape at the requested size

#### Scenario: Diamond falls back to asset
- **WHEN** `imageUrl` is null or Coil fails
- **THEN** the fallback asset is rendered inside the same diamond shape

### Requirement: HomeShimmerPlaceholder shared component
The `core/ui/components/` module SHALL provide a `HomeShimmerPlaceholder` Composable that wraps any child in a shimmer effect (mirrors iOS `.redacted(reason: .placeholder)`). The Composable SHALL accept `modifier: Modifier = Modifier` (last) and a content lambda.

#### Scenario: Shimmer placeholder wraps content
- **WHEN** a section renders `HomeShimmerPlaceholder { ShimmerContent() }`
- **THEN** the shimmer overlay animates over `ShimmerContent()`

## References

- [`docs/architecture.md`](../../../docs/architecture.md) — shared components (§5).
- `kornerz/Kornerz/Components/DiamondImageView.swift` — iOS source-of-truth for `KornerzDiamondImage`.
- `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/Sections/HeaderView/HomeHeaderView.swift` `.redacted(reason: .placeholder)` — iOS source-of-truth for `HomeShimmerPlaceholder`.