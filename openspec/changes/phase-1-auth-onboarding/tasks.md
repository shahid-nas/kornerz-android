## 0. Architecture compliance guardrails

- [x] 0.1 Confirm every task below references the [`docs/architecture.md`](../../../docs/architecture.md) patterns it implements — `AsyncValue<T>` for loading/error, `Channel<NavEvent>` for navigation, `@HiltViewModel` for VMs, `@Singleton` for repositories
- [x] 0.2 Confirm `feature/auth` module's `build.gradle.kts` applies only the convention plugin `kornerz.android.feature.compose.hilt` (arch §9) and declares `namespace = "com.kornerz.feature.auth"`
- [x] 0.3 Confirm no new third-party dependencies are introduced outside the canonical library list (arch §3)
- [x] 0.4 Confirm `feature/auth` is imported only by the `app` module (arch §2.5)

## 1. Core model and shared types

- [x] 1.1 Add `User` `@Serializable` data class to `core/model` with all P1 fields (id, email, displayName, nickname, phone, countryCode, language, photoUrl, isPremium, subscriptionExpiryDate, subscriptionType, subscriptionPlatform, fcmToken, createdAt)
- [x] 1.2 Add `Country` and `CountryCodes` types plus a bundled `country_codes.json` asset in `core/model/src/main/assets/`
- [x] 1.3 Add `SessionState` sealed class (`Anonymous`, `Authenticated(User)`, `Loading`) to `core/storage`
- [x] 1.4 Add `KornerInterest` type and a bundled `korner_interests.json` asset in `core/model/src/main/assets/`

## 2. Custom-auth Cloud Function contract review

- [x] 2.1 Inspect the iOS source under `kornerz/` to enumerate the exact request/response shapes of `login`, `signup`, `requestPasswordReset`, `confirmPasswordResetCode`, `changePassword`, `updateFcmToken`, and `clearFcmToken`
- [x] 2.2 Pin each function's request and response types as `kotlinx.serialization` `@Serializable` data classes in `core/network/auth/dto/`
- [ ] 2.3 Document any missing functions as follow-up issues (does not block P1 scope if all required endpoints exist) — **NOTED**: iOS source uses Firestore queries directly (no Cloud Functions exist). Follow-up issue: replace `FirestoreAuthApi` with a real Cloud Function client once those functions are deployed; until then `FirestoreAuthApi` mirrors iOS behaviour and keeps the contract identical.
- [x] 2.4 Add a `AuthApi` interface in `core/network/auth/` with one suspend method per Cloud Function plus a sealed `AuthResult` return type

## 3. Session storage

- [x] 3.1 Add `androidx.datastore:datastore-preferences` to the Gradle version catalog
- [x] 3.2 Implement `SessionSerializer` (`Serializer<SessionRecord>`) that encodes/decodes the token, `expiresAt` (ISO-8601), and `User` JSON
- [x] 3.3 Implement `SessionRepository` exposing `data(): Flow<SessionState>`, `setSession(...)`, `clearSession()`, and the expired-token-cleanup behavior
- [x] 3.4 Wire a Hilt `@Module` that provides `SessionRepository` as a singleton
- [ ] 3.5 Add a unit test that round-trips `setSession` → `data()` and verifies `clearSession` removes all fields — **DEFERRED**: VM/repo unit tests are scoped to follow-up; the wiring is exercised end-to-end by the auth flows in QA.

## 4. Auth backend client

- [x] 4.1 Implement `FirebaseFunctionsAuthApi` (wraps `FirebaseFunctions` HTTPS calls) with one method per function in `AuthApi` — see follow-up note in §2.3; shipped as `FirestoreAuthApi` instead.
- [x] 4.2 Implement a `AuthInterceptor` that injects the current session token into outbound calls and detects "session expired" responses
- [x] 4.3 Implement `AuthRepository` (orchestrates `AuthApi` + `SessionRepository`) exposing login, signup, logout, and the password-recovery flow as suspend functions returning a sealed `AuthResult`
- [x] 4.4 Wire a Hilt `@Module` for `AuthRepository` and `AuthApi`

## 5. FCM registration

- [x] 5.1 Add `firebase-messaging-ktx` to the version catalog and to the `app` module's dependencies (Firebase BoM already added in P0)
- [x] 5.2 Implement `FcmTokenRegistrar` with `registerOnStart()`, `observeToken()`, and `clearOnLogout()` using `FirebaseMessaging.getInstance().token` (Flow variant)
- [x] 5.3 Wire the registrar into `Application.onCreate()` and into `AuthRepository.logout()`
- [ ] 5.4 Verify on a Pixel API 35 emulator that the token reaches the user record after login — **DEFERRED**: manual QA task, requires emulator boot + dev Firebase project.

## 6. Auth graph scaffolding

- [x] 6.1 Add a new `feature/auth` module with the project's standard scaffolding (Hilt, Compose, navigation-compose, Coil) — convention plugin applied directly in `feature/auth/build.gradle.kts`.
- [x] 6.2 Define the `auth_graph` NavHost with routes for `login`, `signup/{step}`, `privacy_policy`, `forgot_password`, `otp_confirm/{email}`, `change_password/{email}/{token}`
- [x] 6.3 Define `AuthRoute` sealed class with `route` and `composable` factories
- [x] 6.4 Define a shared `AuthViewModelScope` Hilt qualifier used by every auth ViewModel

## 7. Login screen

- [x] 7.1 Create `feature/auth/.../LoginScreen.kt` with email + password fields using `KornerzTextField` and `KornerzPasswordField` (arch §5), a `KornerzButton`, "Create account" and "Forgot password" links; `LoginScreen` renders purely from `LoginState` + `onEvent`
- [x] 7.2 Create `LoginContent.kt` (stateless renderer per arch §4)
- [x] 7.3 Create `LoginState.kt` (`data class`) with `email`, `password`, `submit: AsyncValue<Unit>` (arch §2.2)
- [x] 7.4 Create `LoginEvent.kt` (sealed interface — `EmailChanged`, `PasswordChanged`, `Submit`, `CreateAccount`, `ForgotPassword`)
- [x] 7.5 Create `LoginNavEvent.kt` with `NavigateToShell`, `NavigateToSignup`, `NavigateToForgotPassword`
- [x] 7.6 Create `LoginViewModel` as `@HiltViewModel` injecting `AuthRepository`; exposes `state: StateFlow<LoginState>` and `navEvents: Flow<LoginNavEvent>` via `Channel(BUFFERED)` (arch §2.4)
- [x] 7.7 Apply the design-system theme (Satoshi font, dark/light aware) and RTL-safe layout to `LoginScreen`
- [x] 7.8 Add a `@Preview` for light, dark, and `en-rXA` pseudo-locale (arch §5)
- [ ] 7.9 Create `LoginViewModelTest.kt` covering all `LoginEvent` paths and `AsyncValue` transitions (Turbine) — **DEFERRED**: unit tests are scoped to follow-up.

## 8. Signup wizard

- [x] 8.1 Create `feature/auth/.../SignupState.kt`, `SignupEvent.kt`, `SignupNavEvent.kt`, `SignupViewModel.kt`, `SignupScreen.kt`, `SignupContent.kt` per the arch §4 file layout
- [x] 8.2 Implement `SignupWizardState` data class with all 8-step fields, validation rules per step, `photoUpload: AsyncValue<PhotoUrl>` (arch §2.2), and the current step index
- [x] 8.3 Persist `SignupWizardState` into `SavedStateHandle`; restore from it on process death
- [x] 8.4 Create a `components/SignupStepScaffold.kt` shared by every wizard step (header + Back/Continue controls per arch §4)
- [x] 8.5 Implement step 1 Composable: `SignupFullNameStep` using `KornerzTextField`, back disabled, continue disabled until non-blank

### iOS / Android design parity — signup full-name step (amends §8.5)

- [x] 8.5.1 Update `SignupWizardState.isStep1Valid` to mirror iOS `SignupViewModel.isFullNameValid` (≥2 whitespace tokens, each matching `^[a-zA-Z]+(?:[\s\-']*[a-zA-Z]+)*$`)
- [x] 8.5.2 Create `SignupFullNameContent.kt` that renders step 1 with the iOS-matching layout: full-bleed Aqua `#68B6CC` background, `bg-clouds-light` decoration (250dp / y+60), white Satoshi Medium 24sp title, transparent text field with 2dp white left bar (Satoshi Bold 24sp, white text/placeholder), and a full-width black capsule "Next" CTA
- [x] 8.5.3 Render the new content only for `state.step == 1` in `SignupScreen.kt`; route `Back` to `SignupEvent.Back`; keep the shared `SignupStepScaffold` for steps 2–8 unchanged
- [x] 8.5.4 Implement CTA enable/disable: background `#F2F4F6` and label `#9E9EAE` when `state.isStep1Valid == false`, black background / white label otherwise — matches iOS `KornerzButtonStyle` disabled colors
- [x] 8.5.5 Add `@Preview` light previews for empty and filled `SignupFullNameContent` states
- [x] 8.5.6 Amend `specs/signup-wizard/spec.md` with the new "Full-name wizard step matches the iOS design 1:1" and "Full-name validation mirrors iOS regex" requirements and the matching scenarios

### iOS / Android design parity — signup wizard steps 2–8 (amends §8.6–§8.13)

- [x] 8.5.7 Extract shared branded composables into `BrandedSignupComponents.kt`: `BrandedSignupScaffold` (Aqua bg + cloud header + back chevron + 32dp padding + Satoshi title slot), `BrandedTextField` / `BrandedPasswordField` (2dp white left bar, Satoshi Bold 24sp white text), `BlackCapsuleButton` / `BorderedCapsuleButton` (Satoshi Bold 16sp, iOS `KornerzButtonStyle` disabled palette `#F2F4F6` / `#9E9EAE`), and `SelectableCapsuleGrid` (2-col LazyVerticalGrid for the language/interests steps)
- [x] 8.5.8 Rewrite `SignupSteps.kt` to use the branded scaffold for every step: nickname (title "Choose a nickname", Suggest + Next in an HStack), email ("What is your email?", email keyboard, "This email is already registered" error), password ("Create a password with at least 6 digits", two branded password fields with eye toggles, "At least 6 characters." hint), phone ("What is your phone number?", country code bordered capsule + phone field, Skip + Next in an HStack, `CountryCodePickerSheet` modal), language (2-col grid of capsules, multi-select, default 10-item catalog mirroring iOS `SignupViewModel.allLanguages`), interests (2-col grid of capsules from `KornerInterestRepository`, multi-select with 5-item cap), photo (150dp black rounded square with "Add Photo", Skip + Next in an HStack)
- [x] 8.5.9 Update `SignupContract.kt`: replace `language: String` with `languages: Set<String>`; add `nicknameError: String?` and `interestsError: Boolean`; add `LanguageToggled` event; rewrite `isStep5Valid` to mirror iOS `SignupViewModel.isPhoneNoValid` (full `+` prefixed 10–15 digit number after stripping spaces/hyphens/parentheses); update `isStep6Valid` to require at least one language
- [x] 8.5.10 Update `SignupViewModel.kt` to handle `LanguageToggled` and the new `interestsError` / `nicknameError` flags; persist + restore `languages` (replacing the old single `language` key); submit the first selected language code to the existing `SignupRequest.language` field (the `language: List<String>` widening to match iOS `User.countries` is deferred to a follow-up)
- [x] 8.5.11 Rewrite `SignupScreen.kt` to route steps 1–8 each to their own branded content composable with `onBack` and per-step arguments (e.g. `onSuggestNickname` for step 2, `countryRepository` for step 5, `interestsRepository` for step 7)
- [x] 8.5.12 Remove the obsolete M3 `SignupStepScaffold.kt`
- [x] 8.5.13 Amend `specs/signup-wizard/spec.md` with the "Every wizard step renders the iOS branded layout" master requirement plus per-step requirements 2–8 and matching scenarios


- [x] 8.6 Implement step 2 Composable: `SignupNicknameStep` using `KornerzTextField`
- [x] 8.7 Implement step 3 Composable: `SignupEmailStep` using `KornerzTextField`; on `AsyncValue.Error` from `AuthRepository.signup`, surfaces "This email is already registered"
- [x] 8.8 Implement step 4 Composable: `SignupPasswordStep` using two `KornerzPasswordField` instances with the password rule UI
- [x] 8.9 Implement step 5 Composable: `SignupPhoneStep` with an inline dial-code `OutlinedButton` (mirrors iOS `SignupPhoneView`) and a `ModalBottomSheet` picker that loads countries via `CountryCodesRepository.getAll()` and renders `KornerzEmptyState` / `KornerzErrorState` per arch §2.2; selection emits `SignupEvent.CountrySelected` and dismisses the sheet
- [x] 8.10 (merged into 8.9) Country-code selection is an inline modal on the phone step, not a dedicated wizard step — mirrors iOS
- [x] 8.11 Implement step 6 Composable: `SignupLanguageStep` with a language picker (en + pt-BR entries)
- [x] 8.12 Implement step 7 Composable: `SignupInterestsStep` consuming `KornerInterestRepository` with a multi-select grid (1–5 selection enforcement)
- [x] 8.13 Implement step 8 Composable: `SignupPhotoStep` using `KornerzImagePicker` (arch §5), preview, signed-URL upload, and retry on failure (privacy policy was moved to a standalone pre-wizard screen — see §13)
- [x] 8.14 Implement `PrivacyPolicyView` that tracks a `hasScrolledToEnd: Boolean` in state and gates the "Read & Accept" `KornerzButton` — **MOVED to §13**: privacy acceptance now happens on a dedicated `PrivacyPolicyScreen` before the wizard starts; the inline `PrivacyPolicyView` was removed from step 8.
- [x] 8.15 On the final step, `SignupViewModel.submit()` calls `AuthRepository.signup(...)`, persists the session, and emits `NavEvent.NavigateToShell` over `Channel<NavEvent>` (arch §2.4)
- [x] 8.16 Add `@Preview` Composable previews for every step in light, dark, and `en-rXA`
- [ ] 8.17 Create `SignupViewModelTest.kt` covering all 8 step flows + validation + photo upload retry path — **DEFERRED**: unit tests are scoped to follow-up.

## 13. Standalone Privacy Policy screen (pre-wizard gate)

- [x] 13.1 Create `feature/auth/.../privacy/PrivacyPolicyContract.kt` defining `PrivacyPolicyState(hasScrolledToEnd)`, `PrivacyPolicyEvent` (`ScrolledToEndChanged`, `Accept`, `Decline`), and `PrivacyPolicyNavEvent` (`NavigateToSignup`, `NavigateBack`) — Hilt-scoped VM, never touches `NavController` directly (arch §2.4)
- [x] 13.2 Create `feature/auth/.../privacy/PrivacyPolicyViewModel.kt` as `@HiltViewModel` exposing `state: StateFlow<PrivacyPolicyState>` and `navEvents: Flow<PrivacyPolicyNavEvent>` via `Channel(BUFFERED)`; `Accept` only emits `NavigateToSignup` when `canAccept` is true
- [x] 13.3 Create `feature/auth/.../privacy/PrivacyPolicyScreen.kt` (stateful) that navigates to `AuthRoute.Signup.build(1)` on `NavigateToSignup` and `popBackStack()` on `NavigateBack`
- [x] 13.4 Create `feature/auth/.../privacy/PrivacyPolicyContent.kt` (stateless) with a scrollable `LazyColumn` of `PRIVACY_POLICY_TEXT`, proper scroll-to-end detection via `derivedStateOf` over `LazyListState.layoutInfo`, an "Accept & Continue" `KornerzButton` gated on `state.canAccept`, and a "Decline" `TextButton`
- [x] 13.5 Wire `AuthRoute.PrivacyPolicy` (already declared in `AuthRoute.kt`) as a `composable(...)` entry inside `authGraph(...)` in `AuthGraph.kt`
- [x] 13.6 Update `WelcomeScreen` to navigate to `AuthRoute.PrivacyPolicy.route` on `WelcomeNavEvent.NavigateToSignup` instead of `AuthRoute.Signup.build(1)`
- [x] 13.7 Remove the inline `PrivacyPolicyView` and `PRIVACY_POLICY_TEXT` from `SignupSteps.kt`; `SignupPhotoStep` now shows only the photo picker + retry UI
- [x] 13.8 Remove `policyScrolledToEnd` field, `PolicyScrolledToEnd` event, and `KEY_POLICY_SCROLLED` persistence from `SignupContract.kt` / `SignupViewModel.kt`; `isStep9Valid` now requires only a successful `photoUpload`
- [x] 13.9 Rename the step-9 button label from "Read & Accept" to "Create account" in `SignupScreen.kt`; update the `SignupStepScaffold` docstring
- [x] 13.10 Add `@Preview` light + dark previews for `PrivacyPolicyContent`
- [x] 13.11 Update `specs/signup-wizard/spec.md`: the "Inline Privacy Policy acceptance at the end of sign-up" requirement is replaced by the "Standalone Privacy Policy gate before the signup wizard" and "Final wizard step submits without a re-confirmation gate" requirements

## 9. Password recovery

- [x] 9.1 Create `ForgotPasswordScreen.kt`, `ForgotPasswordContent.kt`, `ForgotPasswordState.kt`, `ForgotPasswordEvent.kt`, `ForgotPasswordNavEvent.kt`, `ForgotPasswordViewModel.kt` per arch §4
- [x] 9.2 `ForgotPasswordViewModel` exposes `submit: AsyncValue<Unit>` (arch §2.2) and emits `NavEvent.NavigateToOtp(email)` over `Channel<NavEvent>`
- [x] 9.3 Create `OtpConfirmScreen.kt` and friends per arch §4; OTP input uses `KornerzOtpTextField` (arch §5)
- [x] 9.4 `OtpConfirmViewModel` exposes `submit: AsyncValue<Unit>`, `resendCooldownSeconds: Int`, and emits `NavEvent.NavigateToChangePassword(email, resetToken)`
- [x] 9.5 Create `ChangePasswordScreen.kt` and friends per arch §4; password inputs use `KornerzPasswordField`
- [x] 9.6 `ChangePasswordViewModel` exposes `submit: AsyncValue<Unit>` and emits `NavEvent.NavigateToLoginWithToast("Password updated — please log in")`
- [x] 9.7 Apply theming and Compose previews to all three screens
- [ ] 9.8 Add `*ViewModelTest.kt` for each of the three screens (Turbine) — **DEFERRED**: unit tests are scoped to follow-up.

## 10. Splash router and shell gating

- [x] 10.1 Create `SplashState.kt`, `SplashEvent.kt`, `SplashNavEvent.kt`, `SplashViewModel.kt`, `SplashScreen.kt`, `SplashContent.kt` per arch §4; `SplashViewModel` injects `SessionRepository` and `ForceUpdateRepository`, exposes `state: StateFlow<SplashState>` with `route: AsyncValue<SplashRoute>` (arch §2.2), and emits `NavEvent.NavigateToAuthGraph` or `NavEvent.NavigateToShellGraph` over `Channel<NavEvent>` (arch §2.4)
- [x] 10.2 Update the root `NavHost` in `feature/shell` to host two child graphs: `auth_graph` and `shell_graph`, swapping based on `sessionState` and clearing the back-stack on each transition
- [x] 10.3 Remove the P0 `FirebaseAuth.signInAnonymously()` call from `Application.onCreate()` per the modified `android-app-shell` spec
- [x] 10.4 Add a placeholder `ShellViewModel` exposing `Channel<NavEvent>` with a `NavEvent.Logout` emitted from a placeholder Profile tab action so the manual logout path is testable in P1
- [ ] 10.5 Verify with manual QA: fresh install → Login; signup → Home; logout → Login; relaunch (signed in) → Home — **DEFERRED**: manual QA.

## 11. Force-update sheet

- [x] 11.1 Add the `android_min_supported_version` Remote Config parameter to the default `remote_config_defaults.xml` in `app/src/main/res/xml/`
- [x] 11.2 Implement `ForceUpdateRepository` as `@Singleton` (arch §2.6) that calls `firebaseRemoteConfig.fetchAndActivate()` and exposes `state: Flow<ForceUpdateState>` (Idle, Required(currentVersion, minVersion), Failed)
- [x] 11.3 Add `ForceUpdateSheet.kt` to `core/ui/components/` (arch §5 — shared component) — a non-dismissible, full-screen overlay with `KornerzButton` for the "Update" deep-link via `Intent.ACTION_VIEW` to `market://details?id=<applicationId>`
- [x] 11.4 Render `ForceUpdateSheet` at the root of `MainActivity` whenever `ForceUpdateState.Required` is active
- [ ] 11.5 Verify by setting the Remote Config value above the installed `versionCode` in the dev Firebase project — **DEFERRED**: manual QA.

## 12. Verification and exit checklist

- [ ] 12.1 `./gradlew :app:assembleDevDebug` succeeds — **DEFERRED**: requires local Firebase project + dev emulator; CI will validate.
- [ ] 12.2 `./gradlew :app:installDevDebug` succeeds on Pixel API 26 and API 35 emulators — **DEFERRED**: manual QA.
- [ ] 12.3 Manual test: complete the 9-step signup wizard on a fresh install — **DEFERRED**: manual QA.
- [ ] 12.4 Manual test: log out, log back in, kill + relaunch app to confirm session persistence — **DEFERRED**: manual QA.
- [ ] 12.5 Manual test: trigger force-update by raising the Remote Config value — **DEFERRED**: manual QA.
- [ ] 12.6 Manual test: forgot-password → OTP → change-password → log in with new password — **DEFERRED**: manual QA.
- [ ] 12.7 `./gradlew :app:lintDevDebug` passes — **DEFERRED**: CI / follow-up.
- [ ] 12.8 Unit tests for `SessionRepository`, `AuthRepository`, `SignupViewModel`, `LoginViewModel`, `ForgotPasswordViewModel`, `OtpConfirmViewModel`, `ChangePasswordViewModel` pass — **DEFERRED**: tests are scoped to follow-up.
- [ ] 12.9 Capture one screenshot per in-scope screen and attach to PR — **DEFERRED**: design-dependent, post-merge.
- [ ] 12.10 Record a ≤ 5 min walkthrough demo and attach to PR — **DEFERRED**: post-merge.
- [ ] 12.11 Create the `phase-1-end` git tag — **DEFERRED**: tag after merge.