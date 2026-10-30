# account-deletion Specification

## Purpose
TBD - created by archiving change phase-6-profile-and-notifications. Update Purpose after archive.
## Requirements
### Requirement: AccountDeletionViewModel exposes confirm dialog state and deletion pipeline
`AccountDeletionViewModel` SHALL expose `AccountDeletionState(showConfirmDialog: Boolean, isDeleting: Boolean, error: AppError?)`. Tapping the (currently-unsurfaced) Delete Account menu row SHALL transition `showConfirmDialog = true`. Confirming SHALL call `UserProfileRepository.deleteAccount()` which posts to the `deleteUserAccount` Cloud Function. Mirrors iOS `UserProfileVC.deleteAccount()` lines 152–185.

#### Scenario: Confirm dialog opens
- **WHEN** user triggers the Delete Account action (UI row wiring is P10)
- **THEN** `AccountDeletionViewModel.showConfirmDialog = true` and the confirm dialog renders

#### Scenario: Confirm invokes deleteUserAccount
- **WHEN** user confirms the dialog
- **THEN** `UserProfileRepository.deleteAccount()` is invoked and `isDeleting = true` is exposed (KornerzLoadingView overlay shown)

### Requirement: Successful deletion clears session and routes to auth
On `Result.Success(Unit)` from `deleteUserAccount`, `AccountDeletionViewModel` SHALL:
1. Call `SessionStorage.clear()` to wipe DataStore session keys.
2. Call `UserProfileRepository.removeFcmToken()` to delete `Users/{userCode}.tokenPush` from Firestore.
3. Emit `userDeletedHisAccount` on a shared event bus (mirrors iOS `NotificationCenter.default.post(name: .userDeletedHisAccount, object: nil)`).
4. Emit `AccountDeletionNavEvent.NavigateToAuth`.

The shell SHALL listen for `userDeletedHisAccount` and route the root NavHost back to the auth flow exactly as it does on logout. Mirrors iOS lines 166–175.

#### Scenario: Successful deletion routes to auth
- **WHEN** `deleteUserAccount` returns `Success`
- **THEN** session is cleared, FCM token is removed, the event bus emits `userDeletedHisAccount`, and the shell root NavHost shows the auth flow

### Requirement: Failed deletion surfaces an error and retains session
On `Result.Failure(error)`, `AccountDeletionViewModel` SHALL expose `AccountDeletionState.Error(message)` and SHALL NOT clear session or remove the FCM token. The confirm dialog SHALL dismiss and a `KornerzErrorState`-style alert SHALL be shown.

#### Scenario: Deletion failure surfaces error
- **WHEN** `deleteUserAccount` returns `Failure(error)`
- **THEN** session and FCM token remain intact, an error alert is shown, and the user remains on the profile screen

