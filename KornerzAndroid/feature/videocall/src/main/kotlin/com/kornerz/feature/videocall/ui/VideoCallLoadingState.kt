package com.kornerz.feature.videocall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.CallSession
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * Full-screen overlay rendered while the call is connecting. Mirrors
 * iOS `VideoCallLoadingState.swift`. `status` is one of the strings
 * defined in `CallSession.STATUS_*`.
 */
@Composable
fun VideoCallLoadingState(
    status: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.scale(1.5f),
            )
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = when (status) {
                    CallSession.STATUS_AUTHENTICATING -> "Authenticating"
                    CallSession.STATUS_CONNECTING -> "Connecting"
                    CallSession.STATUS_CONNECTED -> "Waiting for others to join"
                    CallSession.STATUS_FAILED -> "Failed"
                    null -> "Connecting"
                    else -> status
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "VideoCallLoadingState — Authenticating")
@Composable
private fun VideoCallLoadingAuthenticatingPreview() {
    KornerzTheme(darkTheme = false) {
        VideoCallLoadingState(status = CallSession.STATUS_AUTHENTICATING)
    }
}

@Preview(showBackground = true, name = "VideoCallLoadingState — Connecting")
@Composable
private fun VideoCallLoadingConnectingPreview() {
    KornerzTheme(darkTheme = false) {
        VideoCallLoadingState(status = CallSession.STATUS_CONNECTING)
    }
}

@Preview(showBackground = true, name = "VideoCallLoadingState — Waiting")
@Composable
private fun VideoCallLoadingWaitingPreview() {
    KornerzTheme(darkTheme = false) {
        VideoCallLoadingState(status = CallSession.STATUS_CONNECTED)
    }
}