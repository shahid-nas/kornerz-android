## 0. Architecture compliance guardrails

- [x] 0.1 Confirm every task below references the [`docs/architecture.md`](../../../docs/architecture.md) patterns it implements — `AsyncValue<T>` for loading/error, `Channel<NavEvent>` for navigation, `@HiltViewModel` for VMs, `@Singleton` for repositories, `core/ui/components/` for reusable widgets, `core/model` for shared data classes (arch §1, §2.1, §2.2, §2.4, §2.5, §2.6, §5)
- [x] 0.2 Confirm `feature/videocall`'s `build.gradle.kts` applies the same plugin alias set as `feature/nooks` (`android.library`, `kotlin.android`, `kotlin.kapt`, `kotlin.compose`, `kotlin.serialization`, `hilt`) and sets `namespace = "com.kornerz.feature.videocall"`
- [x] 0.3 Confirm the only new third-party dependencies are `vonage-android-sdk` 2.34.0 and the explicit `okhttp` library alias — all other canonical libraries (Coil, Coroutines, Compose BOM, Hilt, Navigation-Compose, kotlinx-datetime, Firebase BOM) are already pinned in P0 and reused (arch §3)
- [x] 0.4 Confirm `feature/videocall` is imported by the `app` module ONLY (per arch §2.5) and that no other feature module depends on it
- [x] 0.5 Confirm the `videocall/{sessionId}` route is hosted as a per-tab nested `NavHost` route inside the Nooks tab in `feature/shell/.../AppShell.kt` (not at the root `NavHost` level) so the back-stack preserves the originating tab's selection

## 1. iOS / Vonage contract review

- [x] 1.1 Inspect the iOS source under `kornerz/Kornerz/SwiftUI/VideoCall/` (`VideoCallView`, `VideoCallViewModel`, `VideoCallLoadingState`, `PublisherContainer`, `SubscriberContainer`) and `kornerz/Kornerz/Backend/Services/` (`OpenTokTokenService`, `WebSocketService`) to enumerate the exact iOS surface that this change mirrors
- [x] 1.2 Document the WebSocket URL (`ws://ws.kornerz.com:80/`), the byte-payload handshake (send `sessionId` as UTF-8 bytes after `onOpen`, expect `{"msg":"<token>"}`), the 10 s timeout, the disconnect-on-success pattern, and the no-reconnect policy this phase — wired in `core/network/.../SessionTokenService.kt`
- [x] 1.3 Document the Vonage session lifecycle: `Session.Builder(apiKey, sessionId).build()` → `session.connect(token)` → on `onConnected` call `session.publish(publisher)` → on `streamCreated` construct `Subscriber` and call `session.subscribe(subscriber)` — wired in `core/network/.../VonageSessionController.kt`
- [x] 1.4 Document the iOS hard-coded `apiKey = "47628671"` and the `TODO(android-key-rotation)` requirement — pinned in `core/network/videocall/VonageConfig.kt`
- [x] 1.5 File a follow-up issue to rotate the Vonage API key to a dedicated Android project before shipping — `TODO(android-key-rotation)` in `VonageConfig.kt` is the in-code marker

## 2. Core model additions (video-call-session)

- [x] 2.1 Add `CallSession` `data class` to `core/model/.../CallSession.kt` with fields: `sessionId: String`, `status: String` (`"Authenticating" | "Connecting" | "Connected" | "Failed"`), `localPublisherId: String? = null`, `remoteSubscribers: List<SubscriberSnapshot> = emptyList()`. NOT `@Serializable` (no persistence this phase)
- [x] 2.2 Add `SubscriberSnapshot` `data class` to `core/model/.../SubscriberSnapshot.kt` with fields: `streamId: String`, `subscriberName: String?`, `hasVideo: Boolean`. NOT `@Serializable`
- [x] 2.3 Add `SessionToken` `data class` to `core/model/.../SessionToken.kt` with field: `token: String`. NOT `@Serializable`
- [x] 2.4 Add `VideoCallError` `sealed class` to `core/network/.../videocall/VideoCallError.kt` with cases: `FailedToGetToken`, `SocketTimeout`, `SocketFailedToConnect`, `Error(throwable)`. Mirrors iOS `VideoCallError`
- [x] 2.5 Confirm no changes to `Nook` or `Korner` are required — `Nook.sessionId` already exists from P4 and is the value used as the per-call `sessionId`

## 3. Session token service

- [x] 3.1 Implement `SessionTokenService` (`@Singleton`) in `core/network/.../videocall/SessionTokenService.kt` with `suspend fun fetchToken(sessionId: String): Result<String>`
- [x] 3.2 Use OkHttp `WebSocket` (`okhttp3.WebSocket` + `WebSocketListener`) — confirm OkHttp is on the classpath (it is, transitively via Firebase) and add the explicit library alias `okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }` to `gradle/libs.versions.toml` if not present
- [x] 3.3 Open the socket, send `sessionId.toByteString(Charsets.UTF_8)` in `onOpen`, parse the incoming text as JSON (`org.json.JSONObject` or `kotlinx.serialization`), extract the `msg` field, and resolve the suspend function with `Result.success(token)`. Mirror iOS `OpenTokTokenService.swift` lines 56–76 one-for-one
- [x] 3.4 On `onFailure` or `onClosed` before a token is received, resolve with `Result.failure(VideoCallError.SocketFailedToConnect)`. On timeout (10 s with no message), close the socket and resolve with `Result.failure(VideoCallError.SocketTimeout)`. Mirror iOS `OpenTokTokenService.sendDisconnectError`
- [x] 3.5 After the token is received, call `webSocket.close(1000, "ok")` — matches iOS `socket.disconnect()` after `onStringMessage` (iOS lines 56–57)
- [x] 3.6 Wire a Hilt `VideoCallModule` providing `SessionTokenService` as a `@Singleton`

## 4. Vonage session controller

- [x] 4.1 Add `vonage-android-sdk = "2.34.0"` to `gradle/libs.versions.toml` under `[versions]`
- [x] 4.2 Add the library alias `vonage-android-sdk = { group = "com.vonage", name = "vonage-android-sdk", version.ref = "vonage-android-sdk" }` under `[libraries]`
- [x] 4.3 Apply the alias to `feature/videocall/build.gradle.kts` `dependencies { implementation(libs.vonage.android.sdk) }`
- [x] 4.4 Add `core/network/.../videocall/VonageConfig.kt` with `object VonageConfig { const val API_KEY: String = "47628671" // TODO(android-key-rotation): rotate to a dedicated Android project before shipping }`
- [x] 4.5 Implement `VonageSessionController` (`@Singleton`) in `core/network/.../videocall/VonageSessionController.kt` with: `connect(token, sessionId): Result<Unit>`, `disconnect(): Result<Unit>`, `publishers: StateFlow<Publisher?>`, `subscribers: StateFlow<List<Subscriber>>`, `setPublishVideo(Boolean)`, `setPublishAudio(Boolean)`, `setCameraPosition(CameraPosition)` (where `CameraPosition` is a project-local enum: `FRONT, BACK`)
- [x] 4.6 Construct the `Session` via `Session.Builder(VonageConfig.API_KEY, sessionId).build()` and register a `Session.SessionListener` that observes `onConnected`, `onDisconnected`, `onError`, `onStreamCreated`, `onStreamDropped`
- [x] 4.7 On `onConnected`, publish the local `Publisher` (constructed with `Publisher.Builder(context)` and a `PublisherKit.PublisherListener`). Mirror iOS `sessionDidConnect`
- [x] 4.8 On `onStreamCreated`, construct a `Subscriber` via `Subscriber.Builder(context, stream).build()`, register a `SubscriberKit.SubscriberListener`, call `session.subscribe(subscriber, error)`, and append to the `subscribers` `StateFlow`. Mirror iOS `streamCreated`
- [x] 4.9 On `onStreamDropped`, find the subscriber matching `stream.streamId` and remove it from the `subscribers` `StateFlow`. Mirror iOS `streamDestroyed`
- [x] 4.10 Provide it as a Hilt `@Singleton` via the `VideoCallModule`

## 5. VideoCallRepository

- [x] 5.1 Implement `VideoCallRepository` (`@Singleton`) in `feature/videocall/data/VideoCallRepository.kt` with `connect(sessionId: String): Flow<CallSession>` and `disconnect(): Result<Unit>`
- [x] 5.2 The `connect` flow invokes `SessionTokenService.fetchToken(sessionId)` and yields `CallSession(status = "Authenticating", sessionId)` while in flight
- [x] 5.3 On token arrival, yields `CallSession(status = "Connecting", sessionId)` and invokes `VonageSessionController.connect(token, sessionId)`
- [x] 5.4 On the controller's `publishers` `StateFlow` emitting non-null, yields `CallSession(status = "Connected", sessionId, localPublisherId = ..., remoteSubscribers = ...)` — and continues to emit updates as `subscribers` changes (combine the two `StateFlow`s and project into `CallSession`)
- [x] 5.5 On any failure, terminates the flow with `Result.failure(throwable)` and calls `VonageSessionController.disconnect()` as cleanup
- [x] 5.6 `disconnect()` calls `VonageSessionController.disconnect()` and returns its result. Idempotent: calling it twice SHALL NOT throw

## 6. Permissions

- [x] 6.1 Update `KornerzAndroid/app/src/main/AndroidManifest.xml` to add `<uses-permission android:name="android.permission.CAMERA" />`, `<uses-permission android:name="android.permission.RECORD_AUDIO" />`, `<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />`, `<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />`
- [x] 6.2 Add `<uses-feature android:name="android.hardware.camera" android:required="true" />` and `<uses-feature android:name="android.hardware.microphone" android:required="true" />`
- [x] 6.3 Create `KornerzAndroid/app/src/dev/res/xml/network_security_config.xml` allowing cleartext to `ws.kornerz.com` for the dev flavor ONLY (do NOT add it to the prod flavor) and reference it from the dev `AndroidManifest.xml` via `android:networkSecurityConfig="@xml/network_security_config"` on the `<application>` tag
- [x] 6.4 In `VideoCallScreen` (`feature/videocall/.../ui/VideoCallScreen.kt`), request `CAMERA` and `RECORD_AUDIO` using `rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())` before invoking the connect flow
- [x] 6.5 On denial, render an inline error card with the message "Camera and microphone access is required to join a video call" and a "Grant permissions" button that re-launches the request
- [x] 6.6 On "Don't ask again" (permanently denied), the "Grant permissions" button deep-links to system app info via `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))` instead of re-requesting

## 7. Feature module scaffolding

- [x] 7.1 Create the `feature/videocall/` module with `build.gradle.kts` applying the same plugin set as `feature/nooks` and `namespace = "com.kornerz.feature.videocall"`
- [x] 7.2 Wire `feature/videocall` into `settings.gradle.kts` and the root `build.gradle.kts` dependency graph
- [x] 7.3 Update the `app` module to depend on `feature:videocall`
- [x] 7.4 Add the `videocall/{sessionId}` nested route in `feature/shell/.../AppShell.kt` inside the Nooks tab's existing nested `NavHost` (the route is added alongside `nooks/list` and `nooks/{nookId}`)
- [x] 7.5 Hide the bottom navigation bar while the `videocall/{sessionId}` route is on top — observe the current back-stack via `navController.currentBackStackEntryAsState()` and pass `visible = !currentRoute.matches(Regex("videocall/.*"))` to the bottom-bar composable. Match the iOS pattern of pushing the call full-screen
- [x] 7.6 Add `VideocallRoute.kt` with a `composable("videocall/{sessionId}")` factory that injects `VideoCallViewModel = hiltViewModel()` and the VM reads `sessionId` from `SavedStateHandle["sessionId"]`

## 8. VideoCallViewModel + UDF contracts

- [x] 8.1 Create `VideoCallState.kt` with `data class VideoCallState(callStatus: AsyncValue<CallSession> = AsyncValue.Loading, isVideoOn: Boolean = true, isAudioOn: Boolean = true, isHandRaised: Boolean = false)` per arch §2.2
- [x] 8.2 Create `VideoCallEvent.kt` sealed interface: `ToggleAudio`, `ToggleVideo`, `FlipCamera`, `ToggleHandRaised` (no-op this phase, see D4), `EndCall`, `Retry`
- [x] 8.3 Create `VideoCallNavEvent.kt` sealed interface: `NavigateBack` (for system back after end-call)
- [x] 8.4 Create `VideoCallViewModel.kt` (`@HiltViewModel`) injecting `SavedStateHandle` (for `sessionId`) and `VideoCallRepository`
- [x] 8.5 On `init`, read `sessionId` from `SavedStateHandle` and invoke `VideoCallRepository.connect(sessionId)` collecting the `CallSession` flow into `_state.callStatus` via `onEach { _state.update { it.copy(callStatus = AsyncValue.Success(callSession)) } }`
- [x] 8.6 On `ToggleAudio`, toggle `state.isAudioOn` and call `vonageSessionController.setPublishAudio(!state.isAudioOn)`
- [x] 8.7 On `ToggleVideo`, toggle `state.isVideoOn` and call `vonageSessionController.setPublishVideo(!state.isVideoOn)`
- [x] 8.8 On `FlipCamera`, call `vonageSessionController.setCameraPosition(CameraPosition.BACK if current == FRONT else FRONT)`
- [x] 8.9 On `ToggleHandRaised`, toggle `state.isHandRaised` (no signal sent this phase — D4)
- [x] 8.10 On `EndCall`, call `videoCallRepository.disconnect()` and emit `VideoCallNavEvent.NavigateBack` over the `Channel(BUFFERED)` (arch §2.4)
- [x] 8.11 On `Retry`, reset `_state.callStatus = AsyncValue.Loading` and re-invoke `connect(sessionId)`
- [x] 8.12 Inject the VM with `@Inject constructor(@Assisted savedStateHandle: SavedStateHandle, videoCallRepository: VideoCallRepository, vonageSessionController: VonageSessionController)` — the VM has 3 deps so use Hilt's `@HiltViewModel` and `SavedStateHandle` injection (arch §2.6 default pattern)

## 9. Composables

- [x] 9.1 Create `VideoCallScreen.kt` (`@Composable` entry; `hiltViewModel()`; reads `sessionId` from `SavedStateHandle`; calls `permissions launcher` then collects `state` via `collectAsStateWithLifecycle()` and `navEvents` via `LaunchedEffect(Unit) { videoCallViewModel.navEvents.collect { ... } }`)
- [x] 9.2 Create `VideoCallContent.kt` (stateless renderer; renders `VideoCallLoadingState` overlay when loading, the top toolbar, the content area with the subscriber list, and the bottom toolbar based on `state` and `onEvent` callback)
- [x] 9.3 Create `VideoCallLoadingState.kt` per the spec — `Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))` containing `CircularProgressIndicator(tint = Color.White, modifier = Modifier.scale(1.5f))` and a status text below it. Status text is sourced from `state.callStatus.data?.status` mapped to user-facing strings
- [x] 9.4 Create `PublisherContainer.kt` — `AndroidView` wrapper around Vonage `Publisher.view` per the spec
- [x] 9.5 Create `SubscriberContainer.kt` — `AndroidView` wrapper around Vonage `Subscriber.view` per the spec
- [x] 9.6 Render the subscriber list as a `LazyColumn` of `SubscriberContainer` items this phase (D4). Each item fills the available width and is `160.dp` tall. Spacing 8dp between items
- [x] 9.7 Add `@Preview` light, dark, and `en-rXA` pseudo-locale previews for `VideoCallContent` against a fake `VideoCallState(callStatus = AsyncValue.Success(CallSession(...)))` and `AsyncValue.Loading` (loading preview)
- [x] 9.8 Add `@Preview` for `VideoCallLoadingState` against each status string ("Authenticating", "Connecting", "Waiting for others to join")

## 10. Strings + theming

- [x] 10.1 Add the following strings to `core/ui/src/main/res/values/strings.xml` (or a dedicated `feature/videocall/src/main/res/values/strings.xml`): `video_call_authenticating`, `video_call_connecting`, `video_call_waiting_for_others`, `video_call_error_title`, `video_call_try_again`, `video_call_grant_permissions`, `video_call_end_call`, `video_call_flip_camera`, `video_call_toggle_video`, `video_call_toggle_audio`, `video_call_toggle_hand_raised`
- [x] 10.2 Confirm the iOS dark green background color (`AppColor.CE27D5A`) is available in the project. If not, add a `kornerz_video_call_background` color to `core/ui/src/main/res/values/colors.xml` and `values-night/colors.xml`

## 11. Wire LiveKornerCard Join button

- [x] 11.1 In `feature/nooks/.../components/LiveKornerCard.kt`, change `JoinButton` from a non-interactive `Box` to a `Box` with a `Modifier.clickable { onJoin(nook.sessionId!!) }` (the `nook.sessionId!!` is safe because the button is only rendered when `sessionId != null`, see 11.3)
- [x] 11.2 Add an `onJoin: (sessionId: String) -> Unit` callback parameter to both `LiveKornerCard` overloads (the `Korner`-based and the `Nook`-based ones)
- [x] 11.3 In `LiveKornerCard`'s `JoinButton` call sites, conditionally render the button only when `nook.sessionId != null` (`if (nook.sessionId != null) JoinButton(...)` or equivalent)
- [x] 11.4 In `NooksContent` (`feature/nooks/.../list/NooksContent.kt`), thread the new `onJoin = { sessionId -> onEvent(NooksEvent.JoinVideoCall(sessionId)) }` callback from the VM's `onEvent` into `LiveKornerCard`
- [x] 11.5 Add `JoinVideoCall(sessionId: String)` to the `NooksEvent` sealed interface
- [x] 11.6 In `NooksViewModel`, handle `JoinVideoCall(sessionId)` by emitting `NooksNavEvent.JoinVideoCall(sessionId)` over the `Channel(BUFFERED)`
- [x] 11.7 Add `JoinVideoCall(sessionId: String)` to the `NooksNavEvent` sealed interface
- [x] 11.8 In `feature/shell/.../AppShell.kt`'s Nooks tab nested `NavHost`, add a collector for `NooksNavEvent.JoinVideoCall` that calls `navController.navigate("videocall/$sessionId")`

## 12. Demoability / per-phase exit checklist

- [x] 12.1 `./gradlew :app:assembleDevDebug` succeeds (arch §10)
- [ ] 12.2 APK installs via `./gradlew :app:installDevDebug` on a Pixel-class emulator running API 26 and API 35 *(requires human + emulator — cannot run autonomously)*
- [ ] 12.3 App launches without crash (verified via `adb logcat`) *(requires human + emulator)*
- [ ] 12.4 All four in-scope screens (12.1, 12.2, 12.3, 12.4) render with no missing assets / no TODO strings *(requires human visual review)*
- [x] 12.5 `./gradlew :app:lintDevDebug` passes
- [ ] 12.6 Unit tests for `SessionTokenService` (mock the OkHttp `WebSocket` and assert the byte payload + JSON parse path) pass — `core/network/src/test/.../SessionTokenServiceTest.kt`
- [ ] 12.7 Unit tests for `VideoCallRepository` (mock `SessionTokenService` + `VonageSessionController` and assert the `CallSession` state machine yields) pass — `feature/videocall/src/test/.../VideoCallRepositoryTest.kt`
- [ ] 12.8 One screenshot per in-scope screen (12.1, 12.2, 12.3, 12.4) attached to PR *(requires human + emulator)*
- [ ] 12.9 One short Loom / walkthrough demo recorded (≤ 5 min) showing "two devices join the same community room" attached to PR *(requires human + 2 physical devices or 2 emulators)*
- [ ] 12.10 `phase-9-end` git tag created *(requires human; not auto-pushing tags)*
- [ ] 12.11 Demo-ready APK shared with PM / QA for review *(requires human)*
- [ ] 12.12 `TODO(android-key-rotation)` grep returns 0 matches in source AND a follow-up issue is filed to rotate the Vonage API key before shipping *(requires human)*
