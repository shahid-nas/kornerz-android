# Android Korner detail Follow / Unfollow — iOS parity

## Why

The Korner detail screen's Follow / Unfollow capsule is a read-only
affordance on Android: tapping it shows a "coming soon" snackbar and the
`Fellow` collection is never written. iOS writes the same collection
(`FellowRepository.followCommunity` / `unfollowCommunity`), so a user who
follows a Korner on iOS sees the Korner in "Your Kornerz" — and a user who
follows on Android cannot, because the record is never created.

The write has to be byte-identical to iOS's, because both apps read the
same `Fellow` documents: the same `Fellow` collection, the same field
names (including the backend's `fellowAtive` misspelling), the same
random 8-character document id, and the same plain `set` (no merge, no
server timestamp).

## What Changes

- `FellowRepository` (core/network) gains the `Fellow` write path:
  `followCommunity`, `unfollowCommunity` and `fellowExists`, mirroring
  iOS `kornerz/Kornerz/Backend/Repositories/FellowRepository.swift`.
- `KornerFirestoreApi` (core/network) gains `followCommunity`,
  `unfollowCommunity` and `isFollowingCommunity`, which resolve the
  Korner's community document and its lookup keys
  (`communityId`, `communityName`, `communityNameEn`).
- `KornerzRepository` (feature/kornerz) exposes `followKorner` and
  `unfollowKorner`, resolving the signed-in user's `codeUser` — the
  identifier the `Fellow` records are keyed by.
- `KornerDetailViewModel` performs the write on `FollowClicked` and
  flips the capsule's Follow / Unfollow state on success, reporting a
  failure through the existing `ShowMessage` channel.
- The Korner detail read derives the follow state from a dedicated
  `Fellow` existence query, mirroring iOS
  `KornerDetailViewModel.checkIfFellowExists` — the follow state was
  previously derived from the community's member list, which iOS caps at
  50 entries and which therefore may not contain the signed-in user's
  own record.
- The "Tapping Follow reports back" scenario is replaced: the capsule
  now performs the write instead of reporting back.

## Capabilities

### New Capabilities

<!-- None — the change extends existing capabilities. -->

### Modified Capabilities

- `korner-detail`: the Follow / Unfollow capsule now performs the
  `Fellow` write instead of reporting "coming soon", and the follow state
  comes from a dedicated existence query.

## Impact

- `KornerzAndroid/core/network/.../fellow/FellowRepository.kt` — the
  `Fellow` write path.
- `KornerzAndroid/core/network/.../kornerz/KornerFirestoreApi.kt` — the
  follow / unfollow / exists operations and the follow-state read.
- `KornerzAndroid/feature/kornerz/.../KornerzRepository.kt` — the
  repository-level follow / unfollow API.
- `KornerzAndroid/feature/kornerz/.../detail/KornerDetailViewModel.kt` —
  the follow toggle.
- Firestore: writes to the `Fellow` collection, which iOS already reads
  and writes. No new collection, no new field, no schema change — the
  documents Android writes are identical to the ones iOS writes.
