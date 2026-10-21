# create-community-wizard Specification

## Purpose
TBD - created by archiving change phase-7-create-community-and-admin. Update Purpose after archive.

## Status: CANCELLED after iOS audit

The Phase 7 proposal and this spec were written under the assumption that
iOS `UserProfileVC` surfaces a "Create Community" menu row and pushes
`CreateCommunityHomeVC` from the Profile menu. **That assumption was
wrong.** The actual iOS source (`kornerz/Kornerz/HomeVC/ProfileVC/
UserProfileVC.swift`) shows:

- `UserProfileVC.setupMenuList()` (lines 75–110) inserts only
  `userDetail / notifications / completeRegistration / myData /
  reviewTutorial / privacyPolicy / logout / deleteAccount`. No
  `createCommunity` cell is ever inserted.
- The `createCommunity` branch in `didSelectRowAt` (line 246) is
  unreachable dead code — no menu row has `cellType == .createCommunity`.
- The `CreateCommunityHomeVC / NewCommunityNameVC /
  NewCommunityDescriptionVC / SelectCommunityThemeVC` view controllers
  exist in the iOS source tree but are only referenced from
  `UserProfileVC.swift:247` (the dead branch) and from each other
  (the wizard's internal navigation). They are unreachable from the
  running iOS app.

The Android port removed the wizard code (VMs, screens, view
controllers, nav graph) and the spec's `## ADDED Requirements` are
treated as cancelled. No `community/create` shell route is
registered. No `OpenCreateCommunity` `ProfileNavEvent` exists. The
`CommunitiesRepository.create` / `CommunityStorageApi.uploadCover`
write surfaces are removed because they have no caller.

If a future phase reintroduces community creation on Android, the
wizard should be re-scoped with iOS parity in mind — i.e. the entry
point (if any) should be defined first, not the wizard surface.
