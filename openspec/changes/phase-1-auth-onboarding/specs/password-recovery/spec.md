## ADDED Requirements

### Requirement: User can request a password-reset OTP
The app SHALL provide a Forgot Password screen reachable from the Login screen. The screen SHALL collect an email address via `KornerzTextField`, validate it locally, and call the custom-auth `requestPasswordReset` Cloud Function through `AuthRepository.requestPasswordReset(...)`. `ForgotPasswordViewModel` SHALL expose `submit: AsyncValue<Unit>` (per `architecture.md` §2.2) and emit `NavEvent.NavigateToOtp(email)` over its `Channel<NavEvent>` on success (per §2.4). The screen SHALL NOT reveal whether the email exists in the system — both unknown and known emails SHALL show the same confirmation message ("If an account exists, a code was sent").

#### Scenario: Request issued for known email
- **WHEN** the user submits a registered email
- **THEN** the `requestPasswordReset` Cloud Function is called
- **AND** the app navigates to the OTP confirmation screen

#### Scenario: Request issued for unknown email
- **WHEN** the user submits an unregistered email
- **THEN** the Cloud Function returns success without sending an OTP
- **AND** the app navigates to the OTP confirmation screen with the same message

### Requirement: User can confirm an OTP and proceed to change password
The OTP confirmation screen SHALL collect a 6-digit OTP code via the shared `KornerzOtpTextField` Composable from `core/ui/components/` (mirrors iOS `AEOTPTextField` per `architecture.md` §5). After the user submits the code, the app SHALL call the custom-auth `confirmPasswordResetCode` Cloud Function. On success, `OtpConfirmViewModel` SHALL emit `NavEvent.NavigateToChangePassword(email, resetToken)` over its `Channel<NavEvent>`. On failure (invalid or expired code), `OtpConfirmState.submit` SHALL be `AsyncValue.Error` with message "Invalid or expired code" (per §2.2) and the OTP field SHALL be cleared for re-entry. A "Resend code" control SHALL be available after a 30-second cooldown.

#### Scenario: Valid OTP
- **WHEN** the user enters a valid 6-digit OTP
- **THEN** the confirm Cloud Function returns a reset token
- **AND** the app navigates to the Change Password screen with the token

#### Scenario: Invalid OTP
- **WHEN** the user enters an incorrect or already-used code
- **THEN** the screen displays "Invalid or expired code"
- **AND** the OTP field is cleared for re-entry

#### Scenario: Resend cooldown
- **WHEN** the user has just received an OTP and taps "Resend code"
- **THEN** the "Resend code" control is disabled for 30 seconds
- **AND** re-enables after the cooldown elapses

## References

- [`docs/architecture.md`](../../../../docs/architecture.md) — MVVM + Repository + UDF, `AsyncValue<T>`, `Channel<NavEvent>`, Hilt scopes, `kotlinx.serialization`, shared `core/ui/components/` (notably `KornerzPasswordField`, `KornerzOtpTextField`), canonical library list, clean-code rules §7.
- [`docs/phases.md` §Phase 1](../../../../docs/phases.md) — source of truth for P1 scope and exit checklist.
- [`openspec/specs/design-system/spec.md`](../../../specs/design-system/spec.md) — color tokens, Satoshi font, dark/light themes applied to every new screen.

## ADDED Requirements

### Requirement: User can set a new password using the reset token
The Change Password screen SHALL collect a new password and a confirmation password using two `KornerzPasswordField` instances (per `architecture.md` §5), both validated locally against the same password rules used in signup step 4. On confirm, the app SHALL call the custom-auth `changePassword` Cloud Function with the email, the new password, and the reset token. `ChangePasswordState.submit: AsyncValue<Unit>` (per §2.2) tracks the call. On success, `ChangePasswordViewModel` SHALL emit `NavEvent.NavigateToLoginWithToast("Password updated — please log in")` over its `Channel<NavEvent>` and the auth graph SHALL pop back to Login.

#### Scenario: Password successfully changed
- **WHEN** the user submits a valid new password and matching confirmation
- **THEN** the `changePassword` Cloud Function succeeds
- **AND** the app navigates to Login
- **AND** a "Password updated" toast is shown

#### Scenario: Passwords do not match
- **WHEN** the user enters two passwords that do not match
- **THEN** the "Continue" button is disabled
- **AND** the screen displays "Passwords do not match"

#### Scenario: Weak password
- **WHEN** the user enters a password that fails the signup password validation rules
- **THEN** the screen blocks submission and displays the same rule-violation messages used in signup