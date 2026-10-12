# feature-videocall Specification (delta)

## ADDED Requirements

### Requirement: VideoCallScreen hosts a Vonage video room keyed by sessionId
The `VideoCallScreen` `@Composable` entry SHALL be reachable via the `videocall/{sessionId}` route inside the Nooks tab's nested `NavHost`. The screen SHALL read `sessionId` from `SavedStateHandle["sessionId"]` and SHALL pass it to `VideoCallViewModel` via `hiltViewModel()`. While the screen is in the back-stack, the bottom navigation bar SHALL be hidden. Mirrors iOS `SwiftUI/VideoCall/VideoCallView.swift`.

#### Scenario: User joins a video call from the Nooks tab live card
- **WHEN** the user taps the Join button on a `LiveKornerCard` whose underlying `Nook.sessionId` is `"vsess-123"`
- **THEN** the Nooks tab's nested NavHost pushes `videocall/vsess-123`
- **AND** `VideoCallViewModel.init` reads `sessionId = "vsess-123"` from `SavedStateHandle`
- **AND** the bottom navigation bar is hidden

#### Scenario: System back from a video call returns to the Nooks list
- **WHEN** the user is on `videocall/{sessionId}` and presses the system back button
- **THEN** the nested NavHost pops to `nooks/list`
- **AND** the Nooks tab remains selected
- **AND** the bottom navigation bar reappears

### Requirement: VideoCallViewModel exposes call status, publisher, and subscribers as state
`VideoCallViewModel` (`@HiltViewModel`) SHALL expose `VideoCallState(callStatus: AsyncValue<CallSession>, isVideoOn: Boolean = true, isAudioOn: Boolean = true, isHandRaised: Boolean = false)` as a `StateFlow<VideoCallState>`. `callStatus` SHALL be `AsyncValue.Loading` until `VideoCallRepository.connect(sessionId)` resolves, then `AsyncValue.Success(CallSession)`, or `AsyncValue.Error(throwable)` on failure. Navigation events SHALL be emitted over `Channel<VideoCallNavEvent>(BUFFERED)` per arch §2.4. The VM SHALL accept `VideoCallEvent` inputs (`ToggleAudio`, `ToggleVideo`, `FlipCamera`, `EndCall`, `Retry`) and SHALL map them to repository calls or local state mutations. Mirrors iOS `SwiftUI/VideoCall/VideoCallViewModel.swift`.

#### Scenario: VM transitions through Authenticating → Connecting → Connected
- **WHEN** `VideoCallViewModel.init` invokes `VideoCallRepository.connect(sessionId)`
- **THEN** `state.callStatus` first emits `AsyncValue.Loading` (with internal `connecting: Boolean` distinguishing the WebSocket phase from the Vonage connect phase — see D2)
- **AND** on token arrival emits `AsyncValue.Loading` again (WebSocket closed, Vonage connect in flight)
- **AND** on `VonageSessionController` `onConnected` callback emits `AsyncValue.Success(CallSession(sessionId, localPublisherId, remoteSubscribers = []))`
- **AND** on `streamCreated` from the session the VM appends a `SubscriberSnapshot` to `state.callStatus.data.remoteSubscribers`

#### Scenario: VM toggles local audio and video
- **WHEN** the user taps the bottom toolbar's mic button
- **THEN** `VideoCallViewModel.onEvent(VideoCallEvent.ToggleAudio)` is invoked
- **AND** `VonageSessionController.setPublishAudio(!state.isAudioOn)` is called
- **AND** `state.isAudioOn` is updated to `!state.isAudioOn`

### Requirement: VideoCallContent renders top toolbar, content area, and bottom toolbar
`VideoCallContent` `@Composable` SHALL render three vertical regions in this order: a top toolbar (48dp×48dp `PublisherContainer` thumbnail on the left, the community name in Satoshi Bold 18sp next to it, a Spacer), a content area that fills the remaining height and renders the subscriber list inside a `LazyColumn` (this phase, D4), and a bottom toolbar with five circular buttons: End call (X icon, fills the action), Flip camera (arrow.trianglehead.2.clockwise), Toggle video (video.fill / video.slash.fill), Toggle audio (microphone.fill / microphone.slash.fill), Hand raised (hand.raised / hand.raised.fill, see D4). The background SHALL be the brand dark green (`AppColor.CE27D5A` in iOS — ported to `color/kornerz_palette.xml` if not present). The loading overlay SHALL be rendered on top of the content area when `state.callStatus is AsyncValue.Loading`. Mirrors iOS `SwiftUI/VideoCall/VideoCallView.swift`.

#### Scenario: Connected state with no remote subscribers
- **WHEN** `state.callStatus` is `AsyncValue.Success(CallSession(remoteSubscribers = []))` and `state.isVideoOn` is `true`
- **THEN** the top toolbar shows the `PublisherContainer` thumbnail at 48dp×48dp
- **AND** the content area shows a single centered `PublisherContainer` filling the content area (or, if a subscriber-less "waiting" state is preferred, the loading-state copy "Waiting for others to join" — implementation choice, see D-Loading-Overlay)
- **AND** the bottom toolbar shows five circular buttons in the iOS order

#### Scenario: User toggles video off
- **WHEN** the user taps the Toggle video button while `state.isVideoOn = true`
- **THEN** `VideoCallEvent.ToggleVideo` is dispatched
- **AND** `state.isVideoOn` becomes `false`
- **AND** the top toolbar's `PublisherContainer` thumbnail is replaced with a static placeholder Box showing the user's avatar (or, if the avatar is unavailable, a flat-color placeholder)

#### Scenario: User ends the call
- **WHEN** the user taps the End call button
- **THEN** `VideoCallEvent.EndCall` is dispatched
- **AND** `VideoCallRepository.disconnect()` is invoked
- **AND** a `VideoCallNavEvent.NavigateBack` is emitted over the `Channel`
- **AND** the screen pops back to `nooks/list`

### Requirement: VideoCallLoadingState renders a centered progress indicator with status copy
`VideoCallLoadingState` `@Composable` SHALL render a centered overlay (`Modifier.fillMaxSize()` with `background(Color.Black.copy(alpha = 0.6f))`) containing a `CircularProgressIndicator(tint = Color.White, modifier = Modifier.scale(1.5f))` and a status text below it. The status text SHALL be "Authenticating" when the WebSocket token is in flight, "Connecting" when the Vonage session is in flight, "Waiting for others to join" when the session is connected and the publisher is published and `state.callStatus.data.remoteSubscribers.isEmpty()`. The overlay SHALL be hidden when `state.callStatus is AsyncValue.Success` AND `state.callStatus.data.remoteSubscribers.isNotEmpty()`. Mirrors iOS `SwiftUI/VideoCall/VideoCallLoadingState.swift`.

#### Scenario: Authenticating overlay during WebSocket token fetch
- **WHEN** `state.callStatus is AsyncValue.Loading` AND the WebSocket phase is in flight
- **THEN** the loading overlay is rendered
- **AND** the status text is "Authenticating"
- **AND** the underlying `VideoCallContent` is not yet interactive (taps on the bottom toolbar are no-ops while loading)

#### Scenario: Failure overlay on token fetch error
- **WHEN** `SessionTokenService.fetchToken(sessionId)` resolves to an error
- **THEN** `state.callStatus` becomes `AsyncValue.Error(throwable)`
- **AND** the loading overlay is replaced with an error card containing the error message and a "Try again" button
- **AND** tapping "Try again" dispatches `VideoCallEvent.Retry` which re-invokes `VideoCallRepository.connect(sessionId)`

### Requirement: PublisherContainer and SubscriberContainer wrap the Vonage Views via AndroidView
`PublisherContainer` and `SubscriberContainer` `@Composable`s SHALL each be `AndroidView` wrappers that own a `Publisher` / `Subscriber` instance for the lifetime of the composable. The `factory` lambda SHALL construct the `Publisher` / `Subscriber` and return the SDK's `view` as the host `View`. The `update` lambda SHALL be a no-op (the SDK manages the `View` lifecycle). The composable SHALL accept the publisher/subscriber and an optional `Modifier` for sizing and clipping. Mirrors iOS `SwiftUI/VideoCall/PublisherContainer.swift` and `SubscriberContainer.swift`. Per `docs/project-brief.md` decision 1, this is the only place in the app that uses `AndroidView` (apart from any future rich-text editor).

#### Scenario: PublisherContainer composes a Publisher
- **WHEN** `PublisherContainer(publisher = publisher, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(5.dp)))` is composed
- **THEN** an `AndroidView` is created
- **AND** its `factory` constructs a Vonage `Publisher` with the local camera and returns `publisher.view`
- **AND** the host `View` is rendered at 48dp×48dp with a 5dp rounded clip

#### Scenario: SubscriberContainer composes a Subscriber
- **WHEN** `SubscriberContainer(subscriber = subscriber, modifier = Modifier.fillMaxSize())` is composed
- **THEN** an `AndroidView` is created
- **AND** its `factory` returns `subscriber.view`
- **AND** the host `View` is rendered filling the available size
