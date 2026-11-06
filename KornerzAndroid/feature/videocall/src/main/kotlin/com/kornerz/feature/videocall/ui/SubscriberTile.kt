package com.kornerz.feature.videocall.ui

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.components.KornerzDiamondImage
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Satoshi
import com.kornerz.feature.videocall.VideoCallParticipant

/**
 * One remote participant's tile. 1:1 port of iOS `SubscriberContainer`
 * (`kornerz/Kornerz/SwiftUI/VideoCall/SubscriberContainer.swift`):
 *
 *  - a black backdrop carrying the live video surface when the
 *    participant publishes video,
 *  - a 70pt diamond avatar when they do not,
 *  - an overlay row at the bottom with the display name and a
 *    raised-hand badge.
 */
@Composable
internal fun SubscriberTile(
    participant: VideoCallParticipant,
    modifier: Modifier = Modifier,
) {
    val subscriber = participant.subscriber
    val showsVideo = participant.hasVideo && participant.isConnected && subscriber != null

    Box(
        modifier = modifier
            // The backdrop is painted with a rounded shape rather than
            // clipped with one. A Compose `Modifier.clip` puts the
            // children into a clipped layer, and the SDK's `TextureView`
            // renders from its own surface, which such a clip does not
            // shape — that left a thin translucent black fringe around
            // the video. The video is rounded by the platform instead
            // (see `SubscriberContainer`), so the backdrop only has to
            // *look* rounded.
            //
            // The black backdrop is only painted while the video is on:
            // iOS puts `bgView` (the black `Color`) inside the
            // `isVideoEnabled` branch of the `ZStack`, so a participant
            // with the camera off leaves the tile transparent and the
            // call screen's own background shows through.
            .background(
                color = if (showsVideo) {
                    VideoCallPalette.VideoBackdrop
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(SubscriberTileMetrics.CornerRadius),
            ),
    ) {
        if (showsVideo) {
            SubscriberContainer(
                subscriber = subscriber,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = SubscriberTileMetrics.CornerRadius,
            )
            // The SDK draws the video through a `GLSurfaceView`, which
            // only renders once it has been resumed — the resume is
            // driven by `VonageSessionController.resumeRenderers`. If
            // the surface is attached but the renderer was never
            // resumed, the tile stays black, so the state of the
            // surface is worth having in the log.
            LaunchedEffect(participant.streamId) {
                Log.i(
                    TAG,
                    "SubscriberTile video surface: streamId=${participant.streamId} " +
                        "hasView=${subscriber.view != null} " +
                        "hasRenderer=${subscriber.renderer != null}",
                )
            }
        } else {
            KornerzDiamondImage(
                imageUrl = participant.thumbnailUrl,
                size = SubscriberTileMetrics.ThumbnailSize,
                cornerRadius = SubscriberTileMetrics.ThumbnailCornerRadius,
                modifier = Modifier.align(Alignment.Center),
                // iOS leaves the placeholder diamond transparent — the
                // call screen's own background shows through it, the
                // same as the rest of the tile.
                background = Color.Transparent,
                // The camera-off thumbnail is outlined in white so the
                // diamond still reads as an avatar against the orange
                // call background.
                border = BorderStroke(
                    width = SubscriberTileMetrics.ThumbnailBorderWidth,
                    brush = SolidColor(VideoCallPalette.OnBackground),
                ),
            )
        }

        // The drop shadow has to follow the label's glyphs only.
        //
        // iOS applies `.shadow(radius: 5)` to the overlay stack, and
        // SwiftUI casts the shadow from the *rendered* content — only
        // the text and the badge, not the transparent stack. The
        // Android port had it as `Modifier.shadow` on a full-size
        // overlay `Column`, which draws the shadow from the layer's
        // outline, i.e. the whole tile. That showed up as a thin
        // translucent black frame around the live video.
        //
        // A `TextStyle` shadow reproduces the iOS result: the shadow
        // follows the drawn glyphs.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(SubscriberTileMetrics.OverlayPadding),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = participant.userName,
                    color = VideoCallPalette.OnBackground,
                    style = TextStyle(
                        fontFamily = Satoshi,
                        fontWeight = FontWeight.Normal,
                        fontSize = SubscriberTileMetrics.NameSize,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            offset = Offset(0f, 2f),
                            blurRadius = with(LocalDensity.current) {
                                SubscriberTileMetrics.OverlayShadowRadius.toPx()
                            },
                        ),
                    ),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (participant.raisedHand) {
                    // The raised-hand badge is white, so it vanishes on a
                    // white tile. iOS gets this for free from the
                    // overlay stack's `.shadow(radius: 5)`; on Android
                    // the name's shadow is drawn per-glyph via
                    // `TextStyle`, so the icon needs its own pass: the
                    // same glyph, black and blurred, offset downwards
                    // and painted behind the white one.
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PanTool,
                            contentDescription = null,
                            tint = Color.Black.copy(alpha = 0.45f),
                            modifier = Modifier
                                // iOS raises a left hand (`hand.raised`);
                                // Material's `pan_tool` is a right hand,
                                // so the glyph is mirrored.
                                .scale(scaleX = -1f, scaleY = 1f)
                                .offset(y = SubscriberTileMetrics.RaisedHandShadowOffset)
                                .blur(SubscriberTileMetrics.RaisedHandShadowBlur)
                                .size(SubscriberTileMetrics.RaisedHandSize),
                        )
                        Icon(
                            imageVector = Icons.Filled.PanTool,
                            contentDescription = null,
                            tint = VideoCallPalette.OnBackground,
                            modifier = Modifier
                                .scale(scaleX = -1f, scaleY = 1f)
                                .size(SubscriberTileMetrics.RaisedHandSize),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Metrics taken from iOS `SubscriberContainer.Constants` and the
 * overlay's own `.padding(8)` / `.font(.lato(14))`.
 */
internal object SubscriberTileMetrics {
    val CornerRadius = 10.dp
    val ThumbnailSize = 70.dp
    val ThumbnailCornerRadius = 10.dp
    val ThumbnailBorderWidth = 1.dp
    val RaisedHandSize = 25.dp
    val OverlayPadding = 8.dp
    val OverlayShadowRadius = 5.dp
    val RaisedHandShadowOffset = 1.dp
    val RaisedHandShadowBlur = 3.dp
    val NameSize = 14.sp
}

private const val TAG = "VideoCallUI"

@Preview(showBackground = true, name = "Subscriber tile — avatar")
@Composable
private fun SubscriberTileAvatarPreview() {
    KornerzTheme(darkTheme = false) {
        SubscriberTile(
            participant = VideoCallParticipant.preview(
                userName = "Ada Lovelace",
                hasVideo = false,
            ),
            modifier = Modifier.size(160.dp, 120.dp),
        )
    }
}
