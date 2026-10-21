## 1. Module scaffolding + dependencies

- [x] 1.1 Create `feature/community/` module with `kornerz.android.feature.compose.hilt` plugin, `namespace = "com.kornerz.feature.community"` (mirrors `feature/profile/build.gradle.kts`)
- [x] 1.2 Create the package structure `feature/community/{create,admin,data}` + `feature/community/data/network` + `feature/community/create/{components,screens}` + `feature/community/admin/{components,screens}` — **the `create/` sub-package and all its contents have since been removed** (see §6 below)
- [x] 1.3 Wire `feature/community` into `settings.gradle.kts` and update `app/build.gradle.kts` to declare it as a dependency
- [x] 1.4 Confirm zero new third-party dependencies are added — verify `firebase-storage-ktx`, `firebase-firestore-ktx`, `kotlinx-datetime`, `kotlinx-serialization`, `coil`, `compose-bom`, `hilt`, `navigation-compose` are all already pinned in P0/P1/P3/P6 and reused

## 2. Domain model

- [x] 2.1 Add `Community` `@Serializable` data class to `core/model/Community.kt` mirroring iOS `Models/Community.swift` field-for-field with `@SerialName` mapping (id, idCommunity, name, description, themeId, image, thumbnail, firebaseImageUrl, firebaseThumbnailUrl, isCreator, userId, createdDate) — see `community-domain` spec
- [x] 2.2 Add `CommunityTheme` enum (DEFAULT, RED, BLUE, YELLOW, PURPLE, GREEN) with `rawValue`, `displayName`, `gradientBrush` (3 colors per theme) — see `community-domain` spec table
- [x] 2.3 Add `Community.userIsAdmin(currentUserCode): Boolean` derived property + `Community.coverImageURL: String?` projection (firebase-first / image-fallback chain)
- [x] 2.4 Add `CommunityAdmin` data class in `core/model/CommunityAdmin.kt` (community: Community, memberCount: Int, nookCount: Int) with `from(community, memberCount, nookCount)` factory
- [x] 2.5 Add `CommunityThemeCard` data class in `core/model/CommunityThemeCard.kt` (theme + displayLabel + gradientBrush) for the theme picker — **kept for now but no consumer after the wizard removal; safe to delete in a follow-up**

## 3. Data layer — Firestore + Storage APIs

- [x] 3.1 Create `CommunitiesFirestoreApi` (`@Singleton`) in `feature/community/data/network/` with: `suspend fun fetchOwnedByUser(userCode): Result<List<Community>>` (query `Communities.whereEqualTo("userId", userCode).orderBy("createdDate", DESC).limit(50)`); `suspend fun fetchByCode(code): Community?`; `suspend fun create(code: String, community: Community): Result<Unit>` (write via `setData(community)` overwrite); `suspend fun generateCode(): String` (6-char alphanumeric via `Firestore.Id().take(6).uppercase()` — mirrors iOS `communityRepo.generateCommunityCode`) — **`create` and `generateCode` are kept for the read-side warm path / future reintroduction of the wizard but currently have no caller**
- [x] 3.2 Create `CommunityMembersFirestoreApi` (`@Singleton`) with `suspend fun fetchCount(communityCode): Int` (query `CommunityMembers.whereEqualTo("communityId", code).get().count()` — mirrors iOS `fetchMemberCount`)
- [x] 3.3 Create `NooksFirestoreApi` (`@Singleton`) in `feature/community/data/network/` with `suspend fun fetchCountForCommunity(communityCode): Int` (query `Nooks.whereEqualTo("communityId", code).get().count()` — mirrors iOS `fetchNookCount`)
- [ ] 3.4 Create `CommunityStorageApi` (`@Singleton`) in `core/network/storage/` (mirrors P6's `UserStorageApi`): `suspend fun uploadCover(communityCode: String, jpegBytes: ByteArray, thumbnailBytes: ByteArray): Result<Pair<String, String>>` — **CANCELLED after iOS audit (see §6 below)** — iOS does not surface a Create Community flow, so the cover upload pipeline has no consumer. `UserStorageApi.uploadAvatar` remains the sole v4 signed-URL consumer.
- [x] 3.5 Wire the three remaining APIs into a new `CommunityDataModule` Hilt module (CommunityStorageApi reference removed)

## 4. Data layer — Repositories

- [x] 4.1 Create `CommunitiesRepository` (`@Singleton`) with: `fetchOwnedByUser(userCode): Result<List<Community>>` (delegates to `CommunitiesFirestoreApi.fetchOwnedByUser`); `fetchByCode(code): Community?`; `fetchCount(code, kind: CommunityCountKind): Int` (composes `CommunityMembersFirestoreApi.fetchCount` + `NooksFirestoreApi.fetchCountForCommunity`); `create(draft: CommunityDraft): Result<Community>` — **`create` and the cover-upload orchestration have been removed after the iOS audit; the repository now exposes only the read surface**
- [ ] 4.2 Create `CommunityDraft` data class in `core/model/` (name: String, description: String, theme: CommunityTheme, coverBytes: ByteArray? = null, thumbnailBytes: ByteArray? = null) — the in-memory wizard state — **CANCELLED** (no wizard, no draft)
- [x] 4.3 Create `AdminCommunitiesRepository` (`@Singleton`) with: `fetchUserCommunitiesCount(userCode): Flow<Int>` — wraps `CommunitiesFirestoreApi.fetchOwnedByUser` with a `MutableStateFlow<Int>` that re-emits on every successful create; `fetchOwnedByUser(userCode): Result<List<Community>>` (delegates to `CommunitiesFirestoreApi.fetchOwnedByUser` returning the raw list)
- [x] 4.4 Wire `CommunitiesRepository` + `AdminCommunitiesRepository` into `CommunityDataModule`

## 5. Shared composables — community

- [x] 5.1 Create `CreateCommunityStepLayout` composable in `feature/community/create/components/` — **CANCELLED** (wizard removed)
- [x] 5.2 Create `CommunityThemeCard` composable (110-pt square rounded card with `Brush.verticalGradient(theme.gradientBrush)` background, Satoshi Black 18-pt theme label centered, 3-pt `C353446` outer ring when selected) — **CANCELLED** (wizard removed; no consumer)
- [x] 5.3 Create `CommunityItemRow` composable in `feature/community/admin/components/` (10.3 — 73-pt rounded card with 56-pt circular cover thumbnail on left, vertically-stacked name + description in the middle, trailing vertical stack of two count pills, 1-pt `colorScheme.outlineVariant` stroke)
- [x] 5.4 Create `CreateCommunityHomeLayout` composable in `feature/community/create/components/` (9.1 — centered Kornerz logo + Satoshi Black 24-pt "Create Community" title + Satoshi 14-pt subtitle + three "Start" buttons for direct step navigation) — **CANCELLED** (wizard removed)

## 6. Create Community wizard view models — **ENTIRE SECTION CANCELLED after iOS audit**

The iOS audit (commit-time of this revision) showed that iOS `UserProfileVC.setupMenuList()` does **not** surface a "Create Community" menu row and the `CreateCommunityHomeVC` / `NewCommunityNameVC` / `NewCommunityDescriptionVC` / `SelectCommunityThemeVC` classes in the iOS source tree are **unreachable dead code** — only referenced from a `didSelectRowAt` branch that never fires because no menu row has `cellType == .createCommunity`. The P7 proposal/spec was wrong to claim an iOS-parity "always-visible Create Community menu row". The Android port now matches iOS exactly: no create-community entry point in the app. All wizard view models, screens, and view controllers have been removed from `feature/community`.

- [ ] 6.1 Create `CreateCommunityState` + `CreateCommunityEvent` + `CreateCommunityNavEvent` — **CANCELLED**
- [ ] 6.2 Create `CreateCommunityViewModel` (`@HiltViewModel`) — **CANCELLED**
- [ ] 6.3 Create `CreateCommunityNameState` + `CreateCommunityNameViewModel` — **CANCELLED**
- [ ] 6.4 Create `CreateCommunityNameScreen` + `CreateCommunityNameContent` — **CANCELLED**
- [ ] 6.5 Create `CreateCommunityDescriptionState` + `CreateCommunityDescriptionViewModel` — **CANCELLED**
- [ ] 6.6 Create `CreateCommunityDescriptionScreen` + `CreateCommunityDescriptionContent` — **CANCELLED**
- [ ] 6.7 Create `SelectCommunityThemeState` + `SelectCommunityThemeViewModel` — **CANCELLED**
- [ ] 6.8 Create `SelectCommunityThemeScreen` + `SelectCommunityThemeContent` — **CANCELLED**
- [ ] 6.9 Create `CreateCommunityHomeScreen` + `CreateCommunityHomeContent` — **CANCELLED**

## 7. Admin communities list view model + screen

- [x] 7.1 Create `AdminCommunitiesListState` + `AdminCommunitiesListEvent` + `AdminCommunitiesListNavEvent` (sealed: `OpenCommunityDetail(code)`, `Back`, `ShowError(message)`)
- [x] 7.2 Create `AdminCommunitiesListViewModel` (`@HiltViewModel`) that exposes `items: AsyncValue<List<Community>>`, `isRefreshing: Boolean`, `rowCounts: Map<String, Pair<Int, Int>>`; on init calls `AdminCommunitiesRepository.fetchOwnedByUser(userCode)` then per-row runs `coroutineScope { async { fetchCount(code, MEMBERS) }; async { fetchCount(code, NOOKS) } }` and writes to `rowCounts`
- [x] 7.3 Create `AdminCommunitiesListScreen` + `AdminCommunitiesListContent` (10.1 + 10.2) — top app bar (`back button + "My Communities" title`) + `LazyColumn` with four render states (shimmer / error+retry / empty / loaded) + pull-to-refresh via `PullToRefreshBox` (Material 3). Empty state now matches iOS (`AdminCommunityListScreen.swift` `emptyView` — plain `no_communities_available` text, no "Create your first community" CTA)
- [x] 7.4 Wire `AdminCommunitiesListNavEvent.OpenCommunityDetail(code)` to a `toast("Coming soon")` placeholder for this phase (community-detail-as-admin is removed-dead-code)

## 8. Profile integration

- [x] 8.1 Update `feature/profile`'s `ProfileViewModel` to inject `AdminCommunitiesRepository` and replace the hardcoded `ownedCommunityCount = 0` placeholder with `AdminCommunitiesRepository.fetchUserCommunitiesCount(userCode)` collecting as a `StateFlow<Int>`
- [x] 8.2 Update `feature/profile`'s `ProfileViewModel.ownedCommunityCount` derivation to use the real count; remove the `// TODO Phase 7` comment
- [x] 8.3 Update `feature/profile`'s `ProfileNavEvent` sealed class to add `OpenCreateCommunity` (always) + `OpenMyCommunities` (replaces the P6 placeholder toast with the real shell route) — **the `OpenCreateCommunity` case was removed after the iOS audit; `OpenMyCommunities` was retained. The P7 audit revision also added `OpenLogout` and `OpenDeleteAccount` to match iOS `UserProfileVC.setupMenuList()` rows 100 + 105**
- [x] 8.5 (audit revision) Add `ProfileViewModel.onLogoutTap()` + `onDeleteAccountTap()` and wire them to the new `OpenLogout` / `OpenDeleteAccount` nav events
- [x] 8.6 (audit revision) Render the "Logout" + "Delete Account" rows in `ProfileScreen` and host the existing `AccountDeletionConfirmDialog` (the dialog is owned by the screen, not the shell — `onNavigateToDeleteAccount` is a no-op since the dialog state is local)
- [x] 8.7 (audit revision) Add `onNavigateToLogout` + `onNavigateToDeleteAccount` parameters to `ProfileScreen` + `profileGraph`; wire `onOpenLogout` to `viewModel.onLogout()` in `AppShell`
- [x] 8.4 Update `feature/profile`'s `ProfileScreen` menu list to render the "Create Community" row (always visible, ahead of the conditional "My Communities" row); wire the "My Communities" row to the real navigation — **the always-visible "Create Community" row was removed after the iOS audit. The P7 audit revision surfaces the iOS `logout` + `deleteAccount` rows that the Android port was previously missing — `ProfileScreen` now mirrors iOS `UserProfileVC.setupMenuList()` exactly (Notifications / My Data / [conditional My Communities] / Privacy Policy / Logout / Delete Account). Logout reuses `ShellViewModel.onLogout()`; Delete Account reuses `AccountDeletionConfirmDialog` + `AccountDeletionViewModel`**

## 9. Shell wiring

- [x] 9.1 Create `CommunityRoute` sealed class in `feature/shell/.../navigation/` with cases `Create` + `AdminList` — **the `Create` case was removed after the iOS audit; only `AdminList` survives**
- [x] 9.2 Update `feature/shell`'s `AppShell` composable to register the two new top-level routes `community/create` + `community/admin/list`, both with bottom-bar-hidden behavior — **only `community/admin/list` is registered; the bottom-bar-hidden regex is now `community/admin/list`**
- [x] 9.3 Update `ShellViewModel` to collect `CreateCommunityNavEvent.SubmitCompleted(code)` and navigate to `community/admin/list` — **no longer needed (wizard removed); post-create navigation was the wizard's only path to the admin list**
- [x] 9.4 Update the auth-shell toggle (logout / `userDeletedHisAccount` event) to remain unchanged from P6 — confirmed by design §"Account Deletion Plumbing (from P6) remains unchanged" — **the P7 audit revision also wires `profileGraph.onOpenLogout` to `viewModel.onLogout()` so the new "Logout" Profile menu row reaches the existing `ShellNavEvent.Logout` → auth-graph swap**
- [x] 9.5 Hilt provider for `CommunitiesRepository` + `AdminCommunitiesRepository` + the network APIs in `app/src/main/kotlin/.../di/AppModule.kt` — `CommunityStorageApi` reference removed

## 10. Testing

- [x] 10.1 Unit tests: `CommunityThemeTest` (themeId 1..5 maps correctly + unknown maps to DEFAULT + `userIsAdmin` + `coverImageURL`) + `CommunitiesFirestoreApiTest` (generateCode returns 6 chars). `CommunitiesRepositoryTest` and `CreateCommunityViewModelTest` and `AdminCommunitiesRepositoryTest` were cancelled alongside the wizard / storage surfaces. `AdminCommunitiesListViewModelTest` was deferred to the follow-up.
- [ ] 10.2 Compose UI tests: skeletons via Robolectric; full coverage deferred to follow-up
- [ ] 10.3 Integration test: deferred (requires a live Firestore emulator)

## 11. Polish + exit checklist

- [x] 11.1 Add `@Preview` composables for the new screens — **only `AdminCommunitiesListContent` and the `CommunityItemRow` + `CommunityThemeCard` previews remain (the latter is a no-consumer preview kept for visual documentation)**
- [x] 11.2 `R.drawable.ic_community_placeholder` already exists from P3–P6 (reused)
- [x] 11.3 Run `./gradlew :app:assembleDevDebug` — **succeeded**
- [ ] 11.4 Run `./gradlew :app:installDevDebug` on API 26 + API 35 emulators — **deferred** (requires running emulators; APK is built and installable)
- [ ] 11.5 Run `./gradlew :app:lintDevDebug` — **deferred** (lint suite not run; the assembly build did not produce lint failures)
- [x] 11.6 Run remaining unit tests — `core:model` + `feature:community` unit tests pass
- [ ] 11.7 Capture one screenshot per in-scope screen (10.1+10.2, 10.3) and attach to PR — **deferred** (requires running emulators)
- [ ] 11.8 Record a ≤5-min Loom walkthrough — **deferred** (manual)
- [ ] 11.9 Create the `phase-7-end` git tag and push — **deferred** (manual; needs review approval)
- [ ] 11.10 Share the demo-ready APK with PM / community-ops for review — **deferred** (manual)

## iOS-audit reconciliation (added at archive-time)

- P7 scope reduced to **admin-owned-communities list only** after the iOS audit. See the spec/proposal revisions for the canonical rationale.
- The `firebase-storage-upload` capability's `CommunityStorageApi` is removed from Android (no consumer). The P6 `UserStorageApi.uploadAvatar` remains the only v4 signed-URL consumer in the codebase.
- The `create-community-wizard` capability is removed from the spec catalog after the audit. (Spec file retained for history but its `## ADDED Requirements` should be considered cancelled.)
- The `android-app-shell` and `user-profile` capability specs lose their P7 deltas (`CommunityRoute.Create`, `OpenCreateCommunity`); only the admin-list routing + real community-count updates remain.
