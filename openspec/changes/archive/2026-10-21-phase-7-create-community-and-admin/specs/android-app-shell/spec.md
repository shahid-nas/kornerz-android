# android-app-shell delta Specification

## Purpose
TBD - created by archiving change phase-7-create-community-and-admin. Update Purpose after archive.

## Status: P7 delta reduced after iOS audit

The P7 delta registered a `CommunityRoute` sealed class with two
cases — `Create` (the create-community wizard) and `AdminList` (the
admin-owned-communities list). After the iOS audit, only
`AdminList` survives. The wizard route was never registered on the
shell, no `Create` composable was wired, and the bottom-bar-hidden
regex was tightened to `community/admin/list` only.

The `userDeletedHisAccount` event-bus routing is unchanged from P6.

## MODIFIED Requirements

### Requirement: Five-tab bottom navigation shell

The app SHALL render a bottom navigation bar with exactly 5 tabs in
this left-to-right order: Home, Kornerz, Nooks, Loops, Klouders. Each
tab SHALL be reachable by tapping its icon. The currently selected tab
SHALL be visually distinguished. The bottom bar SHALL remain visible
while navigating within a tab's nested graph. The Nooks tab SHALL host
a nested `NavHost` with the `nooks/list` destination (introduced in
P4, `nooks-tab` capability) as its start destination, `nooks/{nookId}`
(introduced in P4, `nook-detail` capability) as a secondary
destination reachable from any `NookCard` or `NookTile` tap, and
`videocall/{sessionId}` (introduced in P9, `feature-videocall`
capability) as a tertiary destination reachable from the Join button
on a `LiveKornerCard` (introduced in P9, `nooks-tab` capability
amendment). The Loops tab SHALL host a nested `NavHost` with the
`loops/list` destination (introduced in P5, `loops-tab` capability)
as its start destination, `loops/{loopId}` (introduced in P5,
`loop-detail` capability) as a secondary destination reachable from
any `LoopItemView` tap, `loops/{loopId}/comment` (introduced in P5,
`loop-comments` capability) as a `dialog` route reachable from the
loop detail's add-comment row, and `loops/{loopId}/reply/{commentId}`
(introduced in P5, `loop-comments` capability) as a tertiary
destination reachable from any `LoopCommentView` `onReply` affordance.
The Profile tab SHALL host a nested `NavHost` with `profile/view`
(introduced in P6, `user-profile` capability) as its start
destination, and `profile/edit-basic-info`,
`profile/edit-basic-info/{field}` (dialog),
`profile/edit-basic-info/{field}/success`, `profile/privacy-policy`
as secondary destinations reachable from the corresponding menu rows
(introduced in P6, `user-profile` + `profile-edit-basic-info` +
`privacy-policy-pdf` capabilities). The shell SHALL register one
additional top-level, full-screen (bottom-bar-hidden) route alongside
the shell graph: `community/admin/list`.

The previous P7 draft also registered a `community/create` route
pushed from the Profile tab. **That route is removed** after the iOS
audit — iOS `UserProfileVC` does not surface a "Create Community"
menu row, and the iOS `CreateCommunityHomeVC` is unreachable dead
code. Android now mirrors iOS exactly and does not register a
`community/create` route.

#### Scenario: Five-tab shell renders
- **WHEN** the shell composable is first composed for an authenticated session
- **THEN** the bottom bar renders with exactly 5 tabs in the order Home, Kornerz, Nooks, Loops, Profile

#### Scenario: Nooks tab nested navigation
- **WHEN** user taps the Nooks tab and then taps a `NookCard`
- **THEN** the bottom bar remains visible and the `nooks/{nookId}` destination renders inside the tab's nested NavHost

#### Scenario: Loops tab nested navigation
- **WHEN** user taps the Loops tab and then taps a `LoopItemView`
- **THEN** the bottom bar remains visible and the `loops/{loopId}` destination renders inside the tab's nested NavHost

#### Scenario: Loops dialog route preserves bottom bar
- **WHEN** user opens `loops/{loopId}/comment` and `loops/{loopId}/reply/{commentId}` dialog routes
- **THEN** the bottom bar stays visible behind the dialog scrim

#### Scenario: Profile tab nested navigation
- **WHEN** user taps the Profile tab and then taps Edit Basic Info / Privacy Policy / Change Basic Info (Field)
- **THEN** the bottom bar stays visible and the matching nested profile destination renders inside the Profile tab's nested NavHost

#### Scenario: Admin list route hides bottom bar (full-screen)
- **WHEN** user navigates to `community/admin/list` from Profile
- **THEN** the bottom bar fades out and the admin list renders full-screen (mirrors iOS push from `UserProfileVC.openMyCommunities`)

#### Scenario: Back from admin list returns to Profile
- **WHEN** user taps the back button in the top app bar of `community/admin/list`
- **THEN** shell pops back to the Profile tab

#### Scenario: Account deletion routes back to auth graph
- **WHEN** the shared `userDeletedHisAccount` event bus emits (introduced in P6, `account-deletion` capability)
- **THEN** the root NavHost clears the shell graph and switches to the auth graph, matching the existing logout behavior

## ADDED Requirements

### Requirement: CommunityRoute sealed class models the P7 admin list
The shell's navigation contract SHALL expose a `CommunityRoute` sealed
class with one case: `AdminList` (maps to `community/admin/list`).
The `AppShell` composable SHALL register this route as a top-level
route outside any tab's nested NavHost, and SHALL include it in the
set of routes that hide the bottom navigation bar.

The previous P7 draft added a `Create` case. **Cancelled after iOS
audit.**

#### Scenario: CommunityRoute.AdminList is registered
- **WHEN** the shell composable is composed
- **THEN** the route `community/admin/list` is registered as a `composable("community/admin/list")` top-level destination and hides the bottom bar

## References
- [`docs/architecture.md`](../../../docs/architecture.md) — MVVM + UDF + `AsyncValue<T>` + `Channel<NavEvent>` + Hilt scopes
- `kornerz/Kornerz/HomeVC/ProfileVC/UserProfileVC.swift` — iOS source-of-truth for the profile menu + the `openMyCommunities()` push (the `openCreateCommunity` push is dead code in iOS — see `tasks.md` for the audit)
- P6 spec `android-app-shell` modification (Phase 6 archive) — Profile nested NavHost + `userDeletedHisAccount` event routing; this delta extends it with the P7 admin list route
- `docs/phases.md` P6 + P7 — phase ordering
