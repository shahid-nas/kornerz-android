# Tasks — Android Korner detail Follow / Unfollow (iOS parity)

## 1. `Fellow` write path (core/network)

- [x] 1.1 Add `followCommunity(userCode, community)` to `FellowRepository` —
      `Fellow/{random 8-char code}` written with a plain `set` carrying
      `fellowId`, `fellowCommunity`, `fellowUserName` and `fellowAtive`
      (mirrors iOS `FellowRepository.followCommunity`).
- [x] 1.2 Add `unfollowCommunity(userCode, communities)` to
      `FellowRepository` — query `Fellow` by `fellowCommunity` +
      `fellowUserName` and delete every match (mirrors iOS
      `FellowRepository.unfollowCommunity`, widened to the community's full
      key set).
- [x] 1.3 Add `fellowExists(userCode, communities)` to `FellowRepository` —
      the existence query (mirrors iOS `FellowRepository.fellowExists`).
- [x] 1.4 Reproduce iOS's document-id scheme — a random 8-character code
      over `ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789`, written both as the
      document id and as the `fellowId` field.

## 2. Community resolution (core/network)

- [x] 2.1 Add `followCommunity(kornerId, userCode)`,
      `unfollowCommunity(kornerId, userCode)` and
      `isFollowingCommunity(kornerId, userCode)` to `KornerFirestoreApi`,
      resolving the Korner's community document and its lookup keys
      (`communityId`, `communityName`, `communityNameEn`).
- [x] 2.2 Derive `korner.isJoined` from the dedicated `fellowExists`
      query, in the same parallel read block as the nook and member reads
      (mirrors iOS `checkIfFellowExists`).

## 3. Repository layer (feature/kornerz)

- [x] 3.1 Add `followKorner(kornerId)` and `unfollowKorner(kornerId)` to
      `KornerzRepository`, resolving the signed-in user's `codeUser` from
      the session.
- [x] 3.2 Fail with a clear error when no user is signed in (mirrors iOS's
      `guard let userId = AppHelper.shared.loggedInUser?.userCode`).

## 4. Korner detail screen (feature/kornerz)

- [x] 4.1 Replace the "coming soon" `FollowClicked` branch in
      `KornerDetailViewModel` with the real toggle — follow when not
      following, unfollow when following.
- [x] 4.2 Flip the capsule state on a successful write and report a
      failure through the existing `ShowMessage` channel.
- [x] 4.3 Guard against a concurrent double tap creating a duplicate
      `Fellow` document (iOS blocks it with its progress HUD).
- [x] 4.4 Confirm the About sheet's capsule reflects the same follow state
      (it already reads `detail.korner.isJoined`).

## 5. Verification

- [x] 5.1 Compile `:core:network` and `:feature:kornerz`.
- [x] 5.2 Compile `:app` (dev debug) to confirm the whole graph builds.
- [x] 5.3 Review the write against iOS field-for-field: collection, field
      names (including the `fellowAtive` misspelling), document id,
      set-vs-merge, and the absence of a timestamp.
