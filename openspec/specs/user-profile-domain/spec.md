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

**P8 clarification (no behavior change):** the P6 requirement text
describes `thumbnailType` as a derived projection. In the shipped
`User.kt` it is a plain nullable field carried on the class, not a
computed property. The requirement text above is retained verbatim
from P6 so the archive merge is faithful; the P8 clarification is
recorded here so a future reader is not misled. **P8 does not change
this requirement's behavior** and `thumbnailType` is out of P8 scope.

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
The P1 `User` schema SHALL NOT change its field set in this phase: no field is added, removed, renamed, or retyped. The fields are persisted in `Users/{email}` — the Firestore **document key** is the user's **email**, not the `codeUser` field, which is a separate field on the document holding the public-facing user code.

The four premium/subscription fields (`isUserPremium` via
`@SerialName`, `subscriptionExpiryDate`, `subscriptionType`,
`subscriptionPlatform`) were declared in P1 as P8 work and are now
**live**: `SubscriptionFirestoreApi` writes three of them, and
`UserFirestoreApi.toUserOrNull()` maps all four on read (see the
`premium-entitlement` capability).

The `isPremium` field's KDoc SHALL be corrected. It previously read
"they are populated by the billing flow introduced in P8", which was
aspirational. It SHALL now state that the field is a
shared-document compatibility value written once at signup by
`FirestoreAuthApi.signUp` (`"isUserPremium" to false`, `:90`) and
**not** the gate input — premium is derived from
`subscriptionExpiryDate`. This mirrors iOS, where
`UserRepository.setUserPremium` (`:170-175`) never writes the field
and `AppHelper.isUserPremium` (`:39-45`) ignores it.

Only the three derived `imageURL` / `thumbnailURL` / `thumbnailType`
properties (P6) and the two derived premium properties added by P8
(`isUserPremiumNow`, `subscriptionExpiryInstant`) are added; neither
is persisted, so both are invisible to `equals` / `hashCode` / DataStore
serialization beyond their read-only access.

**Document-key correction:** the prior text of this requirement read
"the fields persisted in `Users/{userCode}`". That is wrong and is
corrected here to `Users/{email}`. The repo-wide convention is that the
Firestore **document key** is the user's email (`UserFirestoreApi.kt:12-27`
KDoc; `fetchByEmail` at `:38-46`; `FirestoreAuthApi.signUp` at `:75`),
while `codeUser` is a separate field on the document holding the
public-facing user code. `UserFirestoreApi.fetchByCode` resolves the
code to a document via `whereEqualTo("codeUser", userCode).limit(1)`,
never by using the code as the key.

#### Scenario: Existing P1 flows unaffected
- **WHEN** P1 signup / login / session flows read/write `User`
- **THEN** the field set, `equals`, `hashCode`, `copy()`, and DataStore serialization behave identically to P1

#### Scenario: The premium KDoc no longer overstates
- **WHEN** a reader inspects the `isPremium` field's KDoc
- **THEN** it states that the field is written at signup for shared-document compatibility and that `isUserPremiumNow` — not `isPremium` — is the premium decision input

#### Scenario: Document key is the email, not the code
- **WHEN** `SubscriptionFirestoreApi.setSubscription` writes the premium fields
- **THEN** it targets `Users/{email}`, and `codeUser` is read as a field on the document rather than used as the document key
