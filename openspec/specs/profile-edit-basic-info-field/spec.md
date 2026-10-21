# profile-edit-basic-info-field Specification

## Purpose
TBD - created by archiving change phase-6-profile-and-notifications. Update Purpose after archive.
## Requirements
### Requirement: Change basic info modal shows one field at a time
`ChangeBasicInfoScreen` SHALL be registered as a `dialog` route and SHALL render a centered card (rounded 20-pt corners, background `colorScheme.surface`) with: a Satoshi Black 22-pt heading (`C353446` mapped to `colorScheme.onSurface`), a Satoshi Bold 26-pt `KornerzTextField` pre-filled with the original value (`C1B1A23` mapped to `colorScheme.onSurface`), and a pill Save button (Satoshi Bold 17-pt). Mirrors iOS `ChangeBasicInfoVC.swift` lines 57–89.

#### Scenario: Heading reflects the field type
- **WHEN** the dialog route is opened with `field = NAME`
- **THEN** the heading text reads "Full Name" (mirrors iOS line 75–76)

#### Scenario: Heading for nickname
- **WHEN** the dialog route is opened with `field = NICKNAME`
- **THEN** the heading text reads "Nickname" (mirrors iOS line 83–84)

### Requirement: Save button enabled only when value differs from original
The Save button SHALL be disabled (background `CF2F4F6` mapped to `colorScheme.surfaceVariant`, text `C9E9EAE` mapped to `colorScheme.onSurfaceVariant`, no ripple) when `draftValue.isEmpty() || draftValue == originalValue`. It SHALL be enabled (background `C5D47ED` mapped to `colorScheme.primary`, text white) otherwise. Mirrors iOS `ChangeBasicInfoVC.swift` lines 95–116.

#### Scenario: Empty input disables Save
- **WHEN** `ChangeBasicInfoViewModel.draftValue.isEmpty()`
- **THEN** Save is disabled

#### Scenario: Unchanged input disables Save
- **WHEN** `ChangeBasicInfoViewModel.draftValue == ChangeBasicInfoViewModel.originalValue`
- **THEN** Save is disabled

#### Scenario: Changed input enables Save
- **WHEN** `draftValue.isNotEmpty() && draftValue != originalValue`
- **THEN** Save is enabled

### Requirement: Saving name calls UserProfileRepository.updateName
On Save with `field = NAME`, `ChangeBasicInfoViewModel` SHALL call `UserProfileRepository.updateName(userCode, newName)`. The repository SHALL write `Users/{userCode}` with the new `name` field via `setData(["name": newName], merge: true)` (mirrors iOS `FireStoreServiceManager.registerUserWith` line 18–28 used from `ChangeBasicInfoViewModel.updateNameWith` line 12–27). On `Result.Success(updatedUser)` the repository SHALL write the updated `User` to DataStore and emit it through the `observeCurrentUser()` `Flow`. The ViewModel SHALL then emit `ChangeBasicInfoNavEvent.UpdatedSuccessfully(message: "name_changed_message")`.

#### Scenario: Successful name save updates the parent screen
- **WHEN** user taps Save with `field = NAME` and a non-empty value different from the original
- **THEN** `UserProfileRepository.updateName` writes the new value to Firestore, the parent `EditBasicInfoViewModel` re-emits `Success` with the new name, and the success dialog is shown

#### Scenario: Failed name save shows an error
- **WHEN** `UserProfileRepository.updateName` returns `Result.Failure(error)`
- **THEN** `ChangeBasicInfoViewModel` exposes `ChangeBasicInfoState.Error(message)` and an `KornerzErrorState`-style dialog is shown

### Requirement: Saving nickname enforces uniqueness
On Save with `field = NICKNAME`, `ChangeBasicInfoViewModel` SHALL first call `UserProfileRepository.checkIfNickNameExists(nickName)`. If `Result.Success(true)` (taken), the ViewModel SHALL expose `ChangeBasicInfoState.Error(message = "nick_name_not_available")` and SHALL NOT write. If `Result.Success(false)` (available), the ViewModel SHALL call `UserProfileRepository.updateNickName(userCode, newNickName)` which writes `Users/{userCode}` with the new `nickname` field via `setData(["nickname": newNickName], merge: true)` and emits `ChangeBasicInfoNavEvent.UpdatedSuccessfully(message = "nickname_changed_message")`. Mirrors iOS `ChangeBasicInfoViewModel.updateNickNameWith` lines 29–56 + `FireStoreServiceManager.checkIfNickNameExist` lines 30–50.

#### Scenario: Nickname taken error path
- **WHEN** user taps Save with `field = NICKNAME` and `checkIfNickNameExists` returns `Success(true)`
- **THEN** `ChangeBasicInfoViewModel` exposes `ChangeBasicInfoState.Error(message = "nick_name_not_available")` and no Firestore write occurs

#### Scenario: Nickname available success path
- **WHEN** user taps Save with `field = NICKNAME` and `checkIfNickNameExists` returns `Success(false)`
- **THEN** `UserProfileRepository.updateNickName` writes the new value, the parent screen refreshes, and the success dialog is shown

### Requirement: IME insets push the Save button above the keyboard
When the soft keyboard appears, the dialog SHALL pad its bottom edge by `WindowInsets.ime` height (mirrors iOS `handle(keyboardShowNotification:)` lines 130–139 which sets `buttonChangeBC.constant = keyboardRectangle.height + 5`). When the keyboard hides, the padding SHALL reset to the default 20-dp bottom inset.

#### Scenario: Keyboard show insets Save
- **WHEN** the user focuses the input field on a device with `WindowInsets.ime` > 0
- **THEN** the Save button's bottom edge is at least 5-dp above the keyboard top

#### Scenario: Keyboard hide resets inset
- **WHEN** the keyboard dismisses
- **THEN** the Save button's bottom edge returns to the default 20-dp inset

### Requirement: Success dialog confirms the save
After `ChangeBasicInfoNavEvent.UpdatedSuccessfully` is emitted, the shell SHALL dismiss the change dialog and open `profile/edit-basic-info/{field}/success` as a `dialog` route (mirrors iOS `ChangeBasicInfoSuccessVC`). The success dialog SHALL display the success message string (e.g., "Name changed successfully" / "Nickname changed successfully") in a Satoshi Black 22-pt label and a Close button (Satoshi Bold 17-pt, `C5D47ED` background, white text). Tapping Close dismisses the dialog and pops the parent `EditBasicInfoScreen`. Mirrors iOS `ChangeBasicInfoSuccessVC.swift` lines 10–55.

#### Scenario: Success dialog shown after name save
- **WHEN** `ChangeBasicInfoNavEvent.UpdatedSuccessfully(message = "name_changed_message")` is emitted
- **THEN** the change dialog dismisses, the success dialog opens with the message "Name changed successfully", and tapping Close returns to `EditBasicInfoScreen`

#### Scenario: Success dialog shown after nickname save
- **WHEN** `ChangeBasicInfoNavEvent.UpdatedSuccessfully(message = "nickname_changed_message")` is emitted
- **THEN** the change dialog dismisses, the success dialog opens with the message "Nickname changed successfully", and tapping Close returns to `EditBasicInfoScreen`

