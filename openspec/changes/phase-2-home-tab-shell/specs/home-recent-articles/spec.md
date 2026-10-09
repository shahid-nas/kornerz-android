## ADDED Requirements

### Requirement: Recent articles renders a merged feed of posts, videos, and forums
The Recent articles section SHALL render a horizontal carousel mirroring `SwiftUI/Dashboard/HomeTab/Sections/RecentArticles/RecentArticlesView.swift`. Items in the carousel SHALL be a `RecentItem` sum-type with three cases: `.post(Post, User)`, `.video(Video, User)`, `.forum(Forum, User)`. The section SHALL fetch the most recent posts and most recent videos (the iOS reference merges those two; forums are deferred to a follow-up since the Forum model is not yet defined for Android — see `phases.md` "Out of scope" for forums) and merge them sorted by date descending. Each tile SHALL render: the item's thumbnail (or default community mark), the author avatar (`KornerzAvatar` 24dp), the author display name, the title in Satoshi Bold 14sp, and the content preview in Satoshi Regular 12sp.

#### Scenario: Mixed posts and videos render in date order
- **WHEN** the section has 3 posts and 5 videos
- **THEN** all 8 items render in a horizontal carousel
- **AND** items are ordered by their `createdAt` timestamp descending
- **AND** each tile shows the appropriate thumbnail, avatar, title, and content preview

#### Scenario: Tap on a recent item navigates to detail
- **WHEN** user taps a `.post(Post, User)` tile
- **THEN** the section emits `HomeNavEvent.NavigateToRecentItemDetail(itemId = postId)`

#### Scenario: Section hides when feed is empty
- **WHEN** both the posts fetch and the videos fetch return zero items
- **THEN** the section is hidden
- **AND** no empty-state placeholder is shown

### Requirement: Recent articles uses the iOS RandomColorService palette
Each recent articles tile SHALL be backed by one of the five `KornerzCardPalette` swatches (`CE27D5A`, `CAD598D`, `C86BFA9`, `C68B6CC`, `CEBAA4A`) — 1:1 port of iOS `RandomColorService.color(for: index)`. Tiles SHALL rotate through the palette cyclically in the order they render. The swatch is used as the tile's background or accent color, matching the iOS visual.

#### Scenario: Tile background rotates through palette
- **WHEN** a recent articles carousel renders items
- **THEN** tile 0 uses the first swatch, tile 1 uses the second, …, tile 5 wraps back to the first
- **AND** each tile's color matches the corresponding iOS tile

### Requirement: Recent articles renders shimmer placeholders while loading
While `recentArticles` is `AsyncValue.Loading`, the section SHALL render 4 placeholder tiles in the cyclic palette colors (mirrors `RecentArticlesViewModel.swift`'s `ForEach(0..<4) { KornerzSquareView(community: .shimmer, …) }`).

#### Scenario: Loading state shows 4 cyclic placeholders
- **WHEN** `recentArticles` is `AsyncValue.Loading`
- **THEN** the section renders exactly 4 shimmering tiles
- **AND** each tile uses the next cyclic palette color

### Requirement: Recent articles handles partial failure
The recent articles fetch combines two repositories. The section SHALL transition to `AsyncValue.Success` if at least one of the two repositories returns a non-empty list. The section SHALL transition to `AsyncValue.Error` only if both repositories return an error; otherwise the merged list contains whatever was returned.

#### Scenario: Posts succeed, videos fail
- **WHEN** posts repository returns 3 items and videos repository returns an error
- **THEN** the section renders 3 tiles
- **AND** no error UI is shown

## References

- [`docs/architecture.md`](../../../docs/architecture.md) — MVVM (§1), `AsyncValue<T>` (§2.2), `Channel<NavEvent>` (§2.4).
- `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/Sections/RecentArticles/RecentArticlesViewModel.swift` — iOS source-of-truth for the merged feed.
- `kornerz/Kornerz/SwiftUI/Dashboard/HomeTab/Sections/RecentArticles/RecentArticlesView.swift` — iOS source-of-truth for the carousel layout.
- `docs/screen-inventory.md` §"3.5 Recent articles".