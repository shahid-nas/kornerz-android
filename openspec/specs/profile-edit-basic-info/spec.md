# profile-edit-basic-info Specification

## Purpose
TBD - created by archiving change phase-6-profile-and-notifications. Update Purpose after archive.
## Requirements
### Requirement: Edit basic info lists five read-only fields with two editable
`EditBasicInfoScreen` SHALL render a `LazyColumn` of `BasicInfoRow`s in this order: Name, E-mail, Phone, Nickname, Password. Each row SHALL display the current value read from the latest `User` emitted by `UserProfileRepository.observeCurrentUser()`. Only Name and Nickname rows SHALL expose a "Change" affordance (right-chevron icon + clickable surface). Password SHALL render masked as `••••••••`. E-mail and Phone SHALL render as plain text with no Change button. Mirrors iOS `EditBasicInfoVC.swift` lines 56–79 + line 118–120.

#### Scenario: All five rows render with current user values
- **WHEN** `EditBasicInfoViewModel` exposes `EditBasicInfoState.Success(fields)` with five `EditableField` rows populated from `User.name`, `User.email`, `User.phoneNumber`, `User.nickName`, `User.password`
- **THEN** the list shows all five rows in the documented order

#### Scenario: Only Name and Nickname show Change buttons
- **WHEN** any row is rendered
- **THEN** only the Name and Nickname rows display a right-chevron icon and respond to taps; Email, Phone, Password rows do not

#### Scenario: Password row masked
- **WHEN** the Password row is rendered with any non-empty `User.password`
- **THEN** the value text is replaced with `••••••••` (8 bullets regardless of length — matches iOS `BasicProfileInfoCell.setValueAsMasked`)

### Requirement: Edit basic info listens to live user updates
`EditBasicInfoViewModel` SHALL collect from `UserProfileRepository.observeCurrentUser()` and update its `fields` list whenever the user changes (e.g., after a successful nickname edit). Mirrors iOS `EditBasicInfoVC.showSuccessMessageScreenWith` lines 150–159 which re-runs `setupMenuList()` after a successful change.

#### Scenario: Successful nickname edit refreshes the row
- **WHEN** user edits nickname and `ChangeBasicInfoNavEvent.UpdatedSuccessfully` returns to the parent
- **THEN** the parent `EditBasicInfoViewModel` re-emits a `Success(fields)` state with the updated nickname value

### Requirement: Edit basic info dispatches OpenChangeScreen nav event
Tapping the Change button on the Name or Nickname row SHALL emit `EditBasicInfoNavEvent.OpenChangeScreen(field: EditableField)` where `EditableField` is the `enum { NAME, EMAIL, PHONE, NICKNAME, PASSWORD }` (only NAME + NICKNAME are reachable per the prior requirement). The shell SHALL route to `profile/edit-basic-info/{field}` as a `dialog` route.

#### Scenario: Tap Name Change button
- **WHEN** user taps the Change button on the Name row
- **THEN** the shell opens `profile/edit-basic-info/NAME` as a centered dialog

#### Scenario: Tap Nickname Change button
- **WHEN** user taps the Change button on the Nickname row
- **THEN** the shell opens `profile/edit-basic-info/NICKNAME` as a centered dialog

