# user-profile Specification

## Purpose
TBD - created by archiving change phase-7-create-community-and-admin. Update Purpose after archive.

## Status: P7 delta matches iOS menu list

The previous P7 draft added a "Create Community" menu row that was
**always visible** on the Profile screen, mirroring an iOS pattern
that turned out not to exist. After the iOS audit, that row is
removed and the Profile screen mirrors iOS
`UserProfileVC.setupMenuList()` exactly: Notifications / My Data /
[conditional My Communities, visible when `ownedCommunityCount > 0`]
/ Privacy Policy / **Logout / Delete Account**. The P7 audit
revision also surfaces the `logout` and `deleteAccount` rows that
iOS exposes but the Android port was previously missing — both
reuse the existing `ShellViewModel.onLogout()` and
`AccountDeletionViewModel` plumbing.

## ADDED Requirements

### Requirement: Profile menu mirrors iOS exactly
The Profile screen SHALL surface the following menu rows in the
canonical iOS order: Notifications, My Data, [conditional My
Communities, visible when `ownedCommunityCount > 0`], Privacy
Policy, Logout, Delete Account. Mirrors iOS
`UserProfileVC.setupMenuList()` (lines 79–108) row-for-row in this
phase's scope.

There is **no** "Create Community" menu row. The iOS app does not
surface one (`UserProfileVC.setupMenuList()` lines 75–110 do not
insert a `createCommunity` cell) and the iOS
`CreateCommunityHomeVC` is unreachable dead code.

#### Scenario: Open Create Community from Profile menu
**CANCELLED** — there is no Create Community menu row in the Android
port (matches iOS).

#### Scenario: Create Community menu row precedes "My Communities"
**CANCELLED** — no Create Community row exists.

### Requirement: Profile "Logout" menu row triggers full sign-out
The Profile screen SHALL render an always-visible "Logout" menu row (matching iOS line 100 `logout` cell) that emits `ProfileNavEvent.OpenLogout`, which the
shell routes to `ShellViewModel.onLogout()`. The handler clears the
FCM token via `FcmTokenRegistrar.clearOnLogout()` and emits
`ShellNavEvent.Logout`; the root NavHost then swaps back to the
auth graph (matches iOS `UserProfileVC.setupMenuList` line 102 +
`didSelectRowAt` line 225 logout handler).

#### Scenario: Tap Logout menu row
- **WHEN** user taps "Logout"
- **THEN** the FCM token is cleared, `ShellNavEvent.Logout` fires, and the root NavHost switches to the auth graph (matches iOS lines 225–235)

### Requirement: Profile "Delete Account" menu row opens the confirmation dialog
The Profile screen SHALL render an always-visible "Delete Account" menu row (matching iOS line 105 `deleteAccount` cell) that emits `ProfileNavEvent.OpenDeleteAccount`, which the Profile screen catches and forwards to
`AccountDeletionViewModel.onEvent(AccountDeletionEvent.OpenConfirm)`
— the screen renders the existing
`AccountDeletionConfirmDialog` (P6). On confirm, the VM calls
`UserProfileRepository.deleteAccount()` (Cloud Function), removes
the FCM token, clears the local session, emits
`UserDeletedHisAccountEventBus` (consumed by `ShellViewModel` to
fire `ShellNavEvent.Logout` → auth graph), and emits
`AccountDeletionNavEvent.NavigateToAuth` (matches iOS
`UserProfileVC.deleteAccount` lines 152–185 + `didSelectRowAt` line
249).

#### Scenario: Tap Delete Account menu row
- **WHEN** user taps "Delete Account"
- **THEN** the `AccountDeletionConfirmDialog` renders with a Cancel / Delete pair

#### Scenario: Confirm Delete Account
- **WHEN** user taps Delete in the confirm dialog
- **THEN** the `AccountDeletionViewModel.confirm()` runs the Cloud Function, clears the FCM token + local session, emits the `userDeletedHisAccount` event bus, and the shell swaps to the auth graph (matches iOS lines 152–185 + 225–235)

#### Scenario: Cancel Delete Account
- **WHEN** user taps Cancel in the confirm dialog
- **THEN** the dialog dismisses and no Firestore / FCM / session work runs

### Requirement: Profile "My Communities" menu row routes to the admin list (not a P6 placeholder toast)
The Profile screen SHALL emit `ProfileNavEvent.OpenMyCommunities` from the conditionally-visible "My Communities" menu row (added in P6 per
its `user-profile` spec, visible when `ownedCommunityCount > 0`), which the shell routes
to `community/admin/list`. The P6 implementation emitted a
`toast("Coming in Phase 7")` placeholder; this phase replaces the
toast with the real navigation. Mirrors iOS
`UserProfileVC.openMyCommunities()` lines 218–233.

#### Scenario: Tap My Communities menu row
- **WHEN** `ownedCommunityCount > 0` and user taps "My Communities"
- **THEN** shell navigates to `community/admin/list` (top-level, hides the bottom bar)

#### Scenario: My Communities hidden when user owns zero
- **WHEN** `ownedCommunityCount == 0`
- **THEN** the "My Communities" row is not in the menu list (the conditional insert from P6 is preserved)

### Requirement: "Communities" count pill shows the real AdminCommunitiesRepository value
The "Communities" count pill in `UserInfoHeader` SHALL display
`AdminCommunitiesRepository.fetchUserCommunitiesCount(currentUserCode)`
as a `Flow<Int>` that re-emits whenever the user's owned-community
set changes. The P6 implementation used a hardcoded `0` placeholder
via a `// TODO Phase 7` comment in `ProfileViewModel`; this phase
wires the real call. Mirrors iOS
`ProfileUserInfoCell.communityCount` lines 47–55.

#### Scenario: Profile header shows real community count
- **WHEN** the user has created 3 communities and `AdminCommunitiesRepository.fetchUserCommunitiesCount(userCode)` emits `3`
- **THEN** the pill displays "Communities: 3"

#### Scenario: Live update after creating a new community
**DEFERRED** — there is no in-app create-community flow on Android
(matches iOS). The pill updates whenever the count re-emits
(e.g. on `refreshOwnedCount(userCode)`); the flow stays in place for
any future write surface that calls it.

### Requirement: Profile menu dispatches six navigation events
The Profile screen SHALL emit `ProfileNavEvent` on each menu row tap:
`OpenNotifications`, `OpenEditBasicInfo`, `OpenPrivacyPolicy`,
`OpenMyCommunities` (when visible), `OpenLogout`, `OpenDeleteAccount`.

The previous P7 draft added `OpenCreateCommunity`. **Removed after
iOS audit.**

#### Scenario: All menu row taps dispatch the matching event
- **WHEN** user taps Notifications / Edit Basic Info / Privacy Policy / My Communities / Logout / Delete Account
- **THEN** the matching `ProfileNavEvent.Open*` fires and the shell router navigates to the corresponding destination (or shows the delete-account dialog)

## References
- [`docs/architecture.md`](../../../docs/architecture.md) — MVVM + UDF + `AsyncValue<T>` + `Channel<NavEvent>` + Hilt scopes
- `kornerz/Kornerz/HomeVC/ProfileVC/UserProfileVC.swift` — iOS source-of-truth for the menu list (lines 75–110) + logout handler (line 225) + delete-account handler (line 249)
- `kornerz/Kornerz/HomeVC/ProfileVC/ProfileUserInfoCell/ProfileUserInfoCell.swift` — iOS source-of-truth for the "Communities" count pill
- P6 spec `user-profile` (Phase 6 archive) — P6 implementation; this delta wires the real community count + surfaces the iOS logout/delete-account menu rows
- `docs/phases.md` P6 + P7 — phase ordering
