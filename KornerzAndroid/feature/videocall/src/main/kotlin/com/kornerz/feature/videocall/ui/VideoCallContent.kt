package com.kornerz.feature.videocall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.CallSession
import com.kornerz.core.network.videocall.VideoCallDiagnostics
import com.kornerz.core.ui.components.KornerzDiamondImage
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Satoshi
import com.kornerz.feature.videocall.R
import com.kornerz.feature.videocall.BuildConfig
import com.kornerz.feature.videocall.VideoCallEvent
import com.kornerz.feature.videocall.VideoCallParticipant
import com.kornerz.feature.videocall.VideoCallState

/**
 * Stateless renderer for the video call screen.
 *
 * 1:1 port of iOS `VideoCallView`
 * (`kornerz/Kornerz/SwiftUI/VideoCall/VideoCallView.swift`):
 *
 *  - a root column with 16pt gaps between the top toolbar, the
 *    subscriber area and the bottom toolbar, painted with the iOS
 *    call background (`AppColor.CE27D5A`),
 *  - a top toolbar carrying the 48pt local self-view and the community
 *    name,
 *  - a scrolling subscriber grid with the iOS row/tile arithmetic,
 *  - a bottom toolbar of five circular controls,
 *  - a centred status overlay while the call is connecting.
 * - [permissionsGranted] is `null` while the runtime permission request
 *   is still in flight, `false` when the user denied it and `true` once
 *   access is granted.
 */
@Composable
fun VideoCallContent(
    state: VideoCallState,
    onEvent: (VideoCallEvent) -> Unit,
    permissionsGranted: Boolean?,
    onGrantPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    onReconnect: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VideoCallPalette.Background),
    ) {
        if (permissionsGranted == false) {
            PermissionDeniedCard(onGrant = onGrantPermissions)
            return@Box
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(VideoCallLayout.SectionSpacing),
        ) {
            TopToolbar(state = state)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                SubscriberGrid(state = state)
            }
            BottomToolbar(state = state, onEvent = onEvent)
        }
        // iOS draws the status overlay on top of the whole screen
        // (`.edgesIgnoringSafeArea(.all)`), so it covers the toolbars
        // too — it is transparent apart from the spinner and the copy.
        CallStatusOverlay(
            state = state,
            onRetry = { onEvent(VideoCallEvent.Retry) },
        )

        if (BuildConfig.DEBUG) {
            CallDiagnosticsPanel(
                diagnostics = state.diagnostics,
                onReconnect = onReconnect,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 56.dp, end = 8.dp),
            )
        }
    }
}

/**
 * Debug-only panel with the live session internals.
 *
 * "Connected but no subscribers" has three very different causes —
 * a different room, a token that cannot receive streams, or a failed
 * peer connection — and each one is visible here without logcat.
 */
@Composable
private fun CallDiagnosticsPanel(
    diagnostics: VideoCallDiagnostics,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC101014))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = "room: ${diagnostics.requestedRoom}",
            color = Color(0xFF9BE59B),
            style = TextStyle(fontSize = 8.sp, fontFamily = FontFamily.Monospace),
            maxLines = 2,
        )
        Text(
            text = "token: ${diagnostics.token}",
            color = Color(0xFFFFD98A),
            style = TextStyle(fontSize = 8.sp, fontFamily = FontFamily.Monospace),
            maxLines = 3,
        )
        Text(
            text = "conn: ${diagnostics.connection}",
            color = Color(0xFF9BD3FF),
            style = TextStyle(fontSize = 8.sp, fontFamily = FontFamily.Monospace),
            maxLines = 2,
        )
        Text(
            text = "caps: ${diagnostics.capabilities}",
            color = Color(0xFF9BD3FF),
            style = TextStyle(fontSize = 8.sp, fontFamily = FontFamily.Monospace),
            maxLines = 2,
        )
        Text(
            text = "pub: ${diagnostics.publisher}",
            color = Color.White,
            style = TextStyle(fontSize = 8.sp, fontFamily = FontFamily.Monospace),
            maxLines = 2,
        )
        Text(
            text = "subs: ${diagnostics.subscribers}",
            color = Color.White,
            style = TextStyle(fontSize = 8.sp, fontFamily = FontFamily.Monospace),
            maxLines = 3,
        )
        Text(
            text = "last: ${diagnostics.lastCallback}",
            color = Color(0xFFFF9B9B),
            style = TextStyle(fontSize = 8.sp, fontFamily = FontFamily.Monospace),
            maxLines = 2,
        )
        Text(
            text = "reconnect",
            color = Color(0xFF9BE59B),
            style = TextStyle(fontSize = 8.sp, fontFamily = FontFamily.Monospace),
            modifier = Modifier.clickable { onReconnect() },
        )
    }
}

/**
 * Top toolbar — iOS `VideoCallView.topToolbar`. A 48pt self-view on the
 * leading edge (the live publisher feed, or a diamond avatar on a
 * charcoal tile when the camera is off) followed by the community name.
 */
@Composable
private fun TopToolbar(state: VideoCallState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The call paints edge-to-edge, so the toolbar keeps itself
            // clear of the status bar — the iOS safe-area inset — and
            // then takes the extra vertical padding so the self-view
            // and the community name do not sit flush against the top
            // of the visible area.
            .statusBarsPadding()
            .padding(
                top = VideoCallLayout.VerticalPadding,
                start = VideoCallLayout.HorizontalPadding,
                end = VideoCallLayout.HorizontalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LocalPublisherTile(state = state)
        Spacer(modifier = Modifier.size(TopToolbarMetrics.Gap))
        Text(
            text = state.communityName.orEmpty(),
            color = VideoCallPalette.OnBackground,
            style = TextStyle(
                fontFamily = Satoshi,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                fontSize = TopToolbarMetrics.TitleSize,
            ),
            maxLines = 2,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * The local self-view. iOS shows the live publisher feed while the
 * camera is on, and a charcoal tile carrying the user's diamond avatar
 * while it is off.
 */
@Composable
private fun LocalPublisherTile(state: VideoCallState) {
    Box(
        modifier = Modifier
            .size(TopToolbarMetrics.TileSize)
            // The backdrop is painted rounded rather than clipped: the
            // SDK's video surface is not shaped by a Compose clip, so
            // the clip left a thin dark fringe around the feed. The
            // video itself is rounded by the platform.
            .background(
                color = VideoCallPalette.Tile,
                shape = RoundedCornerShape(TopToolbarMetrics.TileCornerRadius),
            ),
    ) {
        val publisher = state.publisher
        if (state.isVideoOn && publisher != null) {
            PublisherContainer(
                publisher = publisher,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = TopToolbarMetrics.TileCornerRadius,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VideoCallPalette.Tile),
            ) {
                KornerzDiamondImage(
                    imageUrl = state.localThumbnailUrl,
                    size = TopToolbarMetrics.ThumbnailSize,
                    cornerRadius = TopToolbarMetrics.ThumbnailCornerRadius,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/**
 * The subscriber area — iOS `VideoCallView.contentView`. A scrolling
 * grid whose row composition and tile sizes come from
 * [VideoCallGrid.kt].
 */
@Composable
private fun SubscriberGrid(state: VideoCallState) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = VideoCallLayout.HorizontalPadding,
                vertical = VideoCallLayout.ContentVerticalPadding,
            ),
    ) {
        // The constraints are in **pixels**, but the tile sizes are
        // applied with `Modifier.size(… .dp)`, which converts the
        // value back to pixels. Feeding the raw pixel count in as a dp
        // value multiplied the size by the display density — on a
        // 2.625x device a 1933px container became 5074px, i.e. a tile
        // 2.6x taller than the screen and cut off by the bottom
        // toolbar. The container is therefore taken in dp.
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        val rows = subscriberGridRows(state.participants)

        LaunchedEffect(state.participants) {
            Log.i(
                TAG,
                "SubscriberGrid render: participants=${state.participants.size} " +
                    "rows=${rows.size} names=${state.participants.map { it.userName }} " +
                    "hasVideo=${state.participants.map { it.hasVideo }} " +
                    "withSubscriber=${state.participants.map { it.subscriber != null }}",
            )
        }

        // The tile geometry is the whole layout contract with iOS, so
        // the container and the computed tile sizes are worth having in
        // the log when a tile looks mis-sized or clipped.
        LaunchedEffect(containerWidth, containerHeight, rows.size) {
            val tiles = rows.map { row ->
                val size = subscriberGridTileSize(
                    rowItemCount = row.size,
                    containerWidth = containerWidth.value,
                    containerHeight = containerHeight.value,
                    totalRows = rows.size,
                )
                "${size.width.toInt()}x${size.height.toInt()}"
            }
            Log.i(
                TAG,
                "SubscriberGrid geometry: container=${containerWidth.value.toInt()}x${containerHeight.value.toInt()}dp " +
                    "rows=${rows.size} tiles=$tiles",
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(VideoCallGridSpacing.dp),
        ) {
            rows.forEach { row ->
                val tileSize = subscriberGridTileSize(
                    rowItemCount = row.size,
                    containerWidth = containerWidth.value,
                    containerHeight = containerHeight.value,
                    totalRows = rows.size,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VideoCallGridSpacing.dp),
                ) {
                    row.forEach { participant ->
                        SubscriberTile(
                            participant = participant,
                            modifier = Modifier.size(tileSize.width.dp, tileSize.height.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom toolbar — iOS `VideoCallView.bottomToolBar`. End call on the
 * leading edge (solid disc), then rotate camera, camera, microphone
 * and raise hand (ringed discs that fill in when toggled off / on).
 */
@Composable
private fun BottomToolbar(
    state: VideoCallState,
    onEvent: (VideoCallEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // …and the bottom toolbar stays clear of the navigation bar,
            // plus the same extra vertical padding so the controls do
            // not sit flush against the bottom of the visible area.
            .navigationBarsPadding()
            .padding(
                bottom = VideoCallLayout.VerticalPadding,
                start = VideoCallLayout.HorizontalPadding,
                end = VideoCallLayout.HorizontalPadding,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularCallButton(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.video_call_end_call),
            onClick = { onEvent(VideoCallEvent.EndCall) },
            style = CallButtonStyle.Light,
        )
        CircularCallButton(
            icon = Icons.Filled.Cameraswitch,
            contentDescription = stringResource(R.string.video_call_flip_camera),
            onClick = { onEvent(VideoCallEvent.FlipCamera) },
        )
        CircularCallButton(
            icon = if (state.isVideoOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
            contentDescription = stringResource(R.string.video_call_toggle_video),
            onClick = { onEvent(VideoCallEvent.ToggleVideo) },
            selected = !state.isVideoOn,
        )
        CircularCallButton(
            icon = if (state.isAudioOn) Icons.Filled.Mic else Icons.Filled.MicOff,
            contentDescription = stringResource(R.string.video_call_toggle_audio),
            onClick = { onEvent(VideoCallEvent.ToggleAudio) },
            selected = !state.isAudioOn,
        )
        CircularCallButton(
            icon = Icons.Filled.PanTool,
            contentDescription = stringResource(R.string.video_call_toggle_hand_raised),
            onClick = { onEvent(VideoCallEvent.ToggleHandRaised) },
            selected = state.isHandRaised,
            // iOS raises a left hand (`hand.raised`); Material's
            // `pan_tool` is a right hand, so the glyph is mirrored.
            flipHorizontally = true,
        )
    }
}

/**
 * Android-only card shown when the camera or microphone permission is
 * denied. iOS asks for camera/microphone access up front and never
 * renders an in-call denial state, so this card has no iOS counterpart
 * — it is styled with the same call palette so it does not look out of
 * place.
 */
@Composable
private fun PermissionDeniedCard(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.video_call_permission_required),
            color = VideoCallPalette.OnBackground,
            style = TextStyle(
                fontFamily = Satoshi,
                fontWeight = FontWeight.Normal,
                fontSize = VideoCallLayout.BodySize,
            ),
        )
        Spacer(modifier = Modifier.size(16.dp))
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(
                containerColor = VideoCallPalette.Control,
                contentColor = VideoCallPalette.Tile,
            ),
        ) {
            Text(text = stringResource(R.string.video_call_grant_permissions))
        }
    }
}

/**
 * Layout metrics shared by the call screen sections. Mirrors the iOS
 * `VideoCallView` spacing (16pt between sections, 16pt horizontal
 * padding).
 */
internal object VideoCallLayout {
    val SectionSpacing = 16.dp
    val HorizontalPadding = 16.dp

    /**
     * Vertical breathing room between the toolbars and the edges of
     * the visible area.
     *
     * The call screen is edge-to-edge (`enableEdgeToEdge()` in
     * `MainActivity`), so the top toolbar and the bottom toolbar are
     * already inset by the status bar / navigation bar. On top of
     * that, iOS keeps a visible margin between the controls and the
     * screen edge, which the Android port was missing — the toolbar
     * buttons looked flush against the edge.
     */
    val VerticalPadding = 16.dp

    /**
     * Vertical breathing room above and below the subscriber grid.
     *
     * iOS's `contentView` only pads **horizontally**
     * (`kornerz/Kornerz/SwiftUI/VideoCall/VideoCallView.swift`:
     * `.padding(.horizontal, 16)`) — there is no vertical padding on
     * iOS. The grid area therefore has to be the full height that is
     * left between the two toolbars, otherwise a single subscriber's
     * tile is 16pt shorter than on iOS and its bottom edge is cut off
     * by the bottom toolbar.
     */
    val ContentVerticalPadding = 0.dp
    val BodySize = 16.sp
}

/**
 * Metrics from iOS `VideoCallView.Constants`.
 */
internal object TopToolbarMetrics {
    val TileSize = 48.dp
    val TileCornerRadius = 5.dp
    val ThumbnailSize = 45.dp
    val ThumbnailCornerRadius = 10.dp
    val Gap = 8.dp
    val TitleSize = 18.sp
}

@Preview(showBackground = true, name = "VideoCallContent — Connected")
@Composable
private fun VideoCallContentConnectedPreview() {
    KornerzTheme(darkTheme = false) {
        VideoCallContent(
            state = VideoCallState(
                callStatus = AsyncValue.Success(
                    CallSession(
                        sessionId = "abc",
                        status = CallSession.STATUS_CONNECTED,
                        localPublisherId = "p1",
                    ),
                ),
                communityName = "Design Korner",
                participants = listOf(
                    VideoCallParticipant.preview("Ada Lovelace"),
                    VideoCallParticipant.preview("Grace Hopper", raisedHand = true),
                ),
            ),
            onEvent = {},
            permissionsGranted = true,
            onGrantPermissions = {},
        )
    }
}

@Preview(showBackground = true, name = "VideoCallContent — Waiting")
@Composable
private fun VideoCallContentWaitingPreview() {
    KornerzTheme(darkTheme = false) {
        VideoCallContent(
            state = VideoCallState(
                callStatus = AsyncValue.Success(
                    CallSession(sessionId = "abc", status = CallSession.STATUS_CONNECTED),
                ),
                communityName = "Design Korner",
            ),
            onEvent = {},
            permissionsGranted = true,
            onGrantPermissions = {},
        )
    }
}

private const val TAG = "VideoCallUI"
