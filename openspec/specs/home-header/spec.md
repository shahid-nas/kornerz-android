# home-header Specification

## Purpose
TBD - created by archiving change phase-2-home-tab-shell. Update Purpose after archive.
## Requirements
### Requirement: Home header renders the signed-in user avatar and points
The Home header (`HomeHeaderView`) SHALL render, horizontally, the signed-in user's avatar on the left and the user's gamification points count on the right, mirroring `SwiftUI/Dashboard/HomeTab/Sections/HeaderView/HomeHeaderView.swift`. The avatar SHALL be a `KornerzDiamondImage` at 52dp with corner radius 10 (1:1 port of iOS `DiamondImageView(size: 52, radius: 10)`). The points badge SHALL render an `icon_points` 18dp icon followed by the integer points formatted as `1234` (no thousands separator — mirrors iOS `points.toString()`).

#### Scenario: Header renders with user avatar
- **WHEN** a signed-in user has a `thumbnailURL`
- **THEN** `HomeHeaderView` renders the avatar via `KornerzDiamondImage` at 52dp / radius 10 using Coil to load the URL
- **AND** the avatar is tappable

#### Scenario: Header falls back to default user mark
- **WHEN** the signed-in user has no `thumbnailURL`
- **THEN** `HomeHeaderView` renders the default user mark inside the same `KornerzDiamondImage`
- **AND** the avatar remains tappable

#### Scenario: Header renders points badge
- **WHEN** the gamification service returns `points = 42`
- **THEN** the points badge displays `42`
- **AND** uses Satoshi Bold 14sp, color `AppColor.C1B1A23` (light) / `AppColor.CFFFFFF` (dark)

### Requirement: Home header state tracks loading with shimmer
The Home header SHALL expose its loading state as `AsyncValue<HomeHeaderData>` per arch §2.2. While `AsyncValue.Loading`, the header SHALL render `HomeShimmerPlaceholder` (a 1:1 port of `HomeHeaderView.swift`'s `.redacted(reason: .placeholder)` shimmer) showing the same layout (avatar + points badge) with a shimmering overlay.

#### Scenario: Header shows shimmer while loading
- **WHEN** `HomeHeaderState.avatar` is `AsyncValue.Loading`
- **THEN** `HomeHeaderView` renders `HomeShimmerPlaceholder`
- **AND** the layout matches the success layout (avatar left, points badge right, horizontal padding 16dp)

#### Scenario: Header transitions shimmer → content
- **WHEN** `HomeHeaderState.avatar` transitions from `AsyncValue.Loading` to `AsyncValue.Success`
- **THEN** the shimmer is replaced by the real content with the same vertical bounds

### Requirement: Home header avatar tap emits onUserTap
Tapping the avatar SHALL emit `HomeEvent.AvatarTapped` to `HomeHeaderViewModel`, which forwards it as `HomeNavEvent.NavigateToProfile` (mirrors `HomeHeaderView.swift` `.onTapGesture { viewModel.onUserTap?() }`).

#### Scenario: Avatar tap triggers profile navigation
- **WHEN** user taps the header avatar
- **THEN** `HomeHeaderViewModel.onAvatarTapped()` emits `HomeNavEvent.NavigateToProfile`

