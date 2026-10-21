# privacy-policy-pdf Specification

## Purpose
TBD - created by archiving change phase-6-profile-and-notifications. Update Purpose after archive.
## Requirements
### Requirement: Privacy policy viewer renders bundled PDF via AndroidView(PDFView)
`PrivacyPolicyScreen` SHALL render a `KornerzTopAppBar` (back button + "Privacy Policy" title) above a `PDFView` wrapped in `AndroidView`. The viewer SHALL load `file:///android_asset/privacy/privacy_en.pdf` via `pdfView.fromAsset("privacy/privacy_en.pdf").load()`. Mirrors iOS `PrivacyPolicyVC.swift` lines 49–80 (`PDFView` loading `Privacy-Police-en.pdf` from the bundle with `autoScales = true`, `minScaleFactor = scaleFactorForSizeToFit`, `maxScaleFactor = 5.0`).

#### Scenario: PDF loads on first composition
- **WHEN** `PrivacyPolicyScreen` enters composition
- **THEN** the `AndroidView(PDFView)` mounts, `fromAsset(...).load()` completes, and the PDF renders zoomed-to-fit

#### Scenario: Pinch-to-zoom bounded to maxScaleFactor 5.0
- **WHEN** user pinches to zoom in
- **THEN** the zoom is capped at `maxScaleFactor = 5.0` (matches iOS line 72)

### Requirement: Accept button hidden when opened from Profile
When `PrivacyPolicyViewModel.isFromProfile = true`, the bottom "I have read and accepted" button SHALL be hidden (mirrors iOS `PrivacyPolicyVC.swift` lines 42–44 — `if isComingFromUserDetail { acceptedPrivacyButton.isHidden = true }`). When `isFromProfile = false` (opened from the onboarding wizard), the button SHALL be visible and tapping it SHALL emit `PrivacyPolicyNavEvent.Accepted` which is handled by the onboarding flow to set `User.acceptedTermsCondition = true`.

#### Scenario: Profile origin hides accept button
- **WHEN** shell navigates to `profile/privacy-policy` with `isFromProfile = true`
- **THEN** the accept button is not rendered

#### Scenario: Onboarding origin shows accept button
- **WHEN** shell navigates to `profile/privacy-policy` with `isFromProfile = false`
- **THEN** the accept button is rendered

### Requirement: Back button pops the route
Tapping the back button on `KornerzTopAppBar` SHALL emit `PrivacyPolicyNavEvent.Back` which the shell handles by popping the route. When `isFromProfile = false`, back instead invokes `PrivacyPolicyNavEvent.Declined` so the onboarding wizard can treat it as a decline (mirrors iOS line 106–110).

#### Scenario: Back from profile pops the route
- **WHEN** user taps back with `isFromProfile = true`
- **THEN** the shell pops to the previous route (`profile/view` or wherever the user came from)

#### Scenario: Back from onboarding emits declined
- **WHEN** user taps back with `isFromProfile = false`
- **THEN** `PrivacyPolicyNavEvent.Declined` is emitted so the onboarding wizard can prompt re-display

