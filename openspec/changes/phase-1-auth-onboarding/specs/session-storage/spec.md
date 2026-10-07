## ADDED Requirements

### Requirement: DataStore-backed session storage
The `core/storage` module SHALL expose a `SessionRepository` that reads and writes a single Preferences DataStore file containing: the auth token, the auth token's `expiresAt` (ISO-8601 string), and the JSON-serialized `User`. The repository SHALL expose `data(): Flow<SessionState>` where `SessionState` is one of `Anonymous`, `Authenticated(user: User)`, or `Loading` (only the very first read before DataStore returns).

#### Scenario: Empty session
- **WHEN** the app starts with no prior session
- **THEN** `data()` first emits `Loading`
- **AND** then emits `Anonymous`

#### Scenario: Persisted session restored
- **WHEN** the app starts after a previous successful login
- **THEN** `data()` emits `Loading`
- **AND** then emits `Authenticated(user)` containing the previously persisted `User`

### Requirement: Session write API
The repository SHALL expose `suspend fun setSession(token: String, expiresAt: Instant, user: User)` and `suspend fun clearSession()`. Writes SHALL be atomic: a partial write that crashes SHALL NOT leave the DataStore file in an inconsistent state where one field is updated and another is not.

#### Scenario: Successful set
- **WHEN** `setSession` is called with a valid token, expiry, and user
- **THEN** all three fields are persisted atomically
- **AND** `data()` immediately emits `Authenticated(user)` to all subscribers

#### Scenario: Successful clear
- **WHEN** `clearSession` is called
- **THEN** the token, expiry, and user are all removed
- **AND** `data()` immediately emits `Anonymous`

## References

- [`docs/architecture.md`](../../../../docs/architecture.md) — Repository return-type conventions (`Flow<T>` for streams, `suspend fun ... Result<T>` for one-shots), Hilt scopes (`@Singleton` for repositories), `kotlinx.serialization`, DataStore listed in canonical library catalog §3.
- [`docs/phases.md` §Phase 1](../../../../docs/phases.md) — source of truth for P1 scope.

## ADDED Requirements

### Requirement: Expired token is treated as anonymous
On read, if the persisted token's `expiresAt` is in the past, the repository SHALL emit `Anonymous` and SHALL clear the stored token on the next write cycle.

#### Scenario: Expired token read
- **WHEN** the DataStore contains a token whose `expiresAt` is in the past
- **THEN** `data()` emits `Anonymous`
- **AND** the next call to `clearSession` removes the expired entry