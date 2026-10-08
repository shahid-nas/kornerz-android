## Why

> **See also:** [`docs/architecture.md`](../../../docs/architecture.md) for module layout, navigation, DI, and data-layer conventions this change conforms to.

Phase 0 shipped a branded 5-tab shell with empty placeholders. The Android app cannot yet acquire real users: every screen behind authentication (Home feed, Loops, Profile, etc.) is unreachable. Phase 1 closes that gap by implementing the full auth + onboarding flow so a fresh install can sign up, log in, recover a forgotten password, and arrive at the (still empty) Home tab — producing a demoable "Identity" milestone and unblocking every downstream phase that depends on an authenticated session.

## What Changes

- Add `core/model` types `User` (id, email, display name, nickname, phone, country, language, photo URL, isPremium, subscription fields, createdAt) and `CountryCodes`.
- Add a `core/network` client that signs users up, logs them in, issues password-reset codes, and confirms them by calling the existing custom-auth Cloud Functions used by the iOS app.
- Add a `core/storage` DataStore-backed session store that persists the current `User` and auth tokens across launches and exposes a flow of session state.
- Add `feature/auth` with a 9-step signup wizard (full name → nickname → email → password → phone → country code → language → Kornerz interests → photo), a Login screen, Forgot Password + OTP confirm + Change Password screens, and a Privacy Policy inline view.
- Update `feature/splash` so that on cold start it routes unauthenticated users to Login and authenticated users into the tab shell (replacing the P0 always-tabs router).
- Update `feature/shell` so the tab nav host gates on session state: signed-out users see the auth graph; signed-in users see the 5-tab shell (still empty P0 placeholders for non-Home tabs).
- Add FCM token registration plumbing (token fetched on login, uploaded to user profile, refreshed on app start; no system-prompt UI yet).
- Ensure custom Satoshi font loading and RTL-safe layouts are applied to every new screen.

## Capabilities

### New Capabilities

- `user-auth`: sign-up, login, logout, session persistence, force-update sheet from Remote Config, and the auth/router that gates the tab shell.
- `signup-wizard`: 9-step onboarding wizard collecting full name, nickname, email, password, phone, language, interests, and photo plus the inline Privacy Policy acceptance view.
- `password-recovery`: forgot-password request, OTP confirmation, and change-password screens backed by the custom-auth Cloud Function OTP flow.
- `fcm-registration`: silent registration of the FCM token against the signed-in user, with refresh on app start, no user-facing UI yet.
- `session-storage`: DataStore-backed storage of auth tokens and current user with a `StateFlow<SessionState>` exposed to the rest of the app.

### Modified Capabilities

- `android-app-shell`: the splash router and main `NavHost` must consult session state on cold start and route unauthenticated users into `feature/auth` instead of the tab shell. The 5 tabs themselves are unchanged (still P0 empty placeholders for non-Home tabs).

## Impact

- New modules: `feature/auth`, `feature/signup` (or merged under `feature/auth`), `feature/password-recovery` (or merged under `feature/auth`).
- New core code in `core/model`, `core/network`, `core/storage`.
- Touched: `feature/splash`, `feature/shell` (router only), `app` module navigation graph, DI graph in `app` and `core/di` (new Hilt modules for session, auth client, FCM).
- Dependencies: Google Play Services Auth (for ID-token retrieval if used by custom-auth), Firebase Cloud Messaging (token API), AndroidX DataStore (preferences), Coil (already in P0) for signup photo upload preview.
- Backend: reuses the existing Firebase `kornerz-72fba` custom-auth Cloud Functions — no new backend work; if any function is missing the equivalent Android client surface, a follow-up issue is filed (does not block Phase 1 scope).
- Localization: English only this phase, mirroring `project-brief.md` §11.7; RTL-safe layouts verified via pseudo-locale.
- Out of scope (deferred): Apple/Google native SSO, email-link sign-in, biometric re-auth, in-app notification settings UI, push-permission prompt (Android 13+), account-deletion UI (lands in P6).