## Why

> **See also:** [`docs/architecture.md`](../../../docs/architecture.md) for the MVVM + Repository + UDF contract, `AsyncValue<T>`, `Channel<NavEvent>`, module boundaries, Hilt scopes, and the canonical library list that every artifact in this change conforms to.

Phases P0–P6 shipped a populated Home, Kornerz, Nooks, Loops, Profile, and Notifications tab on the shared Firebase backend (`kornerz-72fba`), plus the full signup / login / session / force-update / FCM / privacy-policy plumbing. The Profile tab already conditionally surfaces a "My Communities" menu row when `ownedCommunityCount > 0` (per P6's `user-profile` capability), but the destination is currently a `toast("Coming in Phase 7")` placeholder — the admin-owned-communities list has no Android surface yet, the `Community` / `CommunityAdmin` model is missing, and the `CommunitiesRepository.fetchOwnedByUser(...)` + `AdminCommunitiesRepository.fetchUserCommunitiesCount(...)` reads are not implemented. Phase 7 ports the **admin-owned-communities** vertical from iOS. iOS (`kornerz/Kornerz/HomeVC/CommunityDetailAsAdmin/AdminCommunitiesList/`, `kornerz/Kornerz/Backend/Repositories/CommunitiesRepository.swift`) is the **source of truth** for every Firestore field, every sort order, and the per-row member/nook count projections. The Android port uses Compose primitives, MVVM + Repository + UDF, and the canonical library list, reaching behavioral parity so the two builds look identical in screenshots and walkthroughs. Backend integration is real (`kornerz-72fba`), no mock servers, and the phase ships an APK that compiles, launches, and is presentable to PM / community-ops for the "from Profile, view my owned communities" demo.

## iOS audit revision

This proposal was originally written under the assumption that iOS
`UserProfileVC` surfaces a "Create Community" menu row that pushes
`CreateCommunityHomeVC` to a 3-step wizard (`NewCommunityNameVC` →
`NewCommunityDescriptionVC` → `SelectCommunityThemeVC`). The
assumption was wrong. The iOS source
(`kornerz/Kornerz/HomeVC/ProfileVC/UserProfileVC.swift`) shows:

- `UserProfileVC.setupMenuList()` (lines 75–110) inserts only
  `userDetail / notifications / completeRegistration / myData /
  reviewTutorial / privacyPolicy / logout / deleteAccount`. No
  `createCommunity` cell is ever inserted.
- The `createCommunity` branch in `didSelectRowAt` (line 246) is
  unreachable dead code — no menu row has `cellType == .createCommunity`.
- The `CreateCommunityHomeVC` / `NewCommunityNameVC` /
  `NewCommunityDescriptionVC` / `SelectCommunityThemeVC` view
  controllers are only referenced from `UserProfileVC.swift:247`
  (the dead branch) and from each other (the wizard's internal
  navigation). They are unreachable from the running iOS app.

**Phase 7 scope is therefore reduced to the admin-owned-communities
list.** The Create Community wizard, the `CommunityStorageApi`
v4 signed-URL cover upload, the `OpenCreateCommunity`
`ProfileNavEvent`, the `community/create` shell route, the always-
visible "Create Community" Profile menu row, and the "Create your
first community" empty-state CTA on the admin list have all been
removed from the Android port. The remaining P7 work — the admin
list, the real `Communities` model, the read-side
`CommunitiesRepository`, the `AdminCommunitiesRepository`, the live
"Communities" count pill in `UserInfoHeader`, and the shell wiring
for the `community/admin/list` route — is what the rest of this
proposal documents.

If a future phase wants to reintroduce community creation on Android,
it should be re-scoped as an Android-only feature (the iOS code can
be referenced for the field shapes but the entry point has no iOS
parity target). The `## ADDED Requirements` of
`specs/create-community-wizard/spec.md` and
`specs/firebase-storage-upload/spec.md` are retained in the
catalog for history but are marked cancelled.

## What Changes

- Add a new `feature/community` module with the standard `feature/*` scaffolding (matches `feature/profile/build.gradle.kts`; `namespace = "com.kornerz.feature.community"`) hosting the full P7 vertical:
  - `community/create` — the 3-step wizard destinations: `community/create` (the orchestrator host, 9.1), `community/create/name` (9.2), `community/create/description` (9.3), `community/create/theme` (9.4, the segmented theme picker).
  - `community/admin` — the admin-owned-communities list: `community/admin/list` (the host, 10.1; contains a nested NavHost with `community/admin/list` view + the per-row composable 10.3).
  - Mirrors iOS `CreateCommunityHomeVC` + `NewCommunityNameVC` + `NewCommunityDescriptionVC` + `SelectCommunityThemeVC` + `AdminCommunityListController` + `AdminCommunityListScreen` + `CommunityItemView`.

- Add `core/network/storage/CommunityStorageApi` (`@Singleton`) wrapping Firebase Storage v4 signed-URL upload (mirrors `FIREBASE_STORAGE_MIGRATION.md` + iOS `FirebaseStorageService.uploadImageToFirebase(jpeg, completion)`). Uploads to `communities/{communityCode}/cover` storing both a full-size JPEG and a 200-px thumbnail (compress 0.5 / 0.3 — mirrors iOS conventions from `UserProfileRepository.uploadUserProfileImage` lines 76–80). Returns `Pair<imageUrl, thumbnailUrl>`. This is the second concrete consumer of the v4 pipeline; the first is `UserProfileRepository.uploadProfileImage` shipped in P6.

- Add `core/model` `@Serializable` data classes:
  - `Community` — mirrors iOS `Models/Community.swift` field-for-field with `@SerialName` mapping: `id = "id"`, `name`, `description`, `theme = "themeId"` (enum-backed: `THEME_RED = 1`, `THEME_BLUE = 2`, `THEME_YELLOW = 3`, `THEME_PURPLE = 4`, `THEME_GREEN = 5`, `THEME_DEFAULT = 0` — mirrors iOS `CommunityThemeId` enum), `coverImageURL = "image"`, `coverThumbnailURL = "thumbnail"`, `firebaseImageURL`, `firebaseThumbnailURL`, `code = "idCommunity"`, `isAdmin = "isCreator"` (the per-viewer admin flag — mirrors iOS line 41), `createdByUserId = "userId"`, `createdAtTimestamp = "createdDate"`. Includes derived `theme: CommunityTheme` enum projection + `userIsAdmin: Boolean` derived from `isAdmin == createdByUserId == currentUserCode`.
  - `CommunityAdmin` — projection for the admin list (community + admin-role metadata): `community: Community`, `memberCount: Int`, `nookCount: Int`. Computed via a `CommunitiesRepository.fetchOwnedByUser(userCode)` composite result.
  - `CommunityTheme` enum with the six iOS values and per-theme `gradientBrush: Brush` (iOS exposes the per-theme gradient drawn from three colors; the Android port exposes the same brush palette in `core/ui/theme/Themes.kt`).

- Add `CommunitiesRepository` (`@Singleton`) wrapping the iOS `CommunitiesRepository.swift` API surface:
  - `fetchOwnedByUser(userCode): Result<List<CommunityAdmin>>` — returns the list of communities where `createdByUserId == userCode`, with the per-community `memberCount` + `nookCount` projections, sorted by `createdAtTimestamp DESC` (mirrors iOS `AdminCommunityListController.viewDidLoad` lines 38–71). The Android port keeps the iOS batch-fetch pattern (`Promise.all`-like dispatch in iOS becomes `coroutineScope { async }` in Kotlin) so the spinner shows one result, not ten.
  - `fetchByCode(communityCode): Community?` — reuses P6's `CommunitiesRepository.fetchByName` style pattern (added in P6 per `notifications-list` capability for the notification deeplink). Single-document read.
  - `create(draft: CommunityDraft): Result<Community>` — orchestrates the 3-step-wizard write: (1) generates a new `communityCode` (Firestore `document().collection().document().id` — mirrors iOS `communityRepo.generateCommunityCode` lines 88–95, which uses 6-char random alphanumeric), (2) uploads the cover JPEG + thumbnail via `CommunityStorageApi.uploadCover(communityCode, jpegBytes, thumbnailBytes)`, (3) writes the new `Communities/{communityCode}` document with all fields populated via `setData(community)` (overwrite — iOS uses `setData` not `merge` for brand-new communities, lines 102–118). Returns the freshly-`Community` with `firebaseImageURL` / `firebaseThumbnailURL` populated.
  - `fetchCount(communityCode, kind): Int` — composes `memberCount` (from `CommunityMembers.whereEqualTo("communityId", code).get().count()`) + `nookCount` (from `Nooks.whereEqualTo("communityId", code).get().count()`). iOS uses the same two-query pattern in `CommunitiesRepository.fetchMemberCount` + `fetchNookCount` lines 137–165.

- Add `AdminCommunitiesRepository` (`@Singleton`) wrapping the iOS `AdminCommunitiesList` view-model reads:
  - `fetchUserCommunitiesCount(userCode): Flow<Int>` — re-emits whenever the user's owned-community set changes. Mirrors iOS `AdminCommunityListController.viewDidLoad` initial count read (line 47) + `.profileUpdatedNotification` re-trigger pattern.
  - `fetchOwnedByUser(userCode): Result<List<Community>>` — returns the same `Communities.whereEqualTo("userId", userCode).order(by: "createdDate", DESC)` query as iOS line 41, returning just `Community` (without the counts — the Admin screen calls `fetchCount` per row on demand to render the "Members" + "Nooks" badges in `CommunityItemView`).

- Add `CreateCommunityViewModel` (`@HiltViewModel`) — the orchestrator (9.1) that exposes `CreateCommunityState(step: WizardStep, draft: CommunityDraft, isSubmitting: Boolean, error: AppError?)` and `Channel<CreateCommunityNavEvent>` with events `Advance(fromStep)`, `Back`, `SubmitCompleted(communityCode)`. The wizard's draft state is **in-memory** until the final Submit — matches iOS `CreateCommunityHomeVC`'s `communityName` / `communityDescription` / `communityTheme` instance variables (lines 47–58). On `SubmitCompleted(communityCode)` the shell routes to `community/admin/list` (the user lands on their freshly-updated admin list).

- Add `CreateCommunityNameViewModel` (`@HiltViewModel`, `SavedStateHandle`) for 9.2 — exposes `CreateCommunityNameState(name: String, isValid: Boolean)` and emits `CreateCommunityNameNavEvent.Advance(name)` on Continue. Disabled when `name.trim().isEmpty()` — mirrors iOS line 65 of `NewCommunityNameVC.swift` (`continueButton.isEnabled = !nameTextField.text!.trimmingCharacters(...).isEmpty`).

- Add `CreateCommunityDescriptionViewModel` (`@HiltViewModel`, `SavedStateHandle`) for 9.3 — exposes `CreateCommunityDescriptionState(description: String, isValid: Boolean)` and emits `CreateCommunityDescriptionNavEvent.Advance(description)` on Continue. Disabled when `description.trim().isEmpty() || description.length > 500` — mirrors iOS `NewCommunityDescriptionVC.swift` line 60–68 (`maxLength = 500`).

- Add `SelectCommunityThemeViewModel` (`@HiltViewModel`, `SavedStateHandle`) for 9.4 — exposes `SelectCommunityThemeState(themes: List<CommunityThemeCard>, selected: CommunityTheme)` and emits `SelectCommunityThemeNavEvent.Confirm(theme)` on Confirm. Pre-selects the current draft's theme (or the first theme on first pass).

- Add `AdminCommunitiesListViewModel` (`@HiltViewModel`) for 10.1/10.2 — exposes `AdminCommunitiesListState(items: AsyncValue<List<CommunityAdmin>>, isRefreshing: Boolean)` and orchestrates `AdminCommunitiesRepository.fetchOwnedByUser()` + `CommunitiesRepository.fetchCount()` per row on first load. Pull-to-refresh re-fetches both. Render states: shimmer (iOS `SVProgressHUD` + `shimmerView` — Android uses `KornerzLoadingView`), retry on error (`KornerzErrorState`), no-data (`KornerzEmptyState` with "You have not created any community yet" copy — mirrors iOS `AdminCommunityListScreen.swift` empty handling), and the loaded grid.

- Add `feature/community/.../components/CreateCommunityStepLayout` composable — the shared step header + Continue button chrome wrapping the three wizard step destinations. Renders a top app bar (back arrow + "Create Community" title), a step title (`Satoshi Bold 22-pt, color C353446`), the per-step content slot, and a "Continue" pill button (filled `C5D47ED` when enabled, disabled `CF2F4F6` otherwise — Satoshi Bold 17-pt). Mirrors iOS `NewCommunityNameVC.viewDidLoad` chrome (lines 38–72).

- Add `feature/community/.../components/CommunityThemeCard` composable — 110-pt square rounded card with `Brush.verticalGradient(theme.gradientBrush)` background and a Satoshi Black 18-pt theme label centered. Selected variant adds a 3-pt `C353446` outer ring. Tap emits `onSelected(theme)`. Mirrors iOS `SelectCommunityThemeVC.SelectionView`.

- Add `feature/community/.../components/CommunityItemRow` composable (10.3) — rounded 16-pt card on `colorScheme.surface` with `colorScheme.outlineVariant` 1-pt stroke. Layout: 56-pt circular cover thumbnail (Coil + `R.drawable.ic_community_placeholder` fallback, `Brush.verticalGradient(theme.gradientBrush)` when no URL), 2-line title (`name`, Satoshi Bold 17-pt color `C353446` + `description` 1-line truncated, Satoshi 13-pt color `C9E9EAE`), and a trailing vertical stack of "Members: N" + "Nooks: N" mini-badges (8-pt rounded `CF2F4F6` pill, Satoshi 11-pt). Tap emits `onCommunityClicked(communityCode)` which emits `AdminCommunitiesListNavEvent.OpenCommunityDetail(communityCode)`.

- Add `feature/community/.../components/CreateCommunityHomeLayout` composable (9.1) — the orchestrator screen, 5-tab bottom-nav-replacement full-screen route. Layout: a centered Kornerz logo above a Satoshi Black 24-pt title ("Create Community"), a Satoshi 14-pt subtitle ("Bring people together around the things that matter to you"), and three "Start" buttons (one per step) for direct navigation. Mirrors iOS `CreateCommunityHomeVC.viewDidLoad` (lines 22–55). Start button 1 → `community/create/name`; Start button 2 → directly selects the first un-completed step from the in-memory draft.

- Refactor `feature/shell` to add the two new top-level routes: `community/create` (pushed from Profile → tap the "Create Community" menu row, which is **new this phase** — P6's profile menu already conditionally surfaces "My Communities" when `ownedCommunityCount > 0`, this phase also adds a "Create Community" row that is **always visible** per iOS parity) and `community/admin/list` (the destination after a successful create, or pushed directly from the "My Communities" menu row in Profile). Both routes are top-level and hide the bottom bar (mirrors iOS push-to-full-screen from `UserProfileVC` lines 200–240). The Profile tab's "My Communities" menu row is updated to actually navigate to `community/admin/list` instead of the P6 placeholder toast.

- Update `feature/profile`'s `ProfileViewModel` to call the real `AdminCommunitiesRepository.fetchUserCommunitiesCount(userCode)` — the P6 implementation used a hardcoded `0` placeholder. This removes the `data` placeholder comment in `ProfileViewModel` and wires the real count into the "Communities" pill in `UserInfoHeader`.

- Pass the per-phase exit checklist (`./gradlew :app:assembleDevDebug`, install on API 26 + API 35 emulators, no crashes, lint, unit tests, screenshots, demo walkthrough, `phase-7-end` git tag).

## Capabilities

### New Capabilities

- `create-community-wizard`: The 3-step Create Community wizard (9.1 orchestrator + 9.2 Name + 9.3 Description + 9.4 Theme picker). Reached from Profile → "Create Community" row. Holds the in-memory `CommunityDraft` in the orchestrator VM, persists nothing until Confirm on the Theme step. Writes via `CommunitiesRepository.create(draft)` which generates a code + uploads cover + writes Firestore atomically. On success, navigates to `community/admin/list` and the new community appears at the top. Mirrors iOS `CreateCommunityHomeVC` + `NewCommunityNameVC` + `NewCommunityDescriptionVC` + `SelectCommunityThemeVC`. The 9.1 orchestrator exposes direct "Start" buttons for each of the three sub-screens (a power-user shortcut), but the live demo flow is forward-only through the steps. The 9.4 theme picker surfaces all six iOS themes (Red, Blue, Yellow, Purple, Green, Default) with the same per-theme gradient colors used in iOS `SelectCommunityThemeVC.SelectionView` and the same selection-ring stroke. Cover photo upload is **optional** in this phase (the iOS wizard supports a "Skip" affordance — `CreateCommunityHomeVC` lines 100–125; Android mirrors with a "Skip" button that uses a generated gradient cover for the new community; this matches iOS's "no image yet" state when the user taps Skip).

- `admin-communities-list`: The admin-owned-communities list (10.1 host + 10.2 view + 10.3 row). Reached from Profile → "My Communities" row (visible only when `ownedCommunityCount > 0`) and from `create-community-wizard` after a successful Submit. Renders a `LazyColumn` of `CommunityItemRow`s sorted by `createdAtTimestamp DESC`. Pull-to-refresh via `PullToRefreshBox` (Material 3) re-fetches the list + per-row counts. Per-row tap opens `CommunityItemRow`'s deeplink navigation; in this phase the destination routes to a `toast("Coming in Phase 9")` placeholder because community-detail-as-admin is the iOS `CommunityDetailAsAdmin/AdminCommunitiesList/` chain which has its own list of sub-screens documented as removed-dead-code in `screen-inventory.md` §"What is NOT in the Android port" — Android mirrors the no-firmware surface for community-detail-as-admin until a follow-up phase decides what's needed. The "Delete" admin row swipe-action from iOS is also deferred (not in `screen-inventory.md` for P7).

- `community-domain`: The `Community` + `CommunityAdmin` + `CommunityTheme` enum + `CommunityThemeCard` models and projections in `core/model`. The Community schema mirrors iOS `Models/Community.swift` field-for-field, including `themeId` enum mapping, `isCreator` admin flag, `createdDate` timestamp, and the Firebase Storage v4 URL fields (`firebaseImageURL` / `firebaseThumbnailURL`). The `Community.userIsAdmin(currentUserCode)` derived property is the boolean that `AdminCommunitiesListViewModel` uses to gate visibility of admin-only UI (mirrors iOS `isCreatorOfCommunity` lines 36–42 in `UserProfileVC.swift`). Also adds `CommunityThemeCard` projection (theme + display label + gradient brush) used by the theme picker.

- `firebase-storage-upload`: The `CommunityStorageApi` v4 signed-URL upload helper in `core/network`. Uploads cover JPEG + thumbnail to `communities/{communityCode}/cover` with two compression qualities (0.5 full / 0.3 thumb). Mirrors the same pipeline that P6's `UserProfileRepository.uploadProfileImage` uses (the only difference is the storage path). Returns `Pair<fullUrl, thumbnailUrl>`. This is the canonical "v4 upload helper for any image" pattern; future phases (e.g., P10 polish, korner cover photos) reuse it.

### Modified Capabilities

- `android-app-shell`: The 5-tab bottom navigation bar SHALL add two new top-level routes: `community/create` (a full-screen route, hides the bottom bar — matches iOS push from `UserProfileVC.openCreateCommunity()` lines 200–215) and `community/admin/list` (a full-screen route, hides the bottom bar — matches iOS push from `UserProfileVC.openMyCommunities()` lines 218–233). A new `CommunityRoute` sealed class with cases `Create`, `AdminList` is added. The `userDeletedHisAccount` event handler continues to route back to auth (unchanged from P6).

- `user-profile`: The Profile screen SHALL add a new "Create Community" menu row that is **always visible** (mirrors iOS `UserProfileVC.openCreateCommunity()` lines 200–215). The existing "My Communities" menu row (conditionally visible when `ownedCommunityCount > 0`, added in P6) SHALL navigate to `community/admin/list` (the P6 implementation emitted a `toast("Coming in Phase 7")` placeholder — this phase replaces the toast with the real navigation). The "Communities" count pill in `UserInfoHeader` SHALL render the real `AdminCommunitiesRepository.fetchUserCommunitiesCount(userCode)` value (P6 hardcoded the implementation as a future-P7 placeholder; this phase wires the real call).

- `community-domain` and `user-profile-domain` interaction: The `Community.userIsAdmin(currentUserCode)` derived property is computed in `core/model` and consumed by both `AdminCommunitiesRepository` (to gate "edit" affordances — none exposed in this phase) and the `UserInfoHeader`'s "Communities" pill label (which shows just the numeric count regardless of admin status; the pill text remains "Communities", not "My Communities", matching iOS line 53).

- `android-build`: No dependency additions. The `firebase-storage-ktx` dependency required for `CommunityStorageApi` is already pinned in P0's `core/network/build.gradle.kts` (it was added in P0 for future use and was first exercised by P6's `UserProfileRepository.uploadProfileImage`). The v4 signed-URL client code lives in `core/network/storage/` and was also added in P6. P7 reuses both. **No new Gradle dependency is introduced in P7.**

## Impact

- New modules: `feature/community` (single Gradle module hosting both `create` and `admin` sub-packages — mirrors the per-tab boundary from P2/P3/P5; `namespace = "com.kornerz.feature.community"`). Imported only by `app` per arch §2.5.
- New code in `core/model` (`Community` + `CommunityAdmin` + `CommunityTheme` enum + `CommunityThemeCard` + `UserIsAdmin` derived helper), `core/network` (`CommunityStorageApi` + `CommunitiesFirestoreApi` + `CommunityMembersFirestoreApi` + `NooksFirestoreApi` in `feature/community/data/network/`), `feature/community/create/` (`CreateCommunityScreen` / `Content` for 9.1, `CreateCommunityNameScreen` / `Content` for 9.2, `CreateCommunityDescriptionScreen` / `Content` for 9.3, `SelectCommunityThemeScreen` / `Content` for 9.4, the four view models, the `CreateCommunityStepLayout` + `CommunityThemeCard` composables), `feature/community/admin/` (`AdminCommunitiesListScreen` / `Content` for 10.1+10.2, the `CommunityItemRow` composable 10.3, the `AdminCommunitiesListViewModel`), plus the two repositories (`CommunitiesRepository` + `AdminCommunitiesRepository`) in `feature/community/data/`.
- Touched modules: `feature/profile` (`ProfileViewModel.ownedCommunityCount` switches from hardcoded `0` to `AdminCommunitiesRepository.fetchUserCommunitiesCount(userCode)`; the "My Communities" navigation event switches from the P6 toast to `CommunityRoute.AdminList`; a new menu row "Create Community" is added), `feature/shell` (two new top-level routes `community/create` + `community/admin/list`; new `CommunityRoute` sealed class; `AppShell` bottom-bar visibility toggles for the two new full-screen routes), `app` (Hilt module wires `CommunitiesRepository`, `AdminCommunitiesRepository`, `CommunityStorageApi`, `CommunitiesFirestoreApi`, `CommunityMembersFirestoreApi`, `NooksFirestoreApi`).
- Dependencies: **zero new third-party libraries**. All libraries (Compose BOM, Hilt, Navigation-Compose, Coil, Firebase Auth/Firestore/Storage/Cloud Messaging KTX, kotlinx-datetime, kotlinx-serialization) are already pinned. The v4 signed-URL upload helper added in P6 is reused.
- Backend: zero new backend work. Every Firestore read/write matches the iOS app against the shared `kornerz-72fba` project (`Communities/{communityCode}`, `CommunityMembers`, `Nooks`, `Users/{userCode}` + the existing `Users.tokenPush` write). The composite indexes most likely to be required are `Communities` ordered by `createdDate` descending with `userId == userCode` (admin list — iOS uses this without an explicit index note, mirror at PR time and check via Firestore console) and `CommunityMembers` / `Nooks` by `communityId` for the count projections.
- Localization: English only this phase per `project-brief.md` §11.7 and `phases.md` Principle 7; all new screens are RTL-safe and previewed under the `en-rXA` pseudo-locale. Strings sourced from the iOS `Resources/LocalizationStringFiles` fall back to literal English when not yet extracted.
- Out of scope (deferred): Premium / IAP (P8), Video Calling — Vonage / WebRTC (P9), Ranking + Deep Links + Polish (P10). Community-detail-as-admin screens (the iOS `CommunityDetailAsAdmin/` chain — not in `screen-inventory.md` §10 for this phase; documented as removed-dead-code in "What is NOT in the Android port"). The per-row swipe "Delete" admin action (not surfaced in `screen-inventory.md`). The cover photo upload as a mandatory step (iOS already supports Skip via `CreateCommunityHomeVC` lines 100–125; Android mirrors — uploaded photos are optional, defaults to a gradient cover). The `CommunityMembersCount` projection from the real `CommunityMembers` collection (iOS derives the count but writes `0` if the collection is empty per `AdminCommunityListScreen.swift` line 60; Android mirrors iOS behavior exactly). pt-BR localization of all new screens (follow-up per `project-brief.md` §11.7).
