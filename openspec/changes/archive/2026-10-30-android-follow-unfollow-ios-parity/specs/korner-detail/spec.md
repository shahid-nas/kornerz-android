# korner-detail Specification

## References

- [`docs/architecture.md`](../../../../docs/architecture.md) — MVVM + Repository + UDF, `AsyncValue<T>`, module boundaries.
- `kornerz/Kornerz/Backend/Repositories/FellowRepository.swift` — iOS source-of-truth for the `Fellow` collection's follow / unfollow / exists calls.
- `kornerz/Kornerz/SwiftUI/Dashboard/Korner/KornerDetail/KornerDetailViewModel.swift` — iOS source-of-truth for the Korner detail screen's follow toggle (`followTapped`, `followKorner`, `unfollowKorner`, `checkIfFellowExists`).
- `kornerz/Kornerz/Models/FollowMember.swift` — iOS source-of-truth for the `Fellow` document shape.
- [`openspec/changes/android-follow-unfollow-ios-parity/proposal.md`](../proposal.md) — capability rationale for the Follow / Unfollow parity work.
- [`openspec/changes/android-follow-unfollow-ios-parity/design.md`](../design.md) — the write shape, the community-key matching and the follow-state read.

## MODIFIED Requirements

### Requirement: Korner detail header renders the theme-colour hero
The Korner detail screen SHALL render a `KornerDetailHeader` composable at the top, mirroring iOS `SwiftUI/Dashboard/Korner/KornerDetail/KornerDetailHeaderView.swift`. The header SHALL be a solid block in the Korner's theme colour (`korner.themeColor`, falling back to the brand Coral swatch) and SHALL contain, in the iOS arrangement: a back chevron on the leading edge, the Korner name in Satoshi Medium 20sp white beneath it, the Korner cover as a 125-dp triangle-clipped image (15-dp corner radius, top-right corner) on the trailing edge, and a bottom action row holding a Follow / Unfollow mini-capsule (Charcoal `#2E2F3E` fill, white 12sp label) next to an "About" label with a 12-dp chevron. The header SHALL NOT render a 16:9 cover banner with a scrim, nor a full-width Join / Joined button.

#### Scenario: Header renders the hero
- **WHEN** the Korner detail screen renders for a Korner with theme colour `#E27D5A`
- **THEN** the header is a solid `#E27D5A` block
- **AND** the Korner name is shown in white beneath the back chevron
- **AND** the cover is shown as a 125-dp triangle-clipped image on the trailing edge
- **AND** the Follow capsule and the "About ⌄" affordance are shown on the leading edge of the bottom action row

#### Scenario: Follow reflects the joined state
- **WHEN** the signed-in user follows the Korner (`korner.isJoined == true`)
- **THEN** the header's capsule reads "Unfollow"
- **AND** it reads "Follow" otherwise

#### Scenario: Tapping Follow writes the Fellow record
- **WHEN** the user taps the Follow / Unfollow capsule
- **THEN** the screen writes the follow record to the `Fellow` collection, mirroring iOS `FellowRepository.followCommunity` — a document whose id is a random 8-character code, carrying `fellowId` (the same code), `fellowCommunity` (the community), `fellowUserName` (the signed-in user's `codeUser`) and `fellowAtive` (`true`), written with a plain `set` (no merge, no server timestamp)
- **AND** the header's capsule reads "Unfollow" once the write succeeds
- **AND** the About sheet's Follow / Unfollow capsule reflects the same state

#### Scenario: Tapping Unfollow removes the Fellow record
- **WHEN** the signed-in user follows the Korner and taps the Follow / Unfollow capsule
- **THEN** the screen deletes the signed-in user's `Fellow` record for that community, mirroring iOS `FellowRepository.unfollowCommunity`
- **AND** the header's capsule reads "Follow" once the delete succeeds

#### Scenario: A failed follow write reports back
- **WHEN** the follow or unfollow write fails
- **THEN** the screen emits a `ShowMessage` navigation event
- **AND** the capsule keeps its previous Follow / Unfollow state

#### Scenario: Tapping the back chevron pops the screen
- **WHEN** the user taps the header's back chevron
- **THEN** the screen emits `NavigateBack`
- **AND** the shell pops the Korner detail off the Kornerz tab's nested NavHost

## ADDED Requirements

### Requirement: Korner detail derives the follow state from the Fellow collection
The Korner detail read SHALL derive `korner.isJoined` from the signed-in
user's own `Fellow` record for the Korner, mirroring iOS
`KornerDetailViewModel.checkIfFellowExists`. The check SHALL be a dedicated
existence query against the `Fellow` collection, NOT a scan of the
Korner's member list — that list is capped at 50 entries and therefore may
not contain the signed-in user's own record. The community SHALL be matched
by every key it is known by (`communityId`, `communityName`,
`communityNameEn`) so that a follow written by either iOS path is
recognised.

#### Scenario: The follow state comes from a dedicated existence query
- **WHEN** the Korner detail is read for the signed-in user
- **THEN** the follow state is resolved by querying the `Fellow` collection for a record whose `fellowUserName` is the signed-in user's `codeUser` and whose `fellowCommunity` matches one of the Korner's community keys
- **AND** the query runs alongside the Korner's nook and member reads

#### Scenario: A follow written on iOS is recognised
- **WHEN** the signed-in user follows the Korner on the iOS app and then opens the Korner detail on Android
- **THEN** `korner.isJoined` is `true`
- **AND** the header's capsule reads "Unfollow"
