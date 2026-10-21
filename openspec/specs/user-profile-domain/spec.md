# user-profile-domain Specification

## Purpose
TBD - created by archiving change phase-6-profile-and-notifications. Update Purpose after archive.
## Requirements
### Requirement: User gains image URL projection fields from iOS source
The `User` data class in `core/model` SHALL add three derived properties that mirror iOS `Models/User.swift` lines 61–84:
- `val imageURL: String?` — returns `firebaseImageUrl` if non-empty, else `IPFS.baseUrl + image`, else `null`. Note: the Android port returns `null` for IPFS-backed values because the v4 Firebase Storage migration (`FIREBASE_STORAGE_MIGRATION.md`) keeps Firebase URLs only; the Android implementation follows the iOS source-of-truth logic but the IPFS branch is unreachable in practice.
- `val thumbnailURL: String?` — returns `firebaseThumbnailUrl` if non-empty, else `firebaseImageUrl` if non-empty, else `IPFS.baseUrl + (thumbnail ?: image)`, else `null`. Same IPFS-branch caveat.
- `val thumbnailType: ImageType` — returns `ImageType.Url(thumbnailURL)` if `thumbnailURL != null`, else `ImageType.Resource(R.drawable.ic_user_placeholder)`.

Mirrors iOS `User.thumnailURL`, `User.imageURL`, `User.thumbnailType` verbatim.

#### Scenario: Firebase URL only
- **WHEN** `User.firebaseImageUrl = "https://firebasestorage.../avatar.jpg"` and `User.image = null`
- **THEN** `User.imageURL = "https://firebasestorage.../avatar.jpg"`

#### Scenario: Both Firebase URLs set
- **WHEN** `User.firebaseImageUrl = "https://firebasestorage.../avatar.jpg"` and `User.firebaseThumbnailUrl = "https://firebasestorage.../thumb.jpg"`
- **THEN** `User.thumbnailURL = "https://firebasestorage.../thumb.jpg"`

#### Scenario: Fallback to user placeholder
- **WHEN** `User.firebaseImageUrl = null`, `User.firebaseThumbnailUrl = null`, `User.image = null`, `User.thumbnail = null`
- **THEN** `User.thumbnailType = ImageType.Resource(R.drawable.ic_user_placeholder)`

### Requirement: User model fields are unchanged from P1 except for the derived properties
The P1 `User` schema (the fields persisted in `Users/{userCode}`) SHALL NOT be modified by this spec. The full premium/subscription fields (`isUserPremium`, `subscriptionExpiryDate`, `subscriptionType`, `subscriptionPlatform`) remain P8 work per `phases.md`. Only the three derived `imageURL` / `thumbnailURL` / `thumbnailType` properties are added in this phase.

#### Scenario: Existing P1 flows unaffected
- **WHEN** P1 signup / login / session flows read/write `User`
- **THEN** the field set, `equals`, `hashCode`, `copy()`, and DataStore serialization behave identically to P1

