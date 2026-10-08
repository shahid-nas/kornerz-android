## ADDED Requirements

### Requirement: User can sign up through a 9-step wizard
The app SHALL provide a sign-up wizard reachable from the Welcome screen's "Sign Up" action via the standalone Privacy Policy screen. Once the policy is accepted, the wizard SHALL collect, in this exact order across 9 screens: (1) full name, (2) nickname, (3) email, (4) password, (5) phone number, (6) country code (dial code), (7) preferred language, (8) Kornerz interests (multi-select from a bundled catalog of Korner slugs), (9) profile photo. The single `SignupViewModel` SHALL be Hilt-scoped (per `architecture.md` §2.6) and SHALL expose `state: StateFlow<SignupWizardState>` and `navEvents: Flow<NavEvent>` (per §2.4 — never reference `NavController` directly). All Compose screens SHALL use the shared `KornerzTextField`, `KornerzPasswordField`, `KornerzImagePicker`, `KornerzButton`, and `KornerzEmptyState` / `KornerzErrorState` from `core/ui/components/` (per §5). Each step SHALL validate its input locally before allowing navigation to the next step. The wizard SHALL persist its current state across configuration changes and process death for all fields using `SavedStateHandle`. The wizard SHALL NOT call the auth backend until step 9 is confirmed; instead, all collected data SHALL be sent in a single sign-up Cloud Function call through `AuthRepository.signup(...)`. After successful sign-up, `SignupViewModel` SHALL emit `NavEvent.NavigateToShell` over `Channel<NavEvent>`, and the root NavHost SHALL swap from `auth_graph` to `shell_graph` clearing the back-stack.

#### Scenario: Successful sign-up end to end
- **WHEN** a user completes all 9 steps and confirms the final step
- **THEN** the sign-up Cloud Function is called once with all collected fields
- **AND** the returned auth token and `User` are written to `SessionRepository`
- **AND** the app navigates to the Home tab of the shell graph
- **AND** the wizard back-stack is cleared

#### Scenario: Email already in use
- **WHEN** the user enters an email that is already registered
- **THEN** the wizard returns to step 3 with a visible error message "This email is already registered"
- **AND** the previously entered password, phone, and subsequent step data remain populated

#### Scenario: Process death mid-wizard
- **WHEN** the OS kills the app process while the user is on step 5
- **THEN** relaunching the app and navigating back into the wizard SHALL restore the user to step 5
- **AND** all previously entered fields are restored from `SavedStateHandle`

#### Scenario: Skip-ahead is not allowed
- **WHEN** a user attempts to navigate from step 2 to step 4 without completing step 3
- **THEN** the wizard SHALL block the navigation
- **AND** display a validation error on step 3

### Requirement: Standalone Privacy Policy gate before the signup wizard
The app SHALL present a dedicated `PrivacyPolicyScreen` immediately after the user taps "Sign Up" on the Welcome screen and immediately before the first step of the signup wizard. The screen SHALL display the full Kornerz Privacy Policy in a scrollable view and SHALL disable the "Accept & Continue" CTA until the user has scrolled the policy to its end at least once. Tapping "Accept & Continue" SHALL navigate to the first step of the signup wizard (full name). Tapping "Decline" SHALL return to the Welcome screen. No wizard data is collected before the policy is accepted.

#### Scenario: Policy must be read
- **WHEN** the user reaches the Privacy Policy screen and has not yet scrolled the policy to the end
- **THEN** the "Accept & Continue" button is disabled

#### Scenario: Button enables after scroll
- **WHEN** the user scrolls the privacy policy to its bottom
- **THEN** the "Accept & Continue" button becomes enabled
- **AND** tapping it navigates to the signup wizard's full-name step

#### Scenario: Decline returns to Welcome
- **WHEN** the user taps "Decline" on the Privacy Policy screen
- **THEN** the app returns to the Welcome screen
- **AND** no wizard state is created

### Requirement: Final wizard step submits without a re-confirmation gate
Step 9 of the wizard SHALL submit the sign-up request when the user taps "Create account" (the step-9 button label), provided the photo upload has completed successfully. There SHALL NOT be a second Privacy Policy gate at step 9 because the user already accepted the policy on the standalone Privacy Policy screen.

#### Scenario: Step 9 submits
- **WHEN** the user has uploaded a profile photo and taps "Create account" on step 9
- **THEN** the sign-up Cloud Function is called
- **AND** the app navigates to the shell graph

### Requirement: Wizard step navigation controls
Each wizard step SHALL provide a "Back" control (disabled on step 1) and a "Continue" control (disabled until the step's local validation passes). Step 9 SHALL replace "Continue" with "Create account" (no privacy gate at step 9).

#### Scenario: Back on step 1
- **WHEN** the user is on step 1
- **THEN** the "Back" control is not displayed

#### Scenario: Back preserves state
- **WHEN** the user taps "Back" on step 5
- **THEN** the wizard returns to step 4
- **AND** step 5's previously entered value is preserved

### Requirement: Country code selection screen uses bundled catalog
Step 6 SHALL display a searchable, scrollable list of countries derived from a bundled `CountryCodes` JSON asset shipped in `core/model`. Each row SHALL display the country flag, English country name, and dial code. Tapping a row SHALL select that dial code and navigate back to step 5.

#### Scenario: Country codes load
- **WHEN** the user opens step 6
- **THEN** the list displays at least 200 entries from the bundled catalog

#### Scenario: Search filters the list
- **WHEN** the user types "br" in the search field
- **THEN** only entries whose English name or dial code starts with "br" are shown

### Requirement: Kornerz interests selection from bundled catalog
Step 8 SHALL show a multi-select grid of Korner interests fetched from a bundled catalog in `core/model`. The user SHALL select between 1 and 5 interests. The "Continue" button SHALL be disabled until at least one interest is selected.

#### Scenario: Minimum one interest
- **WHEN** the user has not selected any interest on step 8
- **THEN** the "Continue" button is disabled

#### Scenario: Maximum five interests
- **WHEN** the user has already selected 5 interests and taps a sixth
- **THEN** the selection is rejected with a visible message "You can pick up to 5 favorites"

## References

- [`docs/architecture.md`](../../../../docs/architecture.md) — MVVM + Repository + UDF, `AsyncValue<T>`, `Channel<NavEvent>`, Hilt scopes, `kotlinx.serialization`, shared `core/ui/components/` (notably `KornerzPasswordField`, `KornerzImagePicker`), canonical library list, clean-code rules §7.
- [`docs/phases.md` §Phase 1](../../../../docs/phases.md) — source of truth for P1 scope and exit checklist.
- [`openspec/specs/design-system/spec.md`](../../../specs/design-system/spec.md) — color tokens, Satoshi font, dark/light themes applied to every new screen.

## ADDED Requirements

### Requirement: Photo upload on the final wizard step
Step 9 SHALL allow the user to pick a profile photo via the shared `KornerzImagePicker` Composable from `core/ui/components/` (which wraps `ActivityResultContracts.PickVisualMedia` and `ActivityResultContracts.TakePicture`). The selected image SHALL be uploaded via the Firebase Storage v4 signed-URL Cloud Function before the sign-up request is sent. Upload progress SHALL be exposed in `SignupWizardState.photoUpload: AsyncValue<PhotoUrl>` (per `architecture.md` §2.2 — `Loading` / `Success` / `Error`), rendered by `KornerzLoadingView` / preview / `KornerzErrorState`. If the photo upload fails, the wizard SHALL allow the user to retry without losing the other 8 steps' data.

#### Scenario: Gallery pick
- **WHEN** the user taps "Choose from gallery" and picks an image
- **THEN** a preview thumbnail is displayed
- **AND** the chosen URI is stored in wizard state

#### Scenario: Upload retry
- **WHEN** the signed-URL upload fails on the first attempt
- **THEN** the wizard displays a retry control
- **AND** retrying reuses the previously selected image without re-prompting the picker