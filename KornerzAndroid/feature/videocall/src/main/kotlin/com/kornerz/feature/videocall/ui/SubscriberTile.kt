package com.kornerz.feature.videocall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(VideoCallPalette.VideoBackdrop),
    ) {
        val subscriber = participant.subscriber
        if (participant.hasVideo && subscriber != null) {
            SubscriberContainer(
                subscriber = subscriber,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            KornerzDiamondImage(
                imageUrl = participant.thumbnailUrl,
                size = SubscriberTileMetrics.ThumbnailSize,
                cornerRadius = SubscriberTileMetrics.ThumbnailCornerRadius,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .shadow(elevation = SubscriberTileMetrics.OverlayShadowRadius)
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
                    ),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (participant.raisedHand) {
                    Icon(
                        imageVector = Icons.Filled.PanTool,
                        contentDescription = null,
                        tint = VideoCallPalette.OnBackground,
                        modifier = Modifier.size(SubscriberTileMetrics.RaisedHandSize),
                    )
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
    val ThumbnailSize = 70.dp
    val ThumbnailCornerRadius = 10.dp
    val RaisedHandSize = 25.dp
    val OverlayPadding = 8.dp
    val OverlayShadowRadius = 5.dp
    val NameSize = 14.sp
}

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
