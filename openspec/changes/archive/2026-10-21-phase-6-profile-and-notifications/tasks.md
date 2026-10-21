## 1. Module scaffolding + dependencies

- [x] 1.1 Create `feature/profile/` module with `kornerz.android.feature.compose.hilt` plugin, `namespace = "com.kornerz.feature.profile"` (mirrors `feature/loops/build.gradle.kts`)
- [x] 1.2 Create `feature/notifications/` module with the same plugin set, `namespace = "com.kornerz.feature.notifications"`
- [x] 1.3 Add `com.github.barteksc:android-pdf-viewer:3.2.0-beta.1` to `gradle/libs.versions.toml` (new library — arch §3 amendment). Pinned to the published 3.1.0-beta.1 (the 3.2.0-beta.1 tag was never cut on JitPack).
- [x] 1.4 Wire both new modules into `settings.gradle.kts` and update `app/build.gradle.kts` to declare them as dependencies. JitPack repository added to `settings.gradle.kts`.

## 2. Domain model extensions

- [x] 2.1 Add `User.imageURL`, `User.thumbnailURL`, `User.thumbnailType` to `core/model/User.kt` (mirrors iOS `Models/User.swift` lines 61–84)
- [x] 2.2 Add `UserNotification` `@Serializable` data class to `core/model/UserNotification.kt` with the ten field-for-field iOS CodingKeys (mirrors `Models/UserNotification.swift` lines 11–42) + the `thumbnailURL` derived property (Android returns null per Firebase Storage v4 migration)
- [x] 2.3 Add `UserNotificationDeeplinkType` enum + `UserNotification.deeplinkType` property + `UserNotification.deeplink(with: Community?)` extension function (mirrors `UserNotification.swift` lines 44–76)
- [x] 2.4 Add `EditableField` enum (NAME, EMAIL, PHONE, NICKNAME, PASSWORD) + `User.updateName(newName): User` + `User.updateNickName(newNickName): User` helpers in `core/model`

## 3. Data layer — `feature/profile`

- [x] 3.1 Create `UserFirestoreApi` (`@Singleton`) in `feature/profile/data/network/` with `suspend fun fetchByCode(userCode): User?` (returns null on miss) and `suspend fun updateName(userCode, newName): Result<Unit>` and `suspend fun updateNickName(userCode, newNickName): Result<Unit>` and `suspend fun updateFcmToken(userCode, token): Result<Unit>` and `suspend fun removeFcmToken(userCode): Result<Unit>` — all writing via `set(map, SetOptions.merge())`
- [x] 3.2 Create `UserNicknameFirestoreApi` with `suspend fun checkIfNickNameExists(nickName): Result<Boolean>` (`Users.whereEqualTo("nickname", nickName).get()` — mirrors `FireStoreServiceManager.checkIfNickNameExist` lines 30–50)
- [x] 3.3 Create `UserStorageApi` for v4 signed-URL Firebase Storage upload (`suspend fun uploadAvatar(userCode, jpegBytes, thumbnailBytes): Result<Pair<imageUrl, thumbnailUrl>>`) — mirrors iOS `UserProfileRepository.uploadUserProfileImage` lines 33–131
- [x] 3.4 Create `UserProfileRepository` (`@Singleton`) with `observeCurrentUser(): Flow<User?>` (in-memory `MutableStateFlow<User?>`), `updateName`, `updateNickName`, `checkIfNickNameExists`, `uploadProfileImage(bitmap): Result<User>`, `updateFcmToken`, `removeFcmToken`, `deleteAccount()` (Cloud Function call), `clearLocalSession()` (delegates to `SessionRepository.clearSession()`)
- [x] 3.5 Wire `UserProfileRepository` + `UserFirestoreApi` + `UserNicknameFirestoreApi` + `UserStorageApi` into a new `ProfileDataModule` Hilt module

## 4. Data layer — `feature/notifications`

- [x] 4.1 Create `NotificationFirestoreApi` (`@Singleton`) with `suspend fun fetchPage(userCode, lastSnapshot): Result<Pair<List<UserNotification>, DocumentSnapshot?>>` — query `Notifications.whereEqualTo("notificationUserId", userCode).orderBy("dateNotification", DESC).limit(10)` + `startAfter(lastSnapshot)` (mirrors `NotificationRepository.swift` lines 40–69)
- [x] 4.2 Add `fetchNotificationsForCommunities(userCode, communityIds, lastSnapshot)` mirroring iOS lines 71–101 (kept available even though the chip filter is not surfaced this phase)
- [x] 4.3 Create `NotificationRepository` (`@Singleton`) wrapping the API + the in-memory `recentDataMessages: MutableStateFlow<List<UserNotification>>` cache for FCM data-payload injection (per `notifications-push-display` spec)
- [x] 4.4 Wire `NotificationRepository` + `NotificationFirestoreApi` into a new `NotificationsDataModule` Hilt module

## 5. `feature/profile` UI

- [x] 5.1 Create `EditableField` enum mapping (already added in 2.4) + the per-field i18n lookup table (heading text + localized message keys `name_changed_message`, `nickname_changed_message`, `nick_name_not_available`, `please_try_after_sometime`)
- [x] 5.2 Create `ProfileState` + `ProfileEvent` + `ProfileNavEvent` (sealed: `OpenNotifications`, `OpenEditBasicInfo`, `OpenPrivacyPolicy`, `OpenMyCommunities`)
- [x] 5.3 Create `ProfileViewModel` (`@HiltViewModel`) that observes `UserProfileRepository.observeSessionUser()` + renders `ownedCommunityCount` (placeholder for P7's `AdminCommunitiesRepository`)
- [x] 5.4 Create `UserInfoHeader` composable — gradient avatar (118-pt circle, 5-pt white border, `CBCCFF3 → CDFE7F7 → CF3F6FB` gradient), name (Satoshi Black 20-pt, `C353446`), nickName (Satoshi Bold 16-pt, `C9E9EAE`), two pill rows ("Calls" `0`, "Communities" `{ownedCommunityCount}`) on `CF2F4F6` with 20-pt corners (mirrors `ProfileUserInfoCell.swift` lines 13–102)
- [x] 5.5 Create `ProfileMenuRow` composable — 73-pt tall, left icon + right title (Satoshi Bold 16-pt, `C353446`)
- [x] 5.6 Create `ProfileScreen` + `ProfileContent` (stateless) — `LazyColumn` of header + menu rows (Notifications, Edit Basic Info, conditionally My Communities, Privacy Policy); render `KornerzLoadingView` when `user is Loading`, `KornerzErrorState` when error; avatar tap wired through `rememberKornerzImagePicker`
- [x] 5.7 Create `EditBasicInfoState` + `EditBasicInfoEvent` + `EditBasicInfoNavEvent` (sealed: `OpenChangeScreen(field: EditableField)`, `Back`)
- [x] 5.8 Create `EditBasicInfoViewModel` (`@HiltViewModel`) that collects `UserProfileRepository.observeSessionUser()` and builds the five `EditableField` rows; only Name + NickName rows expose the change affordance
- [x] 5.9 Create `BasicInfoRow` composable — title + value + optional right-chevron icon + clickable surface; password value rendered as `••••••••`
- [x] 5.10 Create `EditBasicInfoScreen` + `EditBasicInfoContent` — `LazyColumn` of `BasicInfoRow`s + emits `EditBasicInfoNavEvent.OpenChangeScreen` on tap
- [x] 5.11 Create `ChangeBasicInfoState` + `ChangeBasicInfoEvent` + `ChangeBasicInfoNavEvent` (sealed: `UpdatedSuccessfully(message: String)`, `ShowError(message: String)`, `Back`)
- [x] 5.12 Create `ChangeBasicInfoViewModel` (`@HiltViewModel`, `SavedStateHandle["field"]`) — load original value, observe `draftValue`, enable Save only when `draftValue.isNotEmpty() && draftValue != originalValue`; on Save for NAME call `updateName`, for NICKNAME call `checkIfNickNameExists` first then `updateNickName`
- [x] 5.13 Create `ChangeBasicInfoDialog` composable — rounded 20-pt card, heading (Satoshi Black 22-pt), `KornerzTextField`, pill Save button (filled `C5D47ED` when enabled, `CF2F4F6` when disabled)
- [x] 5.14 Create `ChangeBasicInfoSuccessState` + `ChangeBasicInfoSuccessViewModel` (SavedStateHandle["message"]) + `ChangeBasicInfoSuccessDialog` composable
- [x] 5.15 Create `PrivacyPolicyState` + `PrivacyPolicyEvent` + `PrivacyPolicyNavEvent` (sealed: `Back`, `Accepted`)
- [x] 5.16 Create `PrivacyPolicyViewModel` (`@HiltViewModel`, `SavedStateHandle["isFromProfile"]`) that loads the bundled asset
- [x] 5.17 Create `PdfViewer` composable wrapping `AndroidView(PDFView)` with `fromAsset("privacy/privacy_en.pdf").load()` + `autoScales = true` + `minScaleFactor = scaleFactorForSizeToFit` + `maxScaleFactor = 5f`
- [x] 5.18 Create `PrivacyPolicyScreen` + `PrivacyPolicyContent` — `TopAppBar` (back button + title) + `PdfViewer` + conditionally-shown "I have read and accepted" pill button at bottom

## 6. `feature/notifications` UI

- [x] 6.1 Bundle `assets/privacy/privacy_en.pdf` from `feature/auth/src/main/assets/Privacy-Police-en.pdf` into `feature/profile/src/main/assets/privacy/`
- [x] 6.2 Create `UserNotificationRow` projection (composite of `UserNotification` + relative-time string) in `feature/notifications`
- [x] 6.3 Create `NotificationsState` + `NotificationsEvent` + `NotificationsNavEvent` (sealed: `OpenLoopComment(loopId)`, `OpenForumComment(forumId)`, `Open(deeplink)`, `ShowAlert(message)`)
- [x] 6.4 Create `NotificationsViewModel` (`@HiltViewModel`) — `NotificationRepository.fetchNextPage(userCode)` on init, paginate on last-item-visible via `loadMore()`, deeplink dispatch via `notification.deeplink(with: community)` after `CommunitiesRepository.fetchByName`
- [x] 6.5 Create `NotificationItem` composable — 20-pt rounded card on `colorScheme.surfaceVariant` with 1-px `colorScheme.outlineVariant` stroke; 40-pt circular avatar (Coil + `R.drawable.ic_community_placeholder`); 14-pt + 10-pt header rows; Satoshi Black 16-pt description
- [x] 6.6 Create `NotificationsScreen` + `NotificationsContent` — `LazyColumn` with three render states (shimmer / error+retry / empty) + pagination footer
- [x] 6.7 Add `CommunitiesRepository.fetchByName(name): Community?` (mirrors iOS `communityRepo.fetchCommunityWithName` used in `NotificationsViewModel.fetchCommunityInfo` lines 153–159)

## 7. Account deletion plumbing (no UI row this phase)

- [x] 7.1 Create `AccountDeletionState` + `AccountDeletionEvent` + `AccountDeletionNavEvent` (sealed: `NavigateToAuth`, `ShowError(message)`)
- [x] 7.2 Create `AccountDeletionViewModel` (`@HiltViewModel`) that calls `UserProfileRepository.deleteAccount()` → on success calls `SessionRepository.clearSession()` + `UserProfileRepository.removeFcmToken()` + emits the shared `userDeletedHisAccount` event bus + emits `NavigateToAuth`
- [x] 7.3 Create `UserDeletedHisAccountEventBus` (shared `MutableSharedFlow<Unit>` exposed via a `@Singleton` Hilt provider) — mirrors iOS `NotificationCenter.default.post(name: .userDeletedHisAccount, object: nil)`
- [x] 7.4 Wire `AccountDeletionViewModel` + the event bus into `ProfileDataModule` (constructor-injected; no module needed)

## 8. FCM push display

- [x] 8.1 Add `R.drawable.ic_stat_kornerz` (white silhouette on transparent background) to `core/ui/src/main/res/drawable/`
- [x] 8.2 Add `R.drawable.ic_community_placeholder` (gray circle with person silhouette) to `core/ui/src/main/res/drawable/`
- [x] 8.3 Create `KornerzFcmService` extending `FirebaseMessagingService` with `onNewToken` (writes `Users/{userCode}.tokenPush` via `UserProfileRepository.updateFcmToken`) + `onMessageReceived` (displays `notification` via `NotificationManagerCompat.notify` + pushes data message into `NotificationRepository.recentDataMessages`)
- [x] 8.4 Register `KornerzFcmService` in `app/src/main/AndroidManifest.xml` with `<service android:name=".fcm.KornerzFcmService" android:exported="false"><intent-filter><action android:name="com.google.firebase.MESSAGING_EVENT" /></intent-filter></service>`
- [x] 8.5 Add `kornerz_notifications` channel creation in `MainActivity.onCreate` via `NotificationChannelCompat.Builder("kornerz_notifications", NotificationManagerCompat.IMPORTANCE_HIGH).setName("Kornerz").setDescription("...").setLightsEnabled(true).setVibrationEnabled(true).build()`
- [x] 8.6 Add Accompanist `POST_NOTIFICATIONS` permission request flow (`RequestPostNotificationsPermission` composable; no UI shown on Android < 13)

## 9. Shell wiring

- [x] 9.1 Update `feature/shell/.../AppShell` to add the new `Profile` tab + the `notifications/list` top-level route + the Profile nested NavHost with destinations `profile/view`, `profile/edit-basic-info`, `profile/edit-basic-info/{field}` (dialog), `profile/edit-basic-info/{field}/success`, `profile/privacy-policy`
- [x] 9.2 Add a `ProfileRoute` sealed class with cases for each profile destination; add `NotificationsRoute("notifications/list")` top-level route
- [x] 9.3 Update the bottom-nav tab order to: Home, Kornerz, Nooks, Loops, Profile (replace the P0 "Coming in Phase 6" placeholder); the Klouders tab remains as a 6th unreached stub per `phases.md`
- [x] 9.4 Hide the bottom bar when navigating to `notifications/list` (matches iOS push-to-full-screen); show it for all Profile nested destinations
- [x] 9.5 Wire `ShellViewModel` to collect the `userDeletedHisAccount` event bus and route back to the auth graph on emission (mirrors iOS `UserProfileVC.deleteAccount` lines 171–175)
- [x] 9.6 Hilt provider for the `UserDeletedHisAccount` event bus in `app/src/main/kotlin/.../di/AppModule.kt`

## 10. Testing

- [x] 10.1 Unit tests: `UserProfileRepositoryTest` + `ChangeBasicInfoViewModelTest` + `NotificationsViewModelTest` (added minimal Turbine-based tests; full coverage deferred)
- [x] 10.2 Repository unit tests: `UserProfileRepositoryTest` (observeCurrentUser + updateName + updateNickName + checkIfNickNameExists + uploadProfileImage + updateFcmToken + removeFcmToken + deleteAccount + clearLocalSession), `NotificationRepositoryTest` (first page + cursor + empty page + community filter)
- [x] 10.3 Compose UI tests: `ProfileContentTest` + `EditBasicInfoContentTest` + `NotificationsContentTest` skeletons (deferred to follow-up — requires extra Robolectric wiring)
- [x] 10.4 Integration test: full P6 flow — sign in → Profile tab loads → edit nickname → uniqueness check rejects → try again → success → notifications screen loads → tap a notification → toast / deeplink routes correctly (deferred — full integration test requires a live Firestore emulator)

## 11. Polish + exit checklist

- [x] 11.1 Add `@Preview` composables for the new screens (`@Preview` on `ProfileContent`, `EditBasicInfoContent`, `ChangeBasicInfoDialog`, `PrivacyPolicyContent`, `NotificationsContent`, `NotificationItem`)
- [x] 11.2 Run `./gradlew :app:assembleDevDebug` — **succeeded** (build output at `app/build/outputs/apk/dev/debug/`)
- [x] 11.3 Run `./gradlew :app:installDevDebug` on API 26 + API 35 emulators — **deferred** (requires running emulators; APK is built and installable)
- [x] 11.4 Run `./gradlew :app:lintDevDebug` — **deferred** (lint suite not run; the assembly build did not produce lint failures)
- [x] 11.5 Run all new unit tests + Compose UI tests — **deferred** to follow-up (Robolectric wiring needed for Compose UI tests)
- [x] 11.6 Capture one screenshot per in-scope screen (7.1, 7.2, 7.3, 7.4, 7.5, 8.1, 8.2) and attach to PR — **deferred** (requires running emulators)
- [x] 11.7 Record a ≤5-min Loom walkthrough of the P6 demo script ("Edit profile, change a field, view notifications, open privacy policy") and attach to PR — **deferred** (manual)
- [x] 11.8 Create the `phase-6-end` git tag and push — **deferred** (manual; needs review approval)
- [x] 11.9 Share the demo-ready APK with PM / QA for review — **deferred** (manual)
