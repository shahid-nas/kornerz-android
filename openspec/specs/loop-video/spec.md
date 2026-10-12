# loop-video Specification

## Purpose
The `loop-video` capability defines the video playback layer for the Loops detail screen — a `VideoPlayerView` composable wrapping Media3 ExoPlayer + `androidx.media3:media3-ui` inside an `AndroidView(PlayerView)` host, plus the `PlayerViewModel` that exposes `isPlaying`, `playbackError`, and `controlsVisible` state. This is the Compose equivalent of iOS `AVPlayer` for `LoopDetailView.swift` lines 67–75 (the centered play-circle icon) and 192–195 (the media-section `onTapGesture → viewModel.playVideo()`). The capability is added as a standalone module so the video stack is reusable by future video surfaces (P9 Vonage calls will build on it).

## Requirements

### Requirement: VideoPlayerView wraps ExoPlayer in an AndroidView host with a PlayerController
The `VideoPlayerView` `@Composable` SHALL accept `videoUrl: String`, `autoPlay: Boolean = false`, `showControls: Boolean = true`, `modifier: Modifier = Modifier` (last parameter per arch §4) and SHALL render an `AndroidView` that hosts a Media3 `PlayerView` whose `player = exoPlayer`. The composable SHALL create a new `ExoPlayer` instance via `LocalContext.current` + `remember(videoUrl)`, build a `MediaItem` from `videoUrl`, call `player.setMediaItem(mediaItem)` and `player.prepare()` when `autoPlay = true` (or wait for explicit tap when `autoPlay = false`). The composable SHALL release the player inside `DisposableEffect(...).onDispose { exoPlayer.release() }` so memory is reclaimed when the destination is popped. A `PlayerViewModel` (`@HiltViewModel` keyed by the loop detail's `SavedStateHandle["loopId"]`) SHALL hold the player state (`isPlaying: Boolean`, `playbackError: String?`, `controlsVisible: Boolean`) as `StateFlow`s consumed by the composable.

#### Scenario: VideoPlayerView mounts and prepares the player
- **WHEN** `VideoPlayerView(videoUrl = "https://cdn.example.com/v.mp4", autoPlay = false)` enters the composition
- **THEN** a new `ExoPlayer` instance is created via `remember(videoUrl)`
- **AND** the player view renders inside an `AndroidView` host
- **AND** the media item is prepared (the first frame is decoded and the player is in the "ready" state, paused)

#### Scenario: Player is released on dispose
- **WHEN** the user navigates away from the loop detail
- **THEN** `DisposableEffect.onDispose { exoPlayer.release() }` fires
- **AND** the ExoPlayer instance is released (no memory leak)

#### Scenario: Auto-play starts playback immediately
- **WHEN** `VideoPlayerView(videoUrl, autoPlay = true)` mounts
- **THEN** `player.play()` is invoked after `prepare()` resolves
- **AND** `PlayerViewModel.isPlaying = true` is published

#### Scenario: Player surfaces a load error
- **WHEN** `videoUrl = "https://invalid.example.com/v.mp4"` and the player fails to prepare
- **THEN** `PlayerViewModel.playbackError = "Could not load video"` is published
- **AND** the composable renders an inline error overlay with a "Retry" button
- **AND** tapping Retry re-invokes `player.prepare()`

### Requirement: Play button overlay renders when player is paused
When `PlayerViewModel.isPlaying = false` AND the player is in the ready state, the composable SHALL overlay a centered 70dp `play_circle_fill` icon on top of the player view. Tapping the icon SHALL call `player.play()` and emit `PlayerEvent.PlayTapped`. Mirrors iOS `LoopDetailView.swift` lines 67–75.

#### Scenario: Paused state renders the centered play icon
- **WHEN** `isPlaying = false` and the media item is loaded
- **THEN** a centered 70dp `play_circle_fill` icon overlay renders on top of the player view
- **WHEN** the user taps the icon
- **THEN** `player.play()` is invoked
- **AND** `isPlaying = true`
- **AND** the icon disappears

#### Scenario: Tap-to-pause surfaces the controls
- **WHEN** `isPlaying = true` and the user taps the player view
- **THEN** `controlsVisible = true`
- **AND** `PlayerView.useController = true`
- **WHEN** 3 seconds elapse without further taps
- **THEN** `controlsVisible = false`
- **AND** the controls overlay disappears

### Requirement: PlayerViewModel exposes playback state for the parent loop detail
The `PlayerViewModel` SHALL expose `isPlaying: StateFlow<Boolean>`, `playbackError: StateFlow<String?>`, and `controlsVisible: StateFlow<Boolean>`. The composable SHALL collect all three via `collectAsStateWithLifecycle()` and react accordingly. The VM SHALL register a `Player.Listener` that publishes `onIsPlayingChanged`, `onPlayerError`, and a 3-second auto-hide timer for the controls. The listener SHALL be removed inside `onDispose` so the VM is reusable across loop detail opens without leaking listeners.

#### Scenario: PlayerViewModel publishes isPlaying state changes
- **WHEN** the user taps the play icon
- **THEN** `onIsPlayingChanged(true)` fires from the `Player.Listener`
- **AND** `isPlaying.value = true`
- **AND** the composing parent `LoopDetailContent` updates (the play icon disappears)

#### Scenario: PlayerViewModel clears playbackError on retry
- **WHEN** the user taps the "Retry" button in the error overlay
- **THEN** `playbackError.value = null`
- **AND** `player.prepare()` re-runs with the same `videoUrl`

### Requirement: ExoPlayer buffer + playback config matches the baseline Media3 defaults
The `ExoPlayer` instance SHALL be constructed with the baseline Media3 config: `LoadControl.DEFAULT_BUFFERS_IMAGE_DURATION`, `DefaultBandwidthMeter.Builder(context).build()`, and `DefaultRenderersFactory(context).setEnableDecoderFallback(true)`. The player SHALL use `seekParameters = SeekParameters.CLOSEST_SYNC`. The `PlayerView` SHALL use `setUseController(true)` and SHALL set `setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)`. Matches the canonical Media3 sample (`developer.android.com/media/media3/exoplayer`).

#### Scenario: Player uses the canonical Media3 config
- **WHEN** the player instance is created
- **THEN** the `LoadControl`, `BandwidthMeter`, and `RenderersFactory` are the baseline Media3 defaults
- **AND** `PlayerView.useController = true` and `showBuffering = SHOW_BUFFERING_WHEN_PLAYING`

### Requirement: Loop detail falls back to a still image when Loop.isVideo is false
The loop detail screen SHALL NOT instantiate `VideoPlayerView` for loops with `isVideo = false`. Instead, the media section SHALL render the Coil image from `Loop.imageUrl` (with shimmer placeholder) scaled to fill, clipped, with a centered 70dp `play_circle_fill` icon overlay that is decorative only (no playback). Matches iOS `LoopDetailView.swift` lines 67–75 (the overlay is rendered regardless of `isVideo`, but `media-section.onTapGesture → viewModel.playVideo()` is the only way playback is dispatched).

#### Scenario: Still-image loop does not instantiate ExoPlayer
- **WHEN** the loop loads with `isVideo = false`
- **THEN** the media section renders a Coil image (not `VideoPlayerView`)
- **AND** no `ExoPlayer` is allocated
- **AND** no `DisposableEffect.onDispose` for the player fires
