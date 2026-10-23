# premium-entitlement Specification

## Purpose
TBD - created by archiving change phase-8-premium-iap. Update Purpose after archive.
## Requirements
### Requirement: Premium state is derived from subscriptionExpiryDate, not from isUserPremium
The `User` data class in `core/model` SHALL gain two derived properties that mirror iOS `AppHelper.isUserPremium` (`kornerz/Kornerz/Helper/AppHelper.swift:39-45`) exactly:

```kotlin
val subscriptionExpiryInstant: Instant?
    get() = subscriptionExpiryDate?.let { runCatching { Instant.parse(it) }.getOrNull() }

val isUserPremiumNow: Boolean
    get() = subscriptionExpiryInstant?.isAfter(Clock.System.now()) == true
```

`isUserPremiumNow` SHALL be the **single** input to the premium gate, the Profile row's trailing label, and any entitlement decision. The raw `isPremium` field (`@SerialName("isUserPremium")`) SHALL NOT be read for any premium decision.

This mirrors iOS for two independent reasons:
1. iOS's `UserRepository.setUserPremium(expiryDate:type:)` (`kornerz/Kornerz/Backend/Repositories/UserRepository.swift:165-178`) writes only `subscriptionExpiryDate`, `subscriptionType`, and `subscriptionPlatform` — it **never writes `isUserPremium`**. The bit is written once at signup (`SignupViewModel.swift:171` → `isUserPremium: false`) and never updated, so it is permanently `false` in Firestore for every subscriber on both platforms.
2. iOS's own `AppHelper.isUserPremium` derives from `subscriptionExpiryDate > Date()` and ignores the field entirely.

`subscriptionExpiryInstant` SHALL return `null` for a missing or unparseable `subscriptionExpiryDate` rather than throwing, so a malformed value written by the other platform degrades to "free" instead of crashing the Profile screen.

The KDoc on the `isPremium` field SHALL be updated to state that it is a shared-document compatibility field written at signup and not the gate input.

#### Scenario: Future expiry means premium
- **WHEN** `subscriptionExpiryDate = "2026-12-01T00:00:00Z"` and the current time is 2026-10-22
- **THEN** `isUserPremiumNow` is `true`

#### Scenario: Past expiry means not premium
- **WHEN** `subscriptionExpiryDate = "2026-01-01T00:00:00Z"` and the current time is 2026-10-22
- **THEN** `isUserPremiumNow` is `false`

#### Scenario: Null expiry means not premium
- **WHEN** `subscriptionExpiryDate = null`
- **THEN** `isUserPremiumNow` is `false` and `subscriptionExpiryInstant` is `null`

#### Scenario: Malformed expiry degrades to free, not to a crash
- **WHEN** `subscriptionExpiryDate = "not-a-date"`
- **THEN** `subscriptionExpiryInstant` is `null` and `isUserPremiumNow` is `false`, with no exception thrown

#### Scenario: The raw isUserPremium bit is not consulted
- **WHEN** a user document has `isUserPremium = false` and `subscriptionExpiryDate` in the future
- **THEN** `isUserPremiumNow` is `true`

### Requirement: UserFirestoreApi maps all four premium fields on read
`UserFirestoreApi.toUserOrNull()` in `feature/profile/.../data/network/UserFirestoreApi.kt` SHALL map `isUserPremium`, `subscriptionExpiryDate`, `subscriptionType`, and `subscriptionPlatform` when decoding a `Users/{email}` document.

Today only `FirestoreAuthApi.fetchUser` (`core/network/.../auth/FirestoreAuthApi.kt:190`) maps `isUserPremium`; `UserFirestoreApi.toUserOrNull()` (`:156-177`) drops all four, so every Profile read returns a `User` that appears permanently free. Closing this is a hard prerequisite for the phase: without it the Profile row's trailing label and the gate's entitlement input are both wrong for every read.

#### Scenario: Profile read carries the subscription fields
- **WHEN** a `Users/{email}` document has `subscriptionExpiryDate = "2026-12-01T00:00:00Z"`, `subscriptionType = "video_call_yearly_sub"`, `subscriptionPlatform = "iOS"`
- **THEN** the decoded `User` has all three values populated and `isUserPremiumNow` evaluates to `true` when the date is in the future

#### Scenario: Absent fields decode to safe defaults
- **WHEN** a `Users/{email}` document has none of the four fields
- **THEN** the decoded `User` has `isPremium = false` and all three subscription fields `null`, and `isUserPremiumNow` is `false`

### Requirement: Entitlement write is a three-field updateData keyed by email
`SubscriptionFirestoreApi` (`@Singleton`, `feature/subscription/data/network/`) SHALL expose:

```kotlin
suspend fun setSubscription(
    email: String,
    expiryDate: Instant,
    planId: String,
    platform: String,
): Result<Unit>

suspend fun clearSubscription(email: String): Result<Unit>
```

`setSubscription` SHALL issue a Firestore **`updateData`** on `Users/{email}` writing exactly three keys:

| Kotlin arg | Firestore key | Value |
|---|---|---|
| `expiryDate` | `subscriptionExpiryDate` | Firestore `Timestamp` |
| `planId` | `subscriptionType` | `SubscriptionProductType.rawValue` (`video_call_monthly` or `video_call_yearly_sub`) |
| `platform` | `subscriptionPlatform` | `"Android"` |

It SHALL NOT write `isUserPremium`, and it SHALL NOT use `setData` — `updateData` mirrors iOS `UserRepository.swift:171` and patching rather than replacing guarantees the other ~20 user fields on the document are untouched.

The document SHALL be resolved **by email**, matching the repo-wide `Users/{email}` document-key convention (`UserFirestoreApi.kt:12-27` KDoc; `fetchByEmail` at `:38-46`). Callers holding only a `userCode` SHALL resolve it through the existing `UserProfileRepository.requireEmailForUserCode()` path. It SHALL NOT key on `codeUser`, and SHALL NOT use the lowercase `users/{firebaseUid}` path that `FirestoreAuthApi.updateFcmToken` (`:153-168`) uses — that is a pre-existing FCM inconsistency, not a convention to copy.

`setSubscription` SHALL implement iOS's identical-expiry no-op guard (`UserRepository.swift:168`: `guard user.subscriptionExpiryDate?.dateValue() != expiryDate`): when the stored `subscriptionExpiryDate` already equals the incoming value, it SHALL return `Result.success(Unit)` without issuing a write.

`clearSubscription` SHALL remove the same three keys via Firestore `FieldValue.delete()`, mirroring iOS `UserRepository.setUserFree()` (`:149-163`).

#### Scenario: Purchase writes three fields
- **WHEN** `setSubscription("a@b.com", Instant.parse("2026-12-01T00:00:00Z"), "video_call_yearly_sub", "Android")` completes
- **THEN** the `Users/a@b.com` document has `subscriptionExpiryDate`, `subscriptionType = "video_call_yearly_sub"`, and `subscriptionPlatform = "Android"`, and `isUserPremium` is unchanged

#### Scenario: Existing fields survive the write
- **WHEN** `setSubscription` runs against a user document that also holds `email`, `name`, `nickname`, and `codeUser`
- **THEN** those four fields are unchanged after the write

#### Scenario: Identical expiry is a no-op
- **WHEN** the stored `subscriptionExpiryDate` already equals the incoming expiry
- **THEN** `setSubscription` returns `Result.success(Unit)` and issues no Firestore write

#### Scenario: userCode is resolved to the email key
- **WHEN** the caller holds only a `userCode` and resolves it to `a@b.com`
- **THEN** the write targets `Users/a@b.com`, not `Users/{userCode}` and not the lowercase `users/{uid}` path

#### Scenario: Clear removes all three fields
- **WHEN** `clearSubscription("a@b.com")` completes
- **THEN** `subscriptionExpiryDate`, `subscriptionType`, and `subscriptionPlatform` are all deleted from the document, and `isUserPremiumNow` evaluates to `false` on the next read

### Requirement: SubscriptionRepository grant and revoke entitlement
`SubscriptionRepository` (`@Singleton`, `feature/subscription/data/`) SHALL expose:

| Member | Signature |
|---|---|
| `observeCatalog` | `suspend fun (): AsyncValue<List<SubscriptionProduct>>` |
| `grantEntitlement` | `suspend fun (email: String, plan: SubscriptionProductType, expiry: Instant): Result<Unit>` |
| `revokeEntitlement` | `suspend fun (email: String): Result<Unit>` |
| `refreshCachedUser` | `suspend fun (user: User): Result<Unit>` |
| `purchase` | `suspend fun (product: SubscriptionProduct): Result<PurchaseRecord>` |
| `restore` | `suspend fun (): Result<List<PurchaseRecord>>` |

`grantEntitlement` SHALL resolve the session user's email, call `SubscriptionFirestoreApi.setSubscription` with `platform = "Android"`, and then call `refreshCachedUser` so the DataStore-backed session reflects the new state.

`refreshCachedUser` SHALL read the authoritative user document from Firestore and write it through `SessionRepository.updateUser`, so the cached `User` never diverges from the server for longer than one purchase flow.

The repository SHALL resolve the current email from `SessionRepository.data` (`SessionState.Authenticated`), matching the pattern `GamificationRepository.getUserPoints()` uses (`core/network/.../gamification/GamificationRepository.kt`), and SHALL return `Result.failure` when the session is anonymous.

#### Scenario: Grant writes and caches
- **WHEN** `grantEntitlement` is called for an authenticated user on the yearly plan
- **THEN** `Users/{email}` carries the three Android-written fields and the session DataStore's cached `User` carries the same three values

#### Scenario: Anonymous session fails cleanly
- **WHEN** `grantEntitlement` is called while `SessionRepository.data` emits `SessionState.Anonymous`
- **THEN** it returns `Result.failure` and issues no Firestore write

#### Scenario: Revoke clears and refreshes
- **WHEN** `revokeEntitlement` is called
- **THEN** the three fields are deleted server-side and the cached session `User` is refreshed so `isUserPremiumNow` becomes `false`

### Requirement: SessionRepository gains a user-only patch write
`SessionRepository` in `core/storage` SHALL add:

```kotlin
suspend fun updateUser(transform: (User) -> User): Result<Unit>
```

The method SHALL read the current `SessionRecord`, apply `transform` to the `user` field, and write the record back with `token` and `expiresAt` preserved **verbatim** — all inside a single `dataStore.updateData` block so the write remains atomic. It SHALL NOT call `setSession`, which requires a live auth token that the billing flow does not have.

The anonymous-detection invariants in `SessionRepository.toState()` (empty token, empty user id, or a past `expiresAt` ⇒ `SessionState.Anonymous`) SHALL be unchanged by this method.

#### Scenario: Patch preserves the token
- **WHEN** the DataStore holds an authenticated session and `updateUser { it.copy(subscriptionPlatform = "Android") }` is called
- **THEN** the token and `expiresAt` are unchanged and only the `user` field differs

#### Scenario: Patch keeps the session authenticated
- **WHEN** `updateUser` is called on a valid authenticated session
- **THEN** `data()` continues to emit `SessionState.Authenticated` with the transformed user

#### Scenario: Patch on an anonymous session is a no-op with respect to auth state
- **WHEN** `updateUser` is called while the DataStore holds the anonymous sentinel record
- **THEN** the record remains anonymous and the method returns `Result.success(Unit)`

#### Scenario: Write stays atomic
- **WHEN** `updateUser` is interrupted mid-write
- **THEN** the DataStore file is never left with an updated user and a stale token

### Requirement: Cross-platform field compatibility
`subscriptionType` SHALL use the same two raw values on both platforms — `video_call_monthly` and `video_call_yearly_sub` — so a subscription purchased on iOS is readable on Android and vice versa. `subscriptionPlatform` SHALL record the purchasing platform (`"iOS"` written by the iOS app at `UserRepository.swift:173`, `"Android"` written by this port) and SHALL be written but never branched on: no premium decision anywhere in the Android codebase SHALL test `subscriptionPlatform`.

The `subscriptionExpiryDate` written by Android SHALL be an ISO-8601 instant string, and `SubscriptionFirestoreApi` SHALL convert it to a Firestore `Timestamp` on the write path so the on-wire document shape is identical to iOS's.

#### Scenario: iOS subscriber reads as premium on Android
- **WHEN** a `Users/{email}` document has `subscriptionType = "video_call_monthly"` and `subscriptionPlatform = "iOS"` and a future `subscriptionExpiryDate`
- **THEN** `isUserPremiumNow` is `true` and the Profile row shows "Monthly"

#### Scenario: Platform field is not branched on
- **WHEN** any premium decision is evaluated for a user whose `subscriptionPlatform` is `"iOS"`
- **THEN** the decision uses only `subscriptionExpiryDate`, and the same decision is reached for a `"Android"` subscriber with the same expiry

#### Scenario: Android writes a Timestamp on the wire
- **WHEN** `setSubscription` writes `subscriptionExpiryDate`
- **THEN** the stored Firestore value is a `Timestamp`, readable by iOS's `Timestamp.dateValue()` projection
