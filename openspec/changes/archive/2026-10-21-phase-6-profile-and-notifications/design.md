## Context

Phases P0–P5 shipped a populated Home, Kornerz, Nooks, and Loops tab on the shared Firebase backend (`kornerz-72fba`), plus the full signup / login / session / force-update / P3-P5 detail flows. The fifth tab in the bottom navigation bar (`Profile`) is still the P0 "Coming in Phase 6" empty-state placeholder, and the account-management flows every signed-in user needs (view own profile, edit name/nickName, read privacy policy, see notifications, receive push) have no Android surface yet. Phase 6 closes that gap by porting the iOS Profile & Notifications vertical onto Compose.

iOS (`kornerz/Kornerz/HomeVC/ProfileVC/`, `kornerz/Kornerz/HomeVC/Notifications/`, `kornerz/Kornerz/Backend/Repositories/NotificationRepository.swift`, `kornerz/Kornerz/Backend/Repositories/UserProfileRepository.swift`) is the source of truth for behavior, field names, sort orders, pagination, and interactions. The architectural baseline from P5 is unchanged: MVVM + Repository + UDF, `AsyncValue<T>` per section, `Channel<NavEvent>` for navigation, Hilt scopes (`@HiltViewModel` for VMs, `@Singleton` for repositories), `SavedStateHandle` for nav-arg VMs, `core/model` for `@Serializable` data, Coil for images, and the standard `feature/*` Gradle scaffolding.

The new additions are two feature modules (`feature/profile`, `feature/notifications`) hosting seven destinations in total (`profile/view`, `profile/edit-basic-info`, `profile/edit-basic-info/{field}`, `profile/edit-basic-info/{field}/success`, `profile/privacy-policy`, `notifications/list`, plus the bottom-nav shell wiring). One third-party library arrives in this phase: **android-pdf-viewer 3.2.0-beta.1** — arch §3 does not include a PDF renderer; this proposal is the amendment that adds it because iOS uses `PDFKit` natively and the privacy policy PDF must render identically across platforms. The canonical Firebase Cloud Messaging KTX dependency was already pinned in P1's `fcm-registration` spec; this phase extends it with the push-display half (channel, `KornerzFcmService`, Android 13+ `POST_NOTIFICATIONS` flow).

## Goals / Non-Goals

**Goals:**

- Port the Profile bottom-nav tab root (7.1) — header + menu rows — to Compose with the iOS `UserInfoCell` shape (gradient avatar + name + nickName + two pill count rows).
- Implement `EditBasicInfoScreen` (7.2) as a read-only list of name / email / phone / nickName / password, where only name and nickName expose the "Change" affordance (per iOS `EditBasicInfoVC.swift` line 118–120).
- Implement `ChangeBasicInfoScreen` (7.3) as a centered modal sheet with the iOS shape: rounded 20-pt card, heading + input + Save button. Save is disabled when `draftValue.isEmpty() || draftValue == originalValue`. Save calls `UserProfileRepository.updateName` or `.updateNickName` (which checks uniqueness first) and emits a `success` event.
- Implement `ChangeBasicInfoSuccessScreen` (7.4) as a small confirmation modal after a successful save, mirroring iOS `ChangeBasicInfoSuccessVC`.
- Implement `PrivacyPolicyScreen` (7.5) as a `PDFView` host with a top app bar and a hidden-by-default accept button (visible only when opened from the onboarding flow — from Profile it's hidden per iOS `PrivacyPolicyVC.swift` line 42–44).
- Implement `NotificationsScreen` (8.1 + 8.2) — a `LazyColumn` of `NotificationItem` cards, paginated by `NotificationRepository.fetchNotifications(userCode, after)`, with the three render states (shimmer / retry / no-data) and a per-page-cursor pagination trigger.
- Add `UserProfileRepository` + `NotificationRepository` with the iOS Firestore paths / queries / sort orders / pagination semantics preserved verbatim.
- Add `User` extensions (`imageURL`, `thumbnailURL`, `thumbnailType`) and `UserNotification` (with `deeplink(with:)`) to `core/model`.
- Add `KornerzFcmService` extending `FirebaseMessagingService`, the `kornerz_notifications` channel, and the Android 13+ `POST_NOTIFICATIONS` runtime-permission flow.
- Add account-deletion plumbing (`UserProfileRepository.deleteAccount()`, `AccountDeletionViewModel`) ready for P10 to wire the menu row.
- Pass the per-phase exit checklist (assembleDevDebug, installDevDebug on API 26 + API 35 emulators, no crashes, lint, unit tests, screenshots, demo walkthrough, `phase-6-end` tag).

**Non-Goals:**

- Create Community & Admin (P7), Premium / IAP (P8), Video Calling — Vonage / WebRTC (P9), Ranking + Deep Links + Polish (P10).
- The account-deletion menu row UI (the iOS row + `UserProfileVC.deleteAccount()` confirm dialog presentation; the repository + ViewModel API ship ready for P10 to wire).
- The chip filter on the notifications list (`.all` vs `.communities` — iOS declares the type but the active flow never surfaces the chip per `screen-inventory.md` §8; Android mirrors the no-firmware surface).
- The `Calls` count on the profile header (iOS hardcodes `"0"`; Android mirrors — wire in a follow-up).
- The `.newEvent`, `.newArticle`, `.newForum`, `.videoCall` deeplink arms (P10 deep links + Polish).
- Tap-to-deep-link from a system tray notification (P10).
- pt-BR localization of the privacy policy PDF (follow-up per `project-brief.md` §11.7).
- Logout menu row (P10 item per stakeholder review 2026-10-07).
- Local `notifications` Room cache for offline read (iOS does not persist; Android mirrors the no-firmware surface; a follow-up can add Room).
- Profile image cropping beyond the `maxDimension = 200`-pt thumbnail convention iOS uses (line 81 of `UserProfileRepository.swift`).
- Cover photo / banner upload (iOS only exposes the avatar; Android mirrors).
- `notificationOpened` `newComment` / `loopComment` route destinations — the Android port routes these to a `toast("Coming soon")` placeholder until P10 deep links ship (the underlying `notification.deeplinkType` / `.reference` plumbing is in place so P10 wires the routes in one follow-up).

## Decisions

### Decision: Two feature modules with their own nested NavHost

`feature/profile` hosts the Profile bottom-nav tab and its nested destinations; `feature/notifications` is a single-screen feature that mounts at the app-level NavHost. This mirrors the per-tab boundary established in P2/P3/P5 and keeps `feature/notifications` reusable from any future entry point (e.g., a P10 deep link from a system tray notification).

**Alternatives considered:**
- One combined `feature/account` module hosting both Profile and Notifications. Rejected: Notifications has a distinct lifecycle (pushed as a single route from Profile; future push-handler hooks), and the iOS source already separates them (`HomeVC/ProfileVC/` vs `HomeVC/Notifications/`).
- Notifications as a nested route inside the Profile tab's NavHost. Rejected: iOS pushes `NotificationsController` onto the global nav stack (line 253 of `UserProfileVC.swift`); mirroring that means a top-level route.

### Decision: `UserProfileRepository.observeCurrentUser(): Flow<User?>` mirrors the iOS `AppHelper.shared.user` + `.profileUpdatedNotification` pattern

iOS has no Firestore listener for the current user. After every mutation (`updateNameWith`, `updateNickNameWith`, `uploadUserProfileImage`), iOS writes `AppHelper.shared.user = updatedUser` and posts `.profileUpdatedNotification` — the receiving VC re-reads from the singleton. The Android port makes this explicit: the repository returns a `Flow<User?>` that emits the current cached `User` (read from DataStore on first collect) and re-emits on every mutation by writing the updated `User` back to DataStore and emitting through a `MutableStateFlow` held inside the repository. This satisfies arch §2.3's "Flow for streams" rule.

**Alternatives considered:**
- Add a Firestore listener on `Users/{userCode}` so external updates (e.g., from a server-side Cloud Function triggered by `onUpdate`) propagate. Rejected: iOS does not do this; the iOS data is single-source-of-truth (local) and the demo flow does not require cross-device sync. A follow-up can add the Firestore listener when the iOS app does.

### Decision: `UserProfileRepository.updateNickName` enforces uniqueness on the client before writing

Mirrors iOS `ChangeBasicInfoViewModel.updateNickNameWith` (lines 29–56): first calls `checkIfNickNameExists(nickName)`, if `true` returns `Result.failure(NicknameTaken(localizedKey = "nick_name_not_available"))`; if `false` writes the new nickname via `setData(user, merge: true)` and returns `Result.success(updatedUser)`. The lookup is `Users.whereField("nickname", isEqualTo: nickName).getDocuments()` exactly as iOS does (line 32 of `FireStoreServiceManager.swift`).

**Alternatives considered:**
- Use a Cloud Function `onCall("checkNickNameAvailability")` for the lookup so the client never reads the full `Users` collection. Rejected: iOS does the client-side lookup and the demo flow tolerates a brief full-collection read; matching iOS is the source-of-truth rule.
- Skip the uniqueness check entirely and rely on Firestore security rules to reject duplicate nicknames. Rejected: iOS surfaces a friendly "Nickname already taken" error; the Android port mirrors that UX, so a client-side check that returns a localized error key is required.

### Decision: `NotificationRepository.fetchNotifications` paginates via Firestore `startAfter(lastSnapshot)` cursor

Mirrors iOS `NotificationRepository.swift` lines 40–69: `Notifications.whereField("notificationUserId", isEqualTo: userCode).order(by: "dateNotification", descending: true).limit(10)` then `start(afterDocument: latestPost)`. The Android port uses Firestore KTX's `Query.startAfter(snapshot)` extension. `queryLimit = 10` matches iOS. `fetchNotificationsForCommunities` mirrors lines 71–101 verbatim.

**Alternatives considered:**
- Use a `Paging 3` `Pager` for the feed. Rejected: iOS uses a manual cursor and the same `NotificationsViewModel.fetchNextPage()` pattern; Paging 3 would be over-engineered for a 10-item page and would diverge from the iOS source-of-truth pattern.
- `Flow`-based real-time listener instead of one-shot `getDocuments`. Rejected: iOS uses `getDocuments` (one-shot); a listener would diverge from the source-of-truth rule and would require a local cache that the iOS app does not have.

### Decision: `ChangeBasicInfoScreen` uses a Navigation-Compose `dialog` route (centered modal) — not `ModalBottomSheet`

Mirrors iOS `ChangeBasicInfoVC.modalPresentationStyle = .overFullScreen` (line 140 of `EditBasicInfoVC.swift`) and the centered-card-with-rounded-20-pt-corners layout (line 49 of `EditBasicInfoVC.xib` / lines 62–64 of `ChangeBasicInfoVC.swift`). A `Dialog` route in Navigation-Compose gives the same visual shape (rounded card over a translucent scrim) with less custom chrome than `ModalBottomSheet`.

**Alternatives considered:**
- `ModalBottomSheet` with a custom `SheetState.Expanded`. Rejected: the iOS sheet is centered, not bottom-aligned; matching the visual is the source-of-truth rule.
- `AlertDialog` with `Material 3` shape. Rejected: `AlertDialog` is for short messages, not a card with an input field.

### Decision: `PrivacyPolicyScreen` uses `com.github.barteksc:android-pdf-viewer:3.2.0-beta.1` wrapped in `AndroidView`

This is the new library amendment. iOS uses `PDFKit.PDFView` natively (lines 49–80 of `PrivacyPolicyVC.swift`); the de facto Android equivalent is `android-pdf-viewer` which renders the same PDF the iOS app ships in `kornerz/Kornerz/HomeVC/ProfileVC/PrivacyPolicyVC/Privacy-Police-en.pdf`. The Android port bundles `assets/privacy/privacy_en.pdf` and loads it via `PDFView.fromAsset("privacy/privacy_en.pdf").load()`. `autoScales = true`, `minScaleFactor = scaleFactorForSizeToFit`, `maxScaleFactor = 5f` — matches iOS lines 72–74.

**Alternatives considered:**
- `PdfRenderer` (Android system class). Rejected: it requires manual page rendering onto a `Bitmap` and scrolling logic; iOS uses `PDFKit`'s built-in scrolling/zooming and the Android port should match.
- WebView with `mozilla/pdf.js`. Rejected: heavier dependency and worse UX than `android-pdf-viewer`.
- Compose Multiplatform's `PdfRenderer`. Rejected: not stable on Android.

### Decision: `KornerzFcmService` extends `FirebaseMessagingService` and is registered in `AndroidManifest.xml`

Mirrors iOS `AppDelegate.messaging(_:didReceiveRegistrationToken:)` + the local-notification display path. The Android port:
1. `onNewToken(token)` → `UserProfileRepository.updateFcmToken(token)` which writes `Users/{userCode}.tokenPush = token` via `setData(["tokenPush": token], merge: true)` (mirrors iOS `FireStoreServiceManager.saveUserToken` lines 52–55).
2. `onMessageReceived(remoteMessage)` → for `notification` messages, builds a `NotificationCompat.Builder` with the `kornerz_notifications` channel (created at app startup), shows it via `NotificationManagerCompat.notify(notificationId, builder.build())`. For `data` messages, parses the payload into a `UserNotification` and stores it in a local `MutableStateFlow<List<UserNotification>>` held in `NotificationRepository` so the next feed refresh picks it up (deferred — iOS does not persist; the Android port keeps the no-firmware surface for now).
3. Android 13+ `POST_NOTIFICATIONS` runtime permission is requested at first session open via `requestPermissionLauncher` (Accompanist `rememberLauncherForActivityResult`).
4. Tap-to-deep-link is deferred to P10 (per `phases.md` P10 line 314). For now, the system tray notification opens the splash activity.

**Alternatives considered:**
- Use the `PushNotificationPermission` Compose pattern (a `Composable` that wraps `rememberPushNotificationPermissionState()`). Rejected: the Accompanist `Permissions` API is canonical per arch §3 ("Accompanist ... Permissions") and is the one already used elsewhere in the codebase for camera/microphone in P9.
- `WorkManager` for retrying failed FCM token writes. Rejected: iOS does not retry — the failure is silent — and matching iOS is the source-of-truth rule. A future phase can add a `OneTimeWorkRequest` for retries.

### Decision: Account-deletion plumbing ships without the menu row UI

iOS exposes the "Delete Account" row in the profile menu (`UserProfileVC.deleteAccount()` lines 152–185), but stakeholders flagged it as P10 polish per the 2026-10-07 review (`phases.md` §"Out of scope"). To avoid leaving a dead API surface, this phase ships the full `UserProfileRepository.deleteAccount()` (calls the existing `deleteUserAccount` Cloud Function) and `AccountDeletionViewModel` (emits the `userDeletedHisAccount` event so the shell routes back to auth) — but the menu row UI is held for P10.

**Alternatives considered:**
- Ship the menu row now and reuse the iOS confirmation dialog UX. Rejected: stakeholder scope decision; P10 will wire it.

### Decision: The profile menu surfaces five rows, not the iOS nine

The iOS menu has nine rows: user detail (header), notifications, complete registration, my data, review tutorial, privacy policy, my communities, logout, delete account. The Android port surfaces:
- header (always)
- notifications (always)
- my data (= edit basic info, always)
- privacy policy (always)
- my communities (conditionally, when `ownedCommunityCount > 0`)

iOS-only rows not surfaced in Android: complete registration (stakeholder-flagged dead code), review tutorial (stakeholder-flagged dead code), create community (P7 wizard — not a profile menu item), logout (P10), delete account (P10). This matches the active-flow analysis in `screen-inventory.md` §7.

**Alternatives considered:**
- Mirror all nine rows verbatim and hide the out-of-scope rows via feature flags. Rejected: dead UI is dead UI; the active-flow rule from `phases.md` Principle 3 applies.

## Risks / Trade-offs

- [android-pdf-viewer is a third-party library not in arch §3] → Mitigation: this proposal is the amendment that adds it; rationale documented above; the library has been the de facto Android PDF renderer for years and is what every Android app uses for this purpose. P10 polish can revisit if the team wants a different choice (e.g., `PdfRenderer` from the Android system class).
- [nickname uniqueness check reads the full `Users` collection on every attempt] → Mitigation: iOS does the same; the demo flow tolerates this; matching iOS is the source-of-truth rule. A Cloud Function `onCall("checkNickNameAvailability")` can be added in a follow-up phase if the team decides the lookup should not happen on the client.
- [account-deletion UI is held for P10] → Risk: the menu row is missing and a user wanting to delete their account cannot. Mitigation: the iOS app also has the same risk today (no other path to deletion), so behavior parity is preserved. P10 ships the UI.
- [chip filter on notifications list not surfaced] → Risk: users cannot scope to community-only notifications. Mitigation: iOS does not surface it either; behavior parity preserved. A follow-up can add the chip.
- [`Calls` count on profile header hardcoded to `0`] → Risk: the count is incorrect. Mitigation: iOS hardcodes it too (line 53 of `ProfileUserInfoCell.swift`); behavior parity preserved. A follow-up can wire it when iOS wires it.
- [deeplink types `.newEvent` / `.newArticle` / `.newForum` / `.videoCall` route to "Coming soon"] → Risk: tapping a notification of those types surfaces a toast. Mitigation: iOS `NotificationsViewModel.notificationOpened` calls `DeepLinksManager.shared.handle` which routes to those screens — iOS users never see a "Coming soon" toast. Mitigation: P10 deep links + Polish ships the routes; until then the toast is the documented behavior.
- [Notification tray tap does not deep-link] → Risk: tapping a system tray notification opens the splash activity. Mitigation: P10 deep links + Polish wires the deep-link tap routing; until then splash is the documented behavior.
- [FCM `data` messages are parsed but not persisted to a local cache] → Risk: a data-payload notification is lost if the user does not open the Notifications tab before the Firestore feed refreshes. Mitigation: iOS does not persist either; behavior parity preserved. A follow-up can add a Room cache.
- [PT-BR privacy policy PDF not bundled] → Risk: a pt-BR-locale user sees an English PDF. Mitigation: the project is English-only this phase per `project-brief.md` §11.7 and `phases.md` Principle 7; a follow-up localizes the PDF when the app adds pt-BR.
- [Avatar upload via `UserProfileRepository.uploadProfileImage` is in scope this phase even though it does not appear in the P6 deliverables list in `docs/phases.md` line 197] → This is intentional: `phases.md` line 197 says "Own profile view, edit basic info" but the iOS source shows avatar edit as a first-class affordance on the profile (`ProfileUserInfoCell.editUserImageClicked` → `uploadUserImage` → `uploadUserProfileImage`), and the Android P2 home header already loads the user avatar from Coil. Removing the avatar edit would diverge from iOS and break parity.

## Migration Plan

Phase 6 lands as a single PR landing the two feature modules + the model + repository extensions + the FCM service + the shell wiring. There is no backend migration; every Firestore path is one iOS already uses against `kornerz-72fba`. The `android-pdf-viewer` dependency is the only new third-party addition and is listed in `gradle/libs.versions.toml`. Rollback is a `git revert` of the merge commit and re-tagging `phase-5-end`; no data migration is required because P6 is read/write against the same fields iOS already populates.

## Open Questions

1. **FCM data-message persistence** — does the team want a Room cache for offline read in this phase, or hold for a follow-up? Current decision: hold (matches iOS no-firmware surface).
2. **Avatar upload in P6 vs P7** — `docs/phases.md` P6 deliverables list does not mention avatar edit. The iOS source treats it as a first-class feature. Current decision: ship it in P6 (matches iOS). If the team wants to defer it to P7's "Create Community" image-upload phase, this changes the scope.
3. **Logout menu row timing** — stakeholder review says logout ships in P10. Current decision: ship the DataStore-clear + `userDeletedHisAccount` event-handler plumbing in P6 (it already exists from P1), but do not expose the menu row UI.
4. **`Calls` count wiring** — iOS hardcodes `0`. Current decision: mirror; revisit if iOS wires it.
5. **Notifications chip filter** — iOS declares `NotificationListType.all / .communities` but the chip is never shown in the active flow. Current decision: hide the chip. If the team wants to surface it, the chip + filter logic is a one-line addition to `NotificationsViewModel`.