# user-profile delta Specification

## Purpose
TBD - created by archiving change phase-8-premium-iap. Update Purpose after archive.

## Status: P8 delta adds an Android-only "Premium" row

The P8 delta adds a **"Premium"** menu row to the Profile menu. This row has **no iOS parity target**: `UserProfileVC.setupMenuList()` (`kornerz/Kornerz/HomeVC/ProfileVC/UserProfileVC.swift:75-110`) inserts `userDetail / notifications / completeRegistration / myData / reviewTutorial / privacyPolicy / logout / deleteAccount` — there is no premium cell — and the `TypeOfUserInfoCell` enum (`:11-22`) has no `.subscribe`, `.premium`, `.paywall`, or `.manageSubscription` case. iOS reaches the paywall only through two automatic gates.

The row is required anyway: without it, a free user who dismisses the automatic paywall has no way back to the plans screen for the rest of the process, which would make the dismissal affordance a dead end.

## MODIFIED Requirements

### Requirement: Profile menu mirrors iOS exactly
The Profile screen SHALL surface the following menu rows in the
canonical iOS order: **Premium**, Notifications, My Data, [conditional
My Communities, visible when `ownedCommunityCount > 0`], Privacy
Policy, Logout, Delete Account. Mirrors iOS
`UserProfileVC.setupMenuList()` (lines 79–108) row-for-row in this
phase's scope, **with one documented Android-only addition**: the
"Premium" row, which has no iOS counterpart (see the Status section
above).

There is **no** "Create Community" menu row. The iOS app does not
surface one (`UserProfileVC.setupMenuList()` lines 75–110 do not
insert a `createCommunity` cell) and the iOS
`CreateCommunityHomeVC` is unreachable dead code.

The "Premium" row SHALL be **always visible** — it is not gated on
any count — and SHALL sit **first** in the menu, ahead of
"Notifications". It SHALL show the current premium state as trailing
text, using the shared `ProfileMenuRow` `trailingText` slot
introduced in P8:

| Condition | Trailing text |
|---|---|
| `isUserPremiumNow == false` | `Premium` |
| `isUserPremiumNow == true` and `subscriptionType == "video_call_monthly"` | `Monthly` |
| `isUserPremiumNow == true` and `subscriptionType == "video_call_yearly_sub"` | `Yearly` |
| `isUserPremiumNow == true` and `subscriptionType` is null or unrecognized | `Active until {yyyy-MM-dd}` from `subscriptionExpiryInstant` |

The trailing text SHALL be derived from the **session** `User`
observed by `ProfileViewModel`, so it updates without an extra fetch
whenever the session flow re-emits.

#### Scenario: Open Create Community from Profile menu
**CANCELLED** — there is no Create Community menu row in the Android
port (matches iOS).

#### Scenario: Create Community menu row precedes "My Communities"
**CANCELLED** — no Create Community row exists.

#### Scenario: Premium row is always visible
- **WHEN** a free user with `ownedCommunityCount == 0` opens the Profile tab
- **THEN** the menu renders the Premium row followed by Notifications, My Data, Privacy Policy, Logout, and Delete Account

#### Scenario: Premium row labels a free user
- **WHEN** the session user's `isUserPremiumNow` is `false`
- **THEN** the Premium row's trailing text reads "Premium"

#### Scenario: Premium row labels a monthly subscriber
- **WHEN** the session user's `isUserPremiumNow` is `true` and `subscriptionType == "video_call_monthly"`
- **THEN** the Premium row's trailing text reads "Monthly"

#### Scenario: Premium row falls back to the expiry date
- **WHEN** the session user's `isUserPremiumNow` is `true` and `subscriptionType` is null
- **THEN** the Premium row's trailing text reads "Active until {yyyy-MM-dd}" derived from `subscriptionExpiryInstant`

#### Scenario: Premium row navigates to the plans screen
- **WHEN** the user taps the Premium row
- **THEN** `ProfileNavEvent.OpenPremium` is emitted and the shell routes to `SubscriptionRoute.Plans`

### Requirement: Profile menu dispatches six navigation events
The Profile screen SHALL emit `ProfileNavEvent` on each menu row tap:
`OpenPremium`, `OpenNotifications`, `OpenEditBasicInfo`,
`OpenPrivacyPolicy`, `OpenMyCommunities` (when visible), `OpenLogout`,
`OpenDeleteAccount`.

The previous P7 draft added `OpenCreateCommunity`. **Removed after
iOS audit.** P8 adds `OpenPremium`, which — unlike `OpenCreateCommunity`
— **is** backed by a live row and is not dead code.

#### Scenario: All menu row taps dispatch the matching event
- **WHEN** user taps Premium / Notifications / My Data / Privacy Policy / My Communities / Logout / Delete Account
- **THEN** the matching `ProfileNavEvent.Open*` fires and the shell router navigates to the corresponding destination (or shows the delete-account dialog)

#### Scenario: Premium is wired end to end
- **WHEN** `ProfileViewModel.onPremiumTap()` is invoked
- **THEN** `ProfileNavEvent.OpenPremium` is emitted on the `navEvents` channel and `AppShell.profileGraph` navigates to `SubscriptionRoute.Plans`

## ADDED Requirements

### Requirement: ProfileMenuRow accepts an optional trailing text slot
The shared `ProfileMenuRow` composable in `feature/profile/.../components/ProfileMenuRow.kt` SHALL accept a new parameter `trailingText: String? = null`. When non-null, the row SHALL render the string at the trailing edge of the 73-pt rounded-16 card, vertically centered, right-aligned, with a trailing 16-pt inset mirroring the row's leading 16-pt inset; when null, no trailing element is rendered.

The added parameter SHALL have a `null` default so all six existing call sites compile and render unchanged. The row's existing shape (73-pt height, 16-pt corner radius, `#F2F4F6` background, 24-pt icon tinted `#353446`, 16-sp bold `#353446` title) SHALL NOT change.

This is the first shared-component signature change P0–P7 has required.

#### Scenario: Null default keeps existing rows unchanged
- **WHEN** an existing call site renders `ProfileMenuRow` without passing `trailingText`
- **THEN** the row renders exactly as it did before P8, with no trailing element and no extra spacing

#### Scenario: Trailing text renders when supplied
- **WHEN** `ProfileMenuRow` is called with `trailingText = "Monthly"`
- **THEN** "Monthly" renders right-aligned at the trailing edge of the card, vertically centered

#### Scenario: All existing call sites still compile
- **WHEN** the Profile menu is rebuilt
- **THEN** the six pre-existing `ProfileMenuRow` call sites require no modification

## References

- `kornerz/Kornerz/HomeVC/ProfileVC/UserProfileVC.swift:75-110` — iOS `setupMenuList()`, the negative finding that no premium row exists
- `kornerz/Kornerz/HomeVC/ProfileVC/UserProfileVC.swift:11-22` — iOS `TypeOfUserInfoCell`, the negative finding that no premium cell type exists
- `feature/profile/src/main/kotlin/com/kornerz/feature/profile/view/ProfileContract.kt` — `ProfileState` / `ProfileEvent` / `ProfileNavEvent`
- `feature/profile/src/main/kotlin/com/kornerz/feature/profile/components/ProfileMenuRow.kt` — the component extended by P8