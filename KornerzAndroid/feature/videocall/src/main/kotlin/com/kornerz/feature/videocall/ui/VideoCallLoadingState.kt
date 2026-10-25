package com.kornerz.feature.videocall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.CallSession
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Satoshi
import com.kornerz.feature.videocall.R
import com.kornerz.feature.videocall.VideoCallState

/**
 * The status overlay drawn on top of the subscriber area.
 *
 * Mirrors iOS `VideoCallView.loadingView`:
 *  - `.authenticating` → "Authenticating, please wait",
 *  - `.connecting` → "Connecting",
 *  - `.waitingForUsers` → "Waiting for others to join" in the
 *    emphasised (18pt bold italic) style,
 *  - `.connected` → no overlay at all.
 *
 * iOS reports a failed call through an alert; Android keeps the
 * equivalent in-call state with a "Try again" action, styled with the
 * same call palette.
 */
@Composable
internal fun CallStatusOverlay(
    state: VideoCallState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = (state.callStatus as? AsyncValue.Success)?.data?.status
    when {
        state.callStatus is AsyncValue.Error -> VideoCallErrorState(
            message = stringResource(R.string.video_call_error_title),
            onRetry = onRetry,
            modifier = modifier,
        )

        status == CallSession.STATUS_FAILED -> VideoCallErrorState(
            message = stringResource(R.string.video_call_error_title),
            onRetry = onRetry,
            modifier = modifier,
        )

        status == CallSession.STATUS_AUTHENTICATING -> VideoCallLoadingState(
            message = stringResource(R.string.video_call_authenticating),
            modifier = modifier,
        )

        status == CallSession.STATUS_CONNECTING || state.callStatus is AsyncValue.Loading ->
            VideoCallLoadingState(
                message = stringResource(R.string.video_call_connecting),
                modifier = modifier,
            )

        status == CallSession.STATUS_CONNECTED && state.participants.isEmpty() ->
            VideoCallLoadingState(
                message = stringResource(R.string.video_call_waiting_for_others),
                emphasis = true,
                modifier = modifier,
            )
    }
}

/**
 * Centred spinner plus status copy. Mirrors iOS
 * `VideoCallView.loadingView` (white `ProgressView` scaled 1.5× with
 * 16pt of spacing to the message).
 */
@Composable
internal fun VideoCallLoadingState(
    message: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                color = VideoCallPalette.OnBackground,
                modifier = Modifier.scale(1.5f),
            )
            Text(
                text = message,
                color = VideoCallPalette.OnBackground,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = Satoshi,
                    fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (emphasis) FontStyle.Italic else FontStyle.Normal,
                    fontSize = if (emphasis) 18.sp else 16.sp,
                ),
            )
        }
    }
}

/**
 * Failure state with a "Try again" action. Android-specific — iOS
 * raises an alert instead — but painted with the call palette.
 */
@Composable
internal fun VideoCallErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = message,
                color = VideoCallPalette.OnBackground,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                ),
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VideoCallPalette.Control,
                    contentColor = VideoCallPalette.Tile,
                ),
            ) {
                Text(text = stringResource(R.string.video_call_try_again))
            }
        }
    }
}

@Preview(showBackground = true, name = "VideoCallLoadingState — Connecting")
@Composable
private fun VideoCallLoadingConnectingPreview() {
    KornerzTheme(darkTheme = false) {
        VideoCallLoadingState(message = "Connecting")
    }
}

@Preview(showBackground = true, name = "VideoCallLoadingState — Waiting")
@Composable
private fun VideoCallLoadingWaitingPreview() {
    KornerzTheme(darkTheme = false) {
        VideoCallLoadingState(message = "Waiting for others to join", emphasis = true)
    }
}
