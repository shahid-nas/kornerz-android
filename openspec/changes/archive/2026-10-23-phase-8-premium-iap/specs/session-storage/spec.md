# session-storage delta Specification

## Purpose
TBD - created by archiving change phase-8-premium-iap. Update Purpose after archive.

## MODIFIED Requirements

### Requirement: Session write API
The repository SHALL expose `suspend fun setSession(token: String, expiresAt: Instant, user: User)`, `suspend fun clearSession()`, and `suspend fun updateUser(transform: (User) -> User): Result<Unit>` (added in P8). Writes SHALL be atomic: a partial write that crashes SHALL NOT leave the DataStore file in an inconsistent state where one field is updated and another is not.

`updateUser` is required because `setSession` is the only other write path and it demands a live auth token that the P8 billing flow does not have and must not need. `updateUser` SHALL read the current `SessionRecord`, apply `transform` to the `user` field only, and write the record back with `token` and `expiresAt` preserved **verbatim** — all inside a single `dataStore.updateData` block, so atomicity is preserved. It SHALL return `Result<Unit>` and SHALL NOT throw on a read or write failure.

The anonymous-detection invariants SHALL be unchanged by `updateUser`: a record is anonymous when its `token` is empty, or its `user.id` is empty, or its `expiresAt` fails to parse or is in the past (`SessionRepository.toState()`).

P8 calls `updateUser` after every successful purchase, revoke, and server reconciliation so the cached `User`'s premium fields stay in step with Firestore.

#### Scenario: Successful set
- **WHEN** `setSession` is called with a valid token, expiry, and user
- **THEN** all three fields are persisted atomically
- **AND** `data()` immediately emits `Authenticated(user)` to all subscribers

#### Scenario: Successful clear
- **WHEN** `clearSession` is called
- **THEN** the token, expiry, and user are all removed
- **AND** `data()` immediately emits `Anonymous`

#### Scenario: updateUser preserves the token and expiry
- **WHEN** the DataStore holds an authenticated session and `updateUser { it.copy(subscriptionPlatform = "Android") }` is called
- **THEN** the persisted `token` and `expiresAt` are byte-identical to their prior values and only `user` differs

#### Scenario: updateUser keeps the session authenticated
- **WHEN** `updateUser` is applied to a valid authenticated record
- **THEN** `data()` continues to emit `SessionState.Authenticated` with the transformed user, without a transient `Anonymous` emission

#### Scenario: updateUser on an anonymous record leaves it anonymous
- **WHEN** `updateUser` is called while the DataStore holds the anonymous sentinel record (empty token, empty user id)
- **THEN** the record remains anonymous and the method returns `Result.success(Unit)`

#### Scenario: updateUser surfaces write failures as a Result
- **WHEN** the DataStore write throws
- **THEN** `updateUser` returns `Result.failure` rather than propagating the exception

#### Scenario: Premium state survives a process restart
- **WHEN** a purchase updates the cached user via `updateUser` and the app is later killed and relaunched
- **THEN** the restored `SessionState.Authenticated(user)` carries the same `subscriptionExpiryDate`, `subscriptionType`, and `subscriptionPlatform` values

## References

- `core/storage/src/main/kotlin/com/kornerz/core/storage/session/SessionRepository.kt` — the file modified by P8
- `core/storage/src/main/kotlin/com/kornerz/core/storage/session/SessionRecord.kt` — the single-document `@Serializable` aggregate P8 patches
- `core/storage/src/main/kotlin/com/kornerz/core/storage/session/SessionState.kt` — the emitted state union whose invariants P8 preserves