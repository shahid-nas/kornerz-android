# Design — Android Korner detail Follow / Unfollow (iOS parity)

## Context

The Korner detail screen reads the `Fellow` collection but never writes it.
On iOS the same screen writes it, so the two apps share one follow graph:

```
Fellow/{random 8-char code}
  fellowId        = the document id
  fellowCommunity = the community
  fellowUserName  = the follower's codeUser
  fellowAtive     = true
```

iOS has **two** follow write paths and they disagree on the `fellowCommunity`
value:

| iOS path | writes | reads back with |
|---|---|---|
| SwiftUI `KornerDetailViewModel.followKorner` (`FellowRepository.swift:119`) | `korner.nameEn` (the `communityNameEn` field) | `fellowExists` → `in: [communityId, communityName]` |
| Legacy UIKit `CommunityDetailViewModel.followCommunity` | `community.name` (the `communityName` field) | `checkIsFollowingCommunityFor` → `== communityName` |

Neither iOS path is self-consistent: the SwiftUI path writes `nameEn` but
checks `communityName`, and the legacy path writes `communityName` but
deletes by `communityName` only. Meanwhile the "Your Kornerz" carousel on
both platforms resolves a follow back to a community by
`Communities.whereField("communityName", in: fellows' fellowCommunity)` —
i.e. by the **`communityName`** value, not `communityNameEn`.

So the value written must be `communityName` for the record to be
resolvable by the carousel on either platform, and the read/delete side
must match on **all** of the community's keys (`communityId`,
`communityName`, `communityNameEn`) so that a record written by *either*
iOS path is found and removed.

## Goals / Non-Goals

**Goals:**

- The document Android writes is byte-identical to the one iOS writes:
  same collection, same field names (including the `fellowAtive`
  misspelling), same random 8-character document id, same plain `set`
  (no merge, no server timestamp, no client timestamp).
- Follow and unfollow both work from the Korner detail screen, and the
  capsule reflects the stored state.
- A follow written on either platform is recognised and removable on
  either platform.

**Non-Goals:**

- Rewriting the existing `Fellow` records in Firestore — the read and
  delete paths already tolerate all three community keys, so legacy
  records keep working.
- The Kornerstones / Klouders member list is not re-read after a write;
  iOS does not re-read it either (it only flips `isFollowing`).

## Decisions

### 1. The write lives in `FellowRepository`, the Fellow collection's owner

`FellowRepository` (core/network/fellow) already owns the `Fellow`
collection read (`userFelllowsList`). The write goes there rather than
in `KornerFirestoreApi`, so the collection's field names and the
document-id scheme live in exactly one place.

**Alternative considered:** put the write in `KornerFirestoreApi` next to
the member read. Rejected — that class reads `Fellow` for the community
members but is not the collection's owner; splitting the write across two
classes would spread the field names.

### 2. The document id is a random 8-character code, and `fellowId` repeats it

iOS `FollowMember.from(userId:communityName:)` calls
`AppHelper.getCode(length: 8)` over `ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789`
and stores it as the document id *and* as the `fellowId` field. Android
reproduces both.

**Alternative considered:** let Firestore auto-generate the document id.
Rejected — the `fellowId` field would then differ from the document id,
and iOS's `fellowId` is the same code.

### 3. The plain `set` carries exactly four fields

iOS's `setData(from: fellow)` writes a `FollowMember`. Swift's synthesized
`Codable` omits `nil` optionals, and `FollowMember.from(...)` leaves
`dateTime` `nil` — so the document has no `fellowDateTime` field. Android
writes the same four fields.

**Alternative considered:** include `fellowDateTime` (the legacy iOS path
writes one). Rejected — the SwiftUI Korner detail path, the screen being
ported, does not; keeping the body minimal avoids a field the reference
implementation does not write.

### 4. Unfollow matches on every community key and deletes every match

iOS unfollows with `fellowCommunity == X` and deletes only the **first**
match. Since iOS's two write paths store different values for the same
community, a user who followed from both platforms holds two documents;
deleting one leaves the user "following", because the read is a `whereIn`
over the same key set. Android therefore matches on
`[communityId, communityName, communityNameEn]` and deletes **all**
matches.

**Alternative considered:** mirror iOS exactly (single key, first match).
Rejected — it leaves the follow state stuck when more than one record
exists, which is exactly the state the feature must be able to clear.

### 5. The follow state comes from a dedicated existence query

iOS `KornerDetailViewModel.fetchData` runs `checkIfFellowExists` as its
own query, separate from the follower fetch. Android previously derived
`isJoined` from the community's member list — a list iOS caps at 50
entries, which may not contain the signed-in user's own record. Android
now issues the dedicated `fellowExists` query in the same parallel block
as the nook and member reads.

**Alternative considered:** keep deriving it from the member list.
Rejected — it is the source of a wrong Follow / Unfollow label on a
busy Korner.

### 6. The signed-in user's `codeUser` identifies the follower

`Fellow.fellowUserName` holds the follower's `codeUser` on both
platforms (iOS `AppHelper.shared.loggedInUser?.userCode`). Android reads
it from the session via `SessionRepository`, the same source the
" Your Kornerz" read uses.

## Risks / Trade-offs

- **A `Fellow` document written by the iOS SwiftUI path stores
  `communityNameEn`, which the carousel cannot resolve** → Android's
  unfollow matches on all three keys, so such a record is still removed;
  the carousel gap is pre-existing on iOS and is out of scope here.
- **The read side now issues one extra query** (the existence check)
  → it runs in the same `coroutineScope` as the nook and member reads, so
  it adds no round-trip latency.
- **A concurrent double tap would create two `Fellow` documents** →
  the view model guards the toggle with an in-flight flag, the way iOS's
  progress HUD blocks a second tap.

## Migration Plan

No migration: `Fellow` documents written by Android are identical to the
ones iOS writes, so the two apps read each other's records with no
change. Rolling back the Android app leaves the records in place — they
are read by iOS exactly as before.

## Open Questions

None.
