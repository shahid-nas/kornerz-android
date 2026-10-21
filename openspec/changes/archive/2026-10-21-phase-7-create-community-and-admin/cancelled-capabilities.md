# Cancelled capabilities (phase-7, after iOS audit)

These two capabilities were scoped in the P7 proposal, then **cancelled** after an
audit of the iOS source tree. They carry no `## ADDED Requirements`, so they are not
delta specs and cannot be synced into `openspec/specs/`. Their rationale is preserved
here for future reference.


---

## create-community-wizard

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


---

## firebase-storage-upload

## Status: CANCELLED after iOS audit

The `CommunityStorageApi.uploadCover(...)` v4 signed-URL upload helper
was scoped as the second concrete consumer of the v4 pipeline (the
first being P6's `UserStorageApi.uploadAvatar`). After the iOS audit
showed that iOS does not surface a Create Community flow, the
`CommunityStorageApi` had no consumer and was removed from the
Android port.

The P6 `UserStorageApi.uploadAvatar` remains the sole v4 signed-URL
consumer in the codebase. Future phases that need a cover / image
upload helper can reintroduce the `CommunityStorageApi` (or a more
general `MediaStorageApi`) following the same `StorageSignedUrlClient`
+ `runCatching` pattern.

The P7 proposal's `## ADDED Requirements` in this spec file are
treated as cancelled. The P6 `firebase-storage-avatar-upload` spec
is the canonical reference for the v4 upload pattern.
