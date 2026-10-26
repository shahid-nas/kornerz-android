# feature-videocall Specification

## Purpose
TBD - created by archiving change add-video-calling. Update Purpose after archive.
## Requirements
### Requirement: VideoCallScreen hosts a Vonage video room keyed by sessionId
The `VideoCallScreen` `@Composable` entry SHALL be reachable via the `videocall/{sessionId}?communityName={communityName}&communityId={communityId}` route inside the Nooks tab's nested `NavHost`. The screen SHALL read `sessionId`, `communityName` and `communityId` from `SavedStateHandle` and SHALL pass them to `VideoCallViewModel` via `hiltViewModel()`. While the screen is in the back-stack, the bottom navigation bar SHALL be hidden. Mirrors iOS `SwiftUI/VideoCall/VideoCallView.swift`.

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
`VideoCallViewModel` (`@HiltViewModel`) SHALL expose `VideoCallState(callStatus: AsyncValue<CallSession>, communityName: String?, localThumbnailUrl: String?, isVideoOn: Boolean = true, isAudioOn: Boolean = true, isHandRaised: Boolean = false, publisher: Publisher?, participants: List<VideoCallParticipant>)` as a `StateFlow<VideoCallState>`. `callStatus` SHALL be `AsyncValue.Loading` until `VideoCallRepository.connect(sessionId)` resolves, then `AsyncValue.Success(CallSession)`, or `AsyncValue.Error(throwable)` on failure. `communityName` SHALL be read from the `communityName` route argument. Navigation events SHALL be emitted over `Channel<VideoCallNavEvent>(BUFFERED)` per arch §2.4. The VM SHALL accept `VideoCallEvent` inputs (`ToggleAudio`, `ToggleVideo`, `FlipCamera`, `ToggleHandRaised`, `EndCall`, `Retry`, `PermissionsResult`) and SHALL map them to repository calls or local state mutations. Mirrors iOS `SwiftUI/VideoCall/VideoCallViewModel.swift`.

#### Scenario: VM transitions through Authenticating → Connecting → Connected
- **WHEN** `VideoCallViewModel.init` invokes `VideoCallRepository.connect(sessionId)`
- **THEN** `state.callStatus` first emits `AsyncValue.Loading` (with internal `connecting: Boolean` distinguishing the WebSocket phase from the Vonage connect phase — see D2)
- **AND** on token arrival emits `AsyncValue.Loading` again (WebSocket closed, Vonage connect in flight)
- **AND** on `VonageSessionController` `onConnected` callback emits `AsyncValue.Success(CallSession(sessionId, localPublisherId, remoteSubscribers = []))`
- **AND** on `streamCreated` from the session the VM appends a `SubscriberSnapshot` to `state.callStatus.data.remoteSubscribers` and a `VideoCallParticipant` to `state.participants`

#### Scenario: VM toggles local audio and video
- **WHEN** the user taps the bottom toolbar's mic button
- **THEN** `VideoCallViewModel.onEvent(VideoCallEvent.ToggleAudio)` is invoked
- **AND** `VonageSessionController.setPublishAudio(!state.isAudioOn)` is called
- **AND** `state.isAudioOn` is updated to `!state.isAudioOn`

### Requirement: The raise-hand state is signalled to the room
The call screen SHALL broadcast the local participant's raise-hand state to the room using the iOS signal contract: the signal type SHALL be `Raise_Hand_Active` / `Raise_Hand_InActive` and the body SHALL be `{"type":…,"data":{"userId":…,"userName":…}}`. The receiver SHALL resolve the body's `userId` against each remote peer's published identity and SHALL raise the hand badge on the matching tile only. The local publisher SHALL publish its identity (`{"userName":…,"userId":…,"image":…}`) as the Vonage stream name so remote peers can label this participant. Mirrors iOS `MeetingSignalManager` and `PublisherModel`.

### Requirement: VideoCallContent renders top toolbar, content area, and bottom toolbar
`VideoCallContent` `@Composable` SHALL render three vertical regions in this order — separated by 16dp, matching the iOS `VStack(spacing: 16)` — on the brand coral background (`AppColor.CE27D5A` → `VideoCallPalette.Background`):

1. **Top toolbar** — a 48dp×48dp self-view on the leading edge (`PublisherContainer` clipped to a 5dp rounded rectangle while the camera is on, otherwise a `VideoCallPalette.Tile` rectangle carrying the user's 45dp diamond avatar), an 8dp gap, the community name in Satoshi Bold Italic 18sp, and a `Spacer`.
2. **Content area** — fills the remaining height with 16dp horizontal padding and 8dp vertical padding (so the tiles never butt up against the toolbars) and renders the subscriber **grid** (not a list) using the iOS row arithmetic: 4dp spacing between tiles and rows, a row of two tiles unless the row holds a single participant (which spans the full width), a two-participant room laid out as two full-width rows, and a row height that divides the available height by the row count minus the inter-row spacing.
3. **Bottom toolbar** — five 50dp circular controls with a 26dp glyph, laid out with `SpaceBetween` in the iOS order: End call (`CircleButtonStyleConfig.light` — solid pale disc, dark glyph), Flip camera (ringed), Toggle video (ringed, filled when the camera is off), Toggle audio (ringed, filled when muted), Hand raised (ringed, filled when raised).

The loading overlay SHALL be rendered **on top of the whole screen** (not only the content area) while the call is connecting. Mirrors iOS `SwiftUI/VideoCall/VideoCallView.swift`.

The call SHALL paint edge-to-edge: the shell SHALL NOT apply the `Scaffold`'s system-bar insets to the `NavHost` for the video-call route, so the coral backdrop reaches the status bar and the navigation bar. The call screen SHALL inset its own chrome instead — the top toolbar with `statusBarsPadding()` and the bottom toolbar with `navigationBarsPadding()` — so the controls stay clear of the system bars while the background fills the window (the iOS safe-area behaviour). Without this the window background shows through as two white strips above and below the call.

#### Scenario: Connected state with no remote subscribers
- **WHEN** `state.callStatus` is `AsyncValue.Success(CallSession(remoteSubscribers = []))` and `state.isVideoOn` is `true`
- **THEN** the top toolbar shows the `PublisherContainer` self-view at 48dp×48dp
- **AND** the content area is empty
- **AND** the full-screen overlay shows the "Waiting for others to join" copy in the emphasised (18sp bold italic) style
- **AND** the bottom toolbar shows five circular buttons in the iOS order

#### Scenario: Two participants join the room
- **WHEN** two remote subscribers are present
- **THEN** the grid renders two rows of one tile each (the iOS `subscriberGridRows()` behaviour for a count of two)
- **AND** each tile spans the full content width and half the content height

#### Scenario: A remote participant's subscription drops
- **WHEN** a remote participant is still in the room but the SDK reported its subscription as dropped (`isConnected = false`)
- **THEN** the tile stays in the grid
- **AND** the tile renders the participant's diamond avatar instead of the video surface

#### Scenario: User toggles video off
- **WHEN** the user taps the Toggle video button while `state.isVideoOn = true`
- **THEN** `VideoCallEvent.ToggleVideo` is dispatched
- **AND** `state.isVideoOn` becomes `false`
- **AND** the top toolbar's `PublisherContainer` self-view is replaced by a `VideoCallPalette.Tile` rectangle carrying the user's diamond avatar

#### Scenario: User ends the call
- **WHEN** the user taps the End call button
- **THEN** `VideoCallEvent.EndCall` is dispatched
- **AND** `VideoCallRepository.disconnect()` is invoked
- **AND** a `VideoCallNavEvent.NavigateBack` is emitted over the `Channel`
- **AND** the screen pops back to `nooks/list`

### Requirement: VideoCallLoadingState renders a centered progress indicator with status copy
`VideoCallLoadingState` `@Composable` SHALL render a centered overlay (`Modifier.fillMaxSize()`, transparent — the iOS overlay paints no scrim) containing a `CircularProgressIndicator(tint = white, modifier = Modifier.scale(1.5f))` and a status text 16dp below it. The status text SHALL be "Authenticating, please wait" when the WebSocket token is in flight, "Connecting" when the Vonage session is in flight, and "Waiting for others to join" in the emphasised 18sp bold italic style when the session is connected and no participant has joined. The overlay SHALL be hidden when the session is connected and at least one participant has joined. Mirrors iOS `SwiftUI/VideoCall/VideoCallLoadingState.swift`.

#### Scenario: Authenticating overlay during WebSocket token fetch
- **WHEN** `state.callStatus` is `AsyncValue.Loading` AND the WebSocket phase is in flight
- **THEN** the loading overlay is rendered
- **AND** the status text is "Authenticating, please wait"

#### Scenario: Failure overlay on token fetch error
- **WHEN** `SessionTokenService.fetchToken(sessionId)` resolves to an error
- **THEN** `state.callStatus` becomes `AsyncValue.Error(throwable)`
- **AND** the loading overlay is replaced with an error card containing the error message and a "Try again" button
- **AND** tapping "Try again" dispatches `VideoCallEvent.Retry` which re-invokes `VideoCallRepository.connect(sessionId)`

### Requirement: The call screen asks for camera and microphone access before opening the session
`VideoCallScreen` SHALL request `CAMERA` and `RECORD_AUDIO` at runtime and SHALL forward the result to the view model as `VideoCallEvent.PermissionsResult(granted)`. `VideoCallViewModel` SHALL only open the Vonage session once the permissions are granted. While the request is in flight the screen SHALL render the normal call chrome (no denial card). When the permissions are denied, the screen SHALL render an inline card with a "Grant permissions" button that re-launches the request, or deep-links to the system app-info screen when the permission was permanently denied. Mirrors iOS, which asks for camera/microphone access before it opens the session.

### Requirement: The call screen keeps the screen awake and awards the nook points
While the call screen is on-screen the host activity SHALL keep `FLAG_KEEP_SCREEN_ON` set, mirroring iOS `UIApplication.shared.isIdleTimerDisabled = true`. When the session is opened the view model SHALL award 2 Klouders points with the action `ACCESS_NOOK` and the community id as the reference, mirroring iOS `VideoCallViewModel.addGamificationPoints()`.

### Requirement: PublisherContainer and SubscriberContainer wrap the Vonage Views via AndroidView
`PublisherContainer` and `SubscriberContainer` `@Composable`s SHALL each be `AndroidView` wrappers that own a `Publisher` / `Subscriber` instance for the lifetime of the composable. The `factory` lambda SHALL construct the host `FrameLayout` and attach the SDK's `view` to it. The `update` lambda SHALL re-attach the SDK view only when the rendered view actually changed, so a recomposition never restarts the video renderer. The composable SHALL accept the publisher/subscriber and an optional `Modifier` for sizing and clipping. Mirrors iOS `SwiftUI/VideoCall/PublisherContainer.swift` and `SubscriberContainer.swift`. Per `docs/project-brief.md` decision 1, this is the only place in the app that uses `AndroidView` (apart from any future rich-text editor).

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