# video-call-session Specification

## Purpose
TBD - created by archiving change add-video-calling. Update Purpose after archive.
## Requirements
### Requirement: SessionTokenService implements the WebSocket token handshake
`SessionTokenService` (`@Singleton`) SHALL connect to `ws://ws.kornerz.com:80/` via OkHttp `WebSocket`, wait for the `onOpen` callback, send the Vonage `sessionId` as `ByteString` (UTF-8 bytes), await a single text message of the form `{"msg": "<token>"}`, parse the `msg` field, emit the token via a `suspend` function returning `Result<String>`, then close the socket. On any error (connect failure, malformed message, timeout after 10 s), the suspend function SHALL resume with `Result.failure(throwable)`. The service SHALL NOT auto-reconnect this phase (D2 in `design.md`). Mirrors iOS `Backend/Services/OpenTokTokenService.swift` + `WebSocketService.swift`'s one-shot handshake.

#### Scenario: Token handshake succeeds
- **WHEN** `SessionTokenService.fetchToken("vsess-123")` is invoked
- **AND** the WebSocket connects to `ws://ws.kornerz.com:80/`
- **AND** the server sends `{"msg":"tok-abc"}` within 10 seconds
- **THEN** the suspend function resumes with `Result.success("tok-abc")`

#### Scenario: Token handshake fails on malformed response
- **WHEN** the server sends a message that is not valid JSON or that does not contain a `msg` field
- **THEN** the suspend function resumes with `Result.failure(VideoCallError.FailedToGetToken)`

#### Scenario: Token handshake times out
- **WHEN** no message arrives within 10 seconds of the socket opening
- **THEN** the socket is closed
- **AND** the suspend function resumes with `Result.failure(VideoCallError.SocketTimeout)`

### Requirement: VonageSessionController manages the local publisher and remote subscribers
`VonageSessionController` (`@Singleton`) SHALL construct a Vonage `Session` with `apiKey = "47628671"` (pinned in `VonageConfig.kt`, with a `TODO(android-key-rotation)` marker for the pre-ship rotation) and the per-call `sessionId`. It SHALL provide `connect(token: String, sessionId: String): Result<Unit>`, `disconnect(): Result<Unit>`, `publishers: StateFlow<Publisher?>`, `subscribers: StateFlow<List<Subscriber>>`, `setPublishVideo(Boolean)`, `setPublishAudio(Boolean)`, `setCameraPosition(CameraPosition)`. On `Session.SessionListener.onConnected` it SHALL publish the local `Publisher`. On `streamCreated` it SHALL construct a `Subscriber` and call `session.subscribe(subscriber)`, then append the subscriber to the `subscribers` `StateFlow`. On `streamDropped` it SHALL remove the subscriber. The controller SHALL be process-scoped (one instance per process), NOT per-VM, to allow subscribers to survive VM recreation (same pattern as iOS — the `OTSession` is held by the view model but the publisher is owned by the session).

#### Scenario: Local publisher is published after the session connects
- **WHEN** `VonageSessionController.connect(token, sessionId)` is invoked
- **AND** the session fires `onConnected`
- **THEN** the local `Publisher` is published
- **AND** `publishers` `StateFlow` emits the publisher

#### Scenario: Remote subscriber is added on streamCreated
- **WHEN** the session fires `streamCreated` for a new remote stream
- **THEN** a `Subscriber` is constructed for the stream
- **AND** `session.subscribe(subscriber, error)` is called
- **AND** `subscribers` `StateFlow` emits the updated list including the new subscriber

### Requirement: VideoCallRepository orchestrates token fetch and Vonage connect
`VideoCallRepository` (`@Singleton`) SHALL provide `connect(sessionId: String): Flow<CallSession>` that:
1. Invokes `SessionTokenService.fetchToken(sessionId)` and yields a `CallSession(status = "Authenticating")` while in flight
2. On token arrival, invokes `VonageSessionController.connect(token, sessionId)` and yields `CallSession(status = "Connecting")`
3. On the controller's `onConnected`, yields `CallSession(status = "Connected", sessionId, localPublisherId)` and continues to yield updates as the controller's `subscribers` `StateFlow` changes
4. On any failure, terminates the flow with `Result.failure(throwable)` and ensures `VonageSessionController.disconnect()` is called as cleanup

`disconnect(): Result<Unit>` SHALL call `VonageSessionController.disconnect()` and return its result.

#### Scenario: Repository yields the full state machine
- **WHEN** `VideoCallRepository.connect("vsess-123")` is collected
- **THEN** the flow first emits `CallSession(status = "Authenticating", sessionId = "vsess-123")`
- **AND** after the token arrives, emits `CallSession(status = "Connecting", sessionId = "vsess-123")`
- **AND** after the session connects, emits `CallSession(status = "Connected", sessionId = "vsess-123", localPublisherId = ...)`
- **AND** on each remote `streamCreated`, emits a new `CallSession` with `remoteSubscribers` including the new entry

#### Scenario: Repository cleans up on failure
- **WHEN** the Vonage session fires `onError` before `onConnected`
- **THEN** the flow terminates with `Result.failure(throwable)`
- **AND** `VonageSessionController.disconnect()` is invoked as cleanup
- **AND** a subsequent `connect(sessionId)` call starts fresh