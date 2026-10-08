## Context

Phase 0 delivered a working Android app shell: branded theme, Satoshi font, design tokens, 5-tab navigation, and Firebase init — but no auth. Every screen behind authentication is unreachable. The iOS app already implements the full auth surface (custom-auth Cloud Functions for sign-up / login / OTP / password reset, a session store, and a force-update sheet driven by Remote Config). Phase 1 ports the equivalent surface to Android so that a freshly installed user can sign up, log in, recover a forgotten password, and reach the (still empty) Home tab. The 5-tab shell stays empty for non-Home tabs; P2–P10 populate them.

**Architecture compliance.** This change conforms to [`docs/architecture.md`](../../../docs/architecture.md) — the canonical source of patterns and conventions frozen by P0. Specifically:
  - **MVVM + Repository + UDF** (arch §1, §2.1): every new screen has a `<Feature>State`, `<Feature>Event`, `<Feature>ViewModel`, `<Feature>Screen`, `<Feature>Content` per the §4 folder layout.
  - **`AsyncValue<T>` sealed interface** (arch §2.2) replaces any `isLoading` / `error: String?` booleans; Composables render `Loading` / `Success` / `Error` exhaustively.
  - **`Channel<NavEvent>` side-channel** (arch §2.4): ViewModels never call `NavController` directly.
  - **Hilt scopes** (arch §2.6): repositories and the FCM registrar are `@Singleton`; ViewModels are Hilt-managed.
  - **Module boundaries** (arch §2.5): `feature/auth` is imported only by `app`; `core/*` modules are imported by `feature/*` and `app`.
  - **Shared `core/ui/components/`** (arch §5): every auth screen uses `KornerzTextField`, `KornerzPasswordField`, `KornerzOtpTextField`, `KornerzButton`, `KornerzImagePicker`, `KornerzLoadingView`, `KornerzErrorState` instead of reinventing them.
  - **Canonical library list** (arch §3): no new dependencies introduced — only versions already pinned in `gradle/libs.versions.toml`.
  - **Clean-code rules** (arch §7): no business logic in Composables, no `var` in ViewModels, no `!!`, no magic numbers (spacing/colors via tokens), no `companion object` constants.
  - **Convention plugin** (arch §9): the new `feature/auth` module applies `kornerz.android.feature.compose.hilt`.

## Goals / Non-Goals

**Goals:**
- Provide a complete sign-up, login, and password-recovery flow that is feature-equivalent with iOS for the 16 screens in P1 scope.
- Persist session (auth token + current user) across launches using DataStore.
- Route the splash screen and tab host based on session state on cold start and on session changes (logout, token expiry).
- Register the FCM device token against the signed-in user so downstream phases (P6) can deliver notifications without re-plumbing.
- Apply the existing design tokens, Satoshi font, dark/light themes, and RTL-safe layouts to every new screen.

**Non-Goals:**
- Native SSO (Sign in with Google, Apple) — not used by iOS app's current custom-auth path.
- Email-link / magic-link sign-in.
- Biometric re-auth (deferred).
- Account deletion UI (lands in P6 per phases.md).
- Push permission prompt UI (Android 13+ `POST_NOTIFICATIONS` — lands in P6 when notifications UI ships).
- Subscription / premium gating (P8).
- Home, Loops, Kornerz, Nooks, Profile content (P2–P6).
- Localization beyond English (per `project-brief.md` §11.7); layouts are RTL-safe but copy is en-only.

## Decisions

### 1. Reuse the iOS custom-auth Cloud Functions as the auth backend
The iOS app already calls a set of custom-auth Cloud Functions in the shared `kornerz-72fba` Firebase project (sign-up, login, request password-reset OTP, confirm OTP, change password). Android calls the same functions over HTTPS using the Firebase Functions client. **Why:** zero new backend work, identical security rules, identical user records. **Alternatives:** Firebase Auth with email/password — rejected because iOS does not use it and the user schema in Firestore is shaped by the custom-auth functions.

### 2. DataStore (Preferences) for session storage, exposed as a `Flow<SessionState>`
Session token + `User` JSON are written to a single Preferences DataStore file in `core/storage/session/`. `SessionRepository` is `@Singleton`-scoped (arch §2.6) and exposes `data(): Flow<SessionState>` (Anonymous, Authenticated(user), Loading) — a `Flow`, not a `StateFlow`, because the consumer (the root NavHost) only needs to react to changes, not the latest value alone (arch §2.3 — `Flow<T>` for streams). **Why:** DataStore is the AndroidX recommendation, gives us a flow out of the box, and avoids hand-rolled `SharedPreferences` listeners. **Alternatives:** EncryptedSharedPreferences — rejected for P1 because the iOS app does not encrypt session storage at rest; can be layered in later. Room — overkill for two keys.

### 3. Two top-level navigation graphs: auth graph and shell graph
The `app` module owns a single `NavHost` that switches between two child graphs based on `SessionState`:
- `auth_graph` (Login, Signup wizard steps 1–9, Privacy Policy, Forgot Password, OTP Confirm, Change Password)
- `shell_graph` (the existing P0 5-tab bottom-nav host with empty placeholders)

The splash screen itself becomes a Composable that observes `sessionState` and emits a `NavEvent.NavigateToLogin` or `NavEvent.NavigateToShell` through a `SplashViewModel` `Channel<NavEvent>` (arch §2.4); the Screen collector then calls `navController.navigate(...)` and clears the back-stack with `popUpTo(Splash) { inclusive = true }`. **Why:** Compose Navigation handles back-stack resets cleanly across cold start and logout. **Alternatives:** Single graph with conditional composables — rejected because back-stack management for a 9-step wizard plus OTP flow gets messy.

### 4. Signup wizard modeled as a single `SignupViewModel` with a `WizardState` data class
A single `@HiltViewModel`-scoped `SignupViewModel` holds a `SignupWizardState` (full name, nickname, email, password, phone, country code, language, selected interest Korner slugs, photo URI, current step index, validation errors per step). Each step Composable reads/writes via `collectAsStateWithLifecycle()` and step navigation goes through `Channel<NavEvent>` on the ViewModel (arch §2.4). **Why:** keeps cross-step validation (e.g., "email already in use" surfaced when re-entering step 3) and avoids re-fetching data when the user backs up a step. **Alternatives:** one ViewModel per step — rejected because it forces passing state through savedStateHandle, and the iOS reference uses a single coordinator.

### 5. Country codes as a bundled JSON resource, parsed at startup
`CountryCodes` is shipped as `core/model/src/main/assets/country_codes.json` (ISO-3166-1 alpha-2, dial code, English name, flag emoji) parsed into an immutable list on first access and exposed via `suspend fun CountryCodesRepository.getAll(): Result<List<Country>>` (arch §2.3 — `suspend fun ... Result<T>` for one-shot reads). **Why:** matches iOS's bundled list and avoids a network call. **Alternatives:** libphonenumber — overkill; we don't validate numbers ourselves in P1, only collect them.

### 6. Force-update sheet driven by Firebase Remote Config
A single Remote Config key `android_min_supported_version` is read on splash (after `fetchAndActivate()`) and, when the installed `versionCode` is below the configured value, the root composable overlays a non-dismissible sheet (composed from `KornerzButton` and a shared `ForceUpdateSheet` composable in `core/ui/components/`) that links to the Play Store. **Why:** mirrors the iOS behavior with the same Firebase project. **Alternatives:** in-app version JSON fetch — rejected, Remote Config is already wired into the iOS flow.

### 7. Photo upload during signup uses the existing Firebase Storage v4 signed-URL path
The signup wizard's photo step uploads the selected image via the shared `KornerzImagePicker` Composable (arch §5) and the same signed-URL flow used by iOS (`FirebaseStorageService` in the iOS source). The Android client obtains a signed PUT URL from a Cloud Function and uploads with OkHttp. `SignupWizardState.photoUpload: AsyncValue<PhotoUrl>` follows arch §2.2 (`Loading` / `Success` / `Error`). **Why:** consistency with iOS, no new storage rules. **Alternatives:** direct `FirebaseStorage.putFile` — rejected because iOS does not use it and the bucket rules may not allow direct client writes.

### 8. FCM token registration without UI
On every successful login and on every `Application.onCreate`, a `@Singleton`-scoped `FcmTokenRegistrar` (a Hilt-injected initializer; arch §2.6) collects `FirebaseMessaging.getInstance().token` (a `Flow`), then writes the token to the user document via `suspend fun UserRepository.updateFcmToken(token: String): Result<Unit>` (arch §2.3). No POST_NOTIFICATIONS prompt is shown in P1 — that lands in P6 when notifications UI ships. **Why:** keeps Phase 1 scope tight while unblocking P6.

### 9. Hilt for DI, kotlinx-serialization for `User` JSON
`User`, `Country`, `CountryCodes`, `KornerInterest`, and every Cloud Function DTO are `@Serializable` from `kotlinx.serialization` (arch §3 — canonical library list) for the DataStore serializer and for Cloud Function payloads. Hilt provides `SessionRepository`, `AuthRepository`, `AuthApi`, `UserRepository`, `FcmTokenRegistrar`, `CountryCodesRepository`, `KornerInterestRepository`, and all auth ViewModels. **Why:** consistent with P0 conventions, no reflection.

## Risks / Trade-offs

- [Custom-auth Cloud Function contract drift] → Mitigation: the first task in P1 is a contract-review subtask that calls each function from a scratch Kotlin script and pins the request/response shapes in `core/network/auth/AuthApi.kt`. Any mismatch becomes a follow-up issue.
- [Wizard state loss on process death] → Mitigation: `SignupWizardState` is persisted into `SavedStateHandle` (small fields) and the photo URI is persisted into a `MediaStore`-tracked URI grant; the photo re-upload only happens if the URI is still valid.
- [Force-update Remote Config fetch delay] → Mitigation: `fetchAndActivate()` is awaited before the splash Composable decides to navigate; if it times out, we fall back to allowing entry (fail-open) and log a warning.
- [Token refresh not yet implemented] → The custom-auth token does not refresh itself; expiry is handled by forcing re-login. Mitigation: a global `AuthInterceptor` returns a "session expired" result that the splash reacts to. A follow-up adds refresh tokens.
- [RTL regressions in 16 new screens] → Mitigation: every new Composable is previewed with the `en-rXA` pseudo-locale in addition to `en` and `pt-RBR` (sanity check that bidi attributes are set even though copy is en-only).
- [DataStore migration path] → If P6+ needs richer session data, schema migration is required. Mitigation: keep the serializer in one file (`SessionSerializer.kt`) so future migrations are localized.

## Migration Plan

P0 ships already; P1 is additive — no destructive changes.

1. Merge `feature/auth`, `core/model`/`core/network`/`core/storage` additions, and the splash/shell router updates behind the existing CI gate.
2. Build `assembleDevDebug`, install on Pixel API 26 and API 35 emulators; verify:
   - Fresh install lands on Login.
   - Complete signup wizard → lands on Home tab (empty).
   - Log out → returns to Login.
   - Log back in → Home tab.
   - Kill app, relaunch → goes straight to Home tab.
   - Set `android_min_supported_version` Remote Config above the installed `versionCode` → force-update sheet overlays.
3. Tag `phase-1-end`.
4. Rollback: revert the merge commit (P0 still builds and the splash routes to tabs as before — no session-gated code path existed in P0).