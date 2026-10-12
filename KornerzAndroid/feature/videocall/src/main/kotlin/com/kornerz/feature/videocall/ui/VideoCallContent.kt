package com.kornerz.feature.videocall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.CallSession
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.feature.videocall.VideoCallEvent
import com.kornerz.feature.videocall.VideoCallState

private val VideoCallBackground = Color(0xE27D5A) // matches iOS AppColor.CE27D5A

/**
 * Stateless renderer for the video call screen. Top toolbar, a
 * centered subscriber list, and the bottom toolbar are wired
 * against [VideoCallState] (architecture §2.2).
 */
@Composable
fun VideoCallContent(
    state: VideoCallState,
    onEvent: (VideoCallEvent) -> Unit,
    permissionsGranted: Boolean,
    onGrantPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VideoCallBackground),
    ) {
        if (!permissionsGranted) {
            PermissionDeniedCard(onGrant = onGrantPermissions)
            return@Box
        }
        Column(modifier = Modifier.fillMaxSize()) {
            TopToolbar()
            Spacer(modifier = Modifier.height(8.dp))
            SubscriberArea(state = state, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(8.dp))
            BottomToolbar(
                isAudioOn = state.isAudioOn,
                isVideoOn = state.isVideoOn,
                isHandRaised = state.isHandRaised,
                onEvent = onEvent,
            )
        }
        when (val value = state.callStatus) {
            is AsyncValue.Loading -> VideoCallLoadingState(status = null)
            is AsyncValue.Error -> VideoCallLoadingState(status = null)
            is AsyncValue.Success -> {
                if (value.data.status != CallSession.STATUS_CONNECTED) {
                    VideoCallLoadingState(status = value.data.status)
                }
            }
        }
    }
}

@Composable
private fun TopToolbar() {
    Text(
        text = "Video call",
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun SubscriberArea(state: VideoCallState, modifier: Modifier = Modifier) {
    val items = (state.callStatus as? AsyncValue.Success)?.data?.remoteSubscribers ?: emptyList()
    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "Waiting for others to join",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.streamId }) { snapshot ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = snapshot.subscriberName ?: snapshot.streamId,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun BottomToolbar(
    isAudioOn: Boolean,
    isVideoOn: Boolean,
    isHandRaised: Boolean,
    onEvent: (VideoCallEvent) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onEvent(VideoCallEvent.ToggleAudio) }) {
            Icon(
                imageVector = if (isAudioOn) Icons.Filled.Mic else Icons.Filled.MicOff,
                contentDescription = "Toggle audio",
                tint = Color.White,
            )
        }
        IconButton(onClick = { onEvent(VideoCallEvent.ToggleVideo) }) {
            Icon(
                imageVector = if (isVideoOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                contentDescription = "Toggle video",
                tint = Color.White,
            )
        }
        IconButton(onClick = { onEvent(VideoCallEvent.FlipCamera) }) {
            Icon(
                imageVector = Icons.Filled.Cameraswitch,
                contentDescription = "Flip camera",
                tint = Color.White,
            )
        }
        IconButton(onClick = { onEvent(VideoCallEvent.ToggleHandRaised) }) {
            Icon(
                imageVector = Icons.Filled.PanTool,
                contentDescription = "Toggle hand raised",
                tint = if (isHandRaised) Color.Yellow else Color.White,
            )
        }
        IconButton(onClick = { onEvent(VideoCallEvent.EndCall) }) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = "End call",
                tint = Color.Red,
            )
        }
    }
}

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
            text = "Camera and microphone access is required to join a video call",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.size(16.dp))
        Button(onClick = onGrant) {
            Text("Grant permissions")
        }
    }
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
            ),
            onEvent = {},
            permissionsGranted = true,
            onGrantPermissions = {},
        )
    }
}

@Preview(showBackground = true, name = "VideoCallContent — Loading")
@Composable
private fun VideoCallContentLoadingPreview() {
    KornerzTheme(darkTheme = false) {
        VideoCallContent(
            state = VideoCallState(callStatus = AsyncValue.Loading),
            onEvent = {},
            permissionsGranted = true,
            onGrantPermissions = {},
        )
    }
}

@Preview(showBackground = true, name = "VideoCallContent — Dark")
@Composable
private fun VideoCallContentDarkPreview() {
    KornerzTheme(darkTheme = true) {
        VideoCallContent(
            state = VideoCallState(
                callStatus = AsyncValue.Success(
                    CallSession(sessionId = "abc", status = CallSession.STATUS_CONNECTED),
                ),
            ),
            onEvent = {},
            permissionsGranted = true,
            onGrantPermissions = {},
        )
    }
}