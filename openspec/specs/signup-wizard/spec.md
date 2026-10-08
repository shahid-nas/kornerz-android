# signup-wizard Specification

## Purpose
TBD - created by archiving change phase-1-auth-onboarding. Update Purpose after archive.
## Requirements
### Requirement: Every wizard step renders the iOS branded layout
Every step of the signup wizard (1 through 8) SHALL render inside the shared `BrandedSignupScaffold` (feature/auth/.../signup/BrandedSignupComponents.kt), which mirrors the iOS signup view family in kornerz/.../Signup/Views/ 1:1: a full-bleed Aqua `#68B6CC` background, a `bg-clouds-light` decoration image (250dp tall, `ContentScale.Fit`, y+60 offset) above the form, a white Satoshi Medium 24sp leading-aligned title with 16dp bottom padding, 32dp outer padding and 16dp inner spacing, a white "Back" icon button (`Icons.AutoMirrored.Filled.ArrowBack`, 20dp, white tint) in the top-start corner, and white Satoshi body copy. The previous Material 3 `SignupStepScaffold` (with "Step X of Y" label, default purple CTA, and standard M3 text fields) SHALL NOT be used for any wizard step. The shared `KornerzTextField` / `KornerzPasswordField` / `KornerzButton` from `core/ui/components/` SHALL NOT be used inside the wizard — every step uses the branded `BrandedTextField` / `BrandedPasswordField` / `BlackCapsuleButton` / `BorderedCapsuleButton` helpers instead.

#### Scenario: All 8 steps share the Aqua background
- **WHEN** the user advances through any of the 8 wizard steps
- **THEN** every screen background is solid Aqua `#68B6CC` (not the M3 `colorScheme.background` Smoke)
- **AND** the cloud decoration is visible at the top of every screen
- **AND** the white back chevron is visible in the top-start of every step

#### Scenario: No "Step X of Y" label or M3 purple button
- **WHEN** the user is on any wizard step
- **THEN** the screen does not show a "Step X of Y" label, an outlined M3 text field, or a square purple M3 button
- **AND** the primary CTA is always a black capsule labelled "Next" (or, on step 8, "Create account") with the iOS-style disabled palette `#F2F4F6` / `#9E9EAE`

### Requirement: Step 1 — Full name matches the iOS design 1:1
Step 1 of the wizard SHALL render the dedicated `SignupFullNameContent` composable, which mirrors the iOS `SignupFullNameView` (kornerz/Kornerz/SwiftUI/OnBoarding/Signup/Views/SignupFullNameView.swift) exactly. The screen SHALL have a full-bleed Aqua `#68B6CC` background, a `bg-clouds-light` decoration image (250dp tall, y+60 offset, `ContentScale.Fit`) rendered above the form, a white Satoshi Medium 24sp leading-aligned title "What is your name?", a transparent text field with a 2dp white left vertical bar, Satoshi Bold 24sp white text and placeholder, and a full-width black capsule "Next" CTA. The CTA SHALL be disabled until the input is valid; while disabled its background SHALL be `#F2F4F6` and its label SHALL be `#9E9EAE`. The remaining seven steps (nickname, email, password, phone, language, interests, photo) SHALL continue to use the shared `SignupStepScaffold` and the existing M3 component set. Step 1 SHALL provide a white "Back" icon button in the top-start that dispatches `SignupEvent.Back` and pops the wizard back to the Privacy Policy screen when on step 1.

#### Scenario: Step 1 visual parity with iOS
- **WHEN** a user opens step 1 of the wizard
- **THEN** the background is solid Aqua `#68B6CC` (not the M3 `colorScheme.background`)
- **AND** the cloud decoration image is visible at the top of the screen
- **AND** the title "What is your name?" renders in white Satoshi Medium 24sp
- **AND** the text field shows a 2dp white vertical bar to the left of the input
- **AND** the CTA reads "Next" (not "Continue") and is rendered as a full-width black capsule

#### Scenario: Next button enables only on valid full name
- **WHEN** the user has entered a name that matches the iOS validation
- **THEN** the "Next" capsule is enabled (black background, white label)
- **WHEN** the name is empty, has fewer than two whitespace-separated tokens, or any token contains characters other than letters, hyphens, or apostrophes
- **THEN** the "Next" capsule is disabled (`#F2F4F6` background, `#9E9EAE` label)

#### Scenario: Back icon on step 1 returns to Privacy Policy
- **WHEN** the user taps the white "Back" icon at the top-start of step 1
- **THEN** the wizard emits `SignupNavEvent.NavigateBack` and the navigation pops the back-stack to the Privacy Policy screen

### Requirement: Full-name validation mirrors iOS regex
The `isStep1Valid` selector on `SignupWizardState` SHALL match the iOS `SignupViewModel.isFullNameValid` rule: the trimmed value SHALL be non-empty, SHALL contain at least two whitespace-separated tokens, and every token SHALL match the regex `^[a-zA-Z]+(?:[\s\-']*[a-zA-Z]+)*$` so that names with hyphens (e.g. "Anne-Marie") and apostrophes (e.g. "O'Connor") are accepted while numeric and other characters are rejected.

#### Scenario: Hyphenated and apostrophe names accepted
- **WHEN** the user enters "Anne-Marie O'Connor"
- **THEN** `isStep1Valid` returns true
- **AND** the "Next" CTA becomes enabled

#### Scenario: Single name rejected
- **WHEN** the user enters only "Madonna"
- **THEN** `isStep1Valid` returns false
- **AND** the "Next" CTA stays disabled

#### Scenario: Digits rejected
- **WHEN** the user enters "John 2 Doe"
- **THEN** `isStep1Valid` returns false
- **AND** the "Next" CTA stays disabled

### Requirement: Step 2 — Nickname matches the iOS design
Step 2 SHALL render the title "Choose a nickname" in white Satoshi Medium 24sp, a single `BrandedTextField` bound to `state.nickname` (placeholder "Nickname", email keyboard, `KeyboardCapitalization.None`), and an HStack with a "Suggest nickname" `BorderedCapsuleButton` (black border) and a "Next" `BlackCapsuleButton`. The "Next" CTA SHALL be enabled when `state.isStep2Valid` is true (3–20 chars, `^[a-zA-Z0-9_-]+$`). An optional red error message SHALL render below the field when `state.nicknameError` is non-null (used by iOS when the nickname is already taken).

#### Scenario: Step 2 visual parity with iOS
- **WHEN** the user is on step 2
- **THEN** the title reads "Choose a nickname"
- **AND** the "Suggest nickname" button is a black-bordered capsule
- **AND** the "Next" button is a black capsule (enabled when the nickname is 3–20 chars and matches `^[a-zA-Z0-9_-]+$`)

### Requirement: Step 3 — Email matches the iOS design
Step 3 SHALL render the title "What is your email?" in white Satoshi Medium 24sp, a single `BrandedTextField` bound to `state.email` (placeholder "Valid email", `KeyboardType.Email`, `KeyboardCapitalization.None`), and a "Next" `BlackCapsuleButton` enabled when `state.isStep3Valid` is true and `state.emailAlreadyInUse` is false. When `emailAlreadyInUse` is true, a red "This email is already registered" message SHALL render below the field.

#### Scenario: Step 3 visual parity with iOS
- **WHEN** the user is on step 3
- **THEN** the title reads "What is your email?" in white Satoshi Medium 24sp
- **AND** the field uses the email keyboard
- **AND** the "Next" capsule is black when the input matches the iOS email regex, and disabled (gray) otherwise

### Requirement: Step 4 — Password matches the iOS design
Step 4 SHALL render the title "Create a password with at least 6 digits" in white Satoshi Medium 24sp, two `BrandedPasswordField`s with eye toggles bound to `state.password` and `state.confirmPassword` (placeholders "Password" and "Confirm password"), an "At least 6 characters." hint in white alpha 0.8 Satoshi 12sp, and a "Next" `BlackCapsuleButton` enabled when `state.isStep4Valid` is true (password length ≥ 6 and matches `confirmPassword`).

#### Scenario: Step 4 visual parity with iOS
- **WHEN** the user is on step 4
- **THEN** the title reads "Create a password with at least 6 digits"
- **AND** each password field shows a white eye icon to toggle visibility
- **AND** the "Next" capsule is black only when both password fields are populated, the password is at least 6 chars, and the values match

### Requirement: Step 5 — Phone matches the iOS design
Step 5 SHALL render the title "What is your phone number?" in white Satoshi Medium 24sp. The form row SHALL contain a `BorderedCapsuleButton` (white border) showing `state.countryDialCode` and opening a `CountryCodePickerSheet` on tap, and a `BrandedTextField` bound to `state.phone` (placeholder "Phone Number", `KeyboardType.Phone`). The CTA row SHALL contain a "Skip" `BorderedCapsuleButton` (black border) and a "Next" `BlackCapsuleButton`. Both buttons dispatch `SignupEvent.Continue`. The "Next" CTA SHALL be enabled when `state.isStep5Valid` is true — i.e. `countryDialCode + phone` stripped of spaces/hyphens/parentheses matches `^\+[0-9]{10,15}$`. Tapping the country code capsule SHALL open a `ModalBottomSheet` listing the bundled country catalog (dial code + name); selecting a row dispatches `SignupEvent.CountrySelected(...)` and dismisses the sheet.

#### Scenario: Step 5 visual parity with iOS
- **WHEN** the user is on step 5
- **THEN** the country code is rendered as a white-bordered capsule to the left of the phone field
- **AND** both "Skip" and "Next" capsules are visible at the bottom
- **AND** the "Next" capsule stays disabled (gray) until the full phone (country code + digits, ignoring formatting) is 10–15 digits long and prefixed with `+`

### Requirement: Step 6 — Language matches the iOS design
Step 6 SHALL render the title "Which languages are you able to communicate easily?" in white Satoshi Medium 24sp, a 2-column `SelectableCapsuleGrid` of language options, and a "Next" `BlackCapsuleButton` enabled when `state.languages.isNotEmpty()`. The default catalog is a hard-coded list mirroring the iOS `SignupViewModel.allLanguages` (English, Portuguese (Brazil), Spanish, Italian, French, German, Russian, Japanese, Arabic, Mandarin) so both apps present the same options. A selected option SHALL render as a black-on-white capsule; an unselected option SHALL render as a white-bordered capsule. Tapping a capsule SHALL dispatch `SignupEvent.LanguageToggled(code)`.

#### Scenario: Step 6 multi-select parity
- **WHEN** the user taps one or more language capsules
- **THEN** each tapped capsule flips between black-on-white (selected) and white-bordered (unselected)
- **AND** the "Next" capsule stays disabled until at least one language is selected

### Requirement: Step 7 — Interests match the iOS design
Step 7 SHALL render the title "Pick your Korners. Which topics are you interested in?" in white Satoshi Medium 24sp, a 2-column `SelectableCapsuleGrid` of `KornerInterest` items loaded from the bundled `KornerInterestRepository` (rendered through `AsyncValueContent`), and a "Next" `BlackCapsuleButton` enabled when `state.interests.isNotEmpty() && state.interests.size <= 5`. The "You can pick up to 5 favorites" hint SHALL appear in white Satoshi 12sp when `state.interestsError` is true.

#### Scenario: Step 7 multi-select with 5-item cap
- **WHEN** the user has selected 5 interests and taps a sixth
- **THEN** the selection is rejected (no sixth item added)
- **AND** the "You can pick up to 5 favorites" hint becomes visible
- **AND** the "Next" capsule remains enabled because at least one interest is selected

### Requirement: Step 8 — Photo matches the iOS design
Step 8 SHALL render the title "Add a photo" in white Satoshi Medium 24sp, a centered 150dp × 150dp black `RoundedCornerShape(32.dp)` square containing the text "Add Photo" (or "Photo selected" after a pick) in white Satoshi Medium 16sp, and a CTA row with "Skip" and "Next" `BorderedCapsuleButton` (black) and `BlackCapsuleButton`. Tapping the photo tile SHALL launch `ActivityResultContracts.PickVisualMedia.ImageOnly`; on success, the selected `Uri` is dispatched via `SignupEvent.PhotoSelected(uri)`. The "Next" capsule SHALL be enabled when `state.isStep8Valid` is true (photo upload `AsyncValue.Success`).

#### Scenario: Step 8 photo parity
- **WHEN** the user opens step 8 without a selected photo
- **THEN** the centered 150dp black rounded square shows "Add Photo" in white
- **AND** the "Next" capsule is disabled (gray)
- **AND** tapping the photo tile opens the system gallery picker
- **WHEN** a photo is selected
- **THEN** the tile text changes to "Photo selected" and the "Next" capsule becomes black

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
