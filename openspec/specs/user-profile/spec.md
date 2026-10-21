# user-profile Specification

## Purpose
TBD - created by archiving change phase-6-profile-and-notifications. Update Purpose after archive.
## Requirements
### Requirement: Profile tab renders user header and menu list
The Profile bottom-nav tab SHALL render a `UserInfoHeader` (gradient avatar + name + nickName + two pill count rows: "Calls" and "Communities") above a `LazyColumn` of `ProfileMenuRow` entries. The avatar SHALL load from `User.thumbnailURL` via Coil with `R.drawable.ic_user_placeholder` fallback (mirrors iOS `ProfileUserInfoCell.awakeFromNib` line 72–74). Tapping the avatar SHALL open `KornerzImagePicker` and on result call `UserProfileRepository.uploadProfileImage`. The "Communities" count SHALL be `AdminCommunitiesRepository.fetchUserCommunitiesCount(userCode)`.

#### Scenario: Header renders user data
- **WHEN** `ProfileViewModel` exposes `ProfileState.Success(user, ownedCommunityCount)`
- **THEN** the header displays `user.name`, `user.nickName`, the avatar from `user.thumbnailURL`, the "Communities" pill shows `ownedCommunityCount`, and the "Calls" pill shows `0`

#### Scenario: Owned communities row appears only when user is a creator
- **WHEN** `ownedCommunityCount > 0`
- **THEN** a "My Communities" menu row is inserted before "Privacy Policy" (mirrors iOS `isCreatorOfCommunity.didSet` insertion at `count - 2`)

#### Scenario: Avatar edit uploads new image and refreshes header
- **WHEN** user taps avatar, picks an image, and `uploadProfileImage` returns `Result.Success(updatedUser)`
- **THEN** the header avatar updates without leaving the screen and DataStore-backed `User` is updated

### Requirement: Profile menu navigation dispatches NavEvents
The Profile screen SHALL emit `ProfileNavEvent` on each menu row tap: `OpenNotifications`, `OpenEditBasicInfo`, `OpenPrivacyPolicy`, `OpenMyCommunities` (when visible), `Logout` (when P10 wires it).

#### Scenario: Tap Notifications menu row
- **WHEN** user taps the Notifications menu row
- **THEN** shell navigates to `notifications/list`

#### Scenario: Tap My Data menu row
- **WHEN** user taps the My Data menu row
- **THEN** shell navigates to `profile/edit-basic-info`

#### Scenario: Tap Privacy Policy menu row
- **WHEN** user taps the Privacy Policy menu row
- **THEN** shell navigates to `profile/privacy-policy`

### Requirement: Empty and loading states for the profile header
The Profile screen SHALL render `KornerzLoadingView` (shimmer) when `ProfileState.user is AsyncValue.Loading` and `KornerzErrorState` when `AsyncValue.Error(throwable)` is emitted (mirrors iOS `SVProgressHUD` + `RetryView` pattern).

#### Scenario: First load shimmer
- **WHEN** `ProfileViewModel.init` and DataStore has not yet hydrated
- **THEN** the header shows a shimmer placeholder until `observeCurrentUser()` emits the first `User`

#### Scenario: Fetch error with retry
- **WHEN** `UserProfileRepository.observeCurrentUser()` emits an error (network failure reading from DataStore)
- **THEN** the header is replaced by `KornerzErrorState` with a Retry button that re-collects the flow

