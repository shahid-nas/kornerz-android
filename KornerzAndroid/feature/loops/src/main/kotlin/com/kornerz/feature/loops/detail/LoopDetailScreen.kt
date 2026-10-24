package com.kornerz.feature.loops.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.feature.loops.components.LoopAddCommentBar
import com.kornerz.feature.loops.video.VideoPlayerView

/**
 * Entry Composable for the loop detail screen (architecture §2.1).
 *
 * The screen owns the `Scaffold`: the add-comment bar lives in its
 * `bottomBar`, so it stays pinned above the system navigation inset
 * and never scrolls away — the Android equivalent of iOS's
 * `safeAreaInset(edge: .bottom)`.
 *
 * The **back affordance** is rendered by [LoopDetailContent] as a
 * control floating over the media area — iOS hangs it on a transparent
 * navigation bar that overlays the media. The system back gesture
 * takes the same route as that control: both raise
 * [LoopDetailEvent.BackTapped], which the ViewModel turns into a
 * single [LoopDetailNavEvent.Pop].
 */
@Composable
fun LoopDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: LoopDetailViewModel = hiltViewModel(),
    onNavEvent: (LoopDetailNavEvent) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { onNavEvent(it) }
    }
    LaunchedEffect(viewModel) {
        viewModel.incrementViewCountOnce()
    }

    // The system back gesture takes the same route as the floating
    // back control — both raise `BackTapped`, so the screen leaves
    // through one event.
    BackHandler(enabled = true) {
        viewModel.onEvent(LoopDetailEvent.BackTapped)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            LoopAddCommentBar(
                avatarUrl = state.currentUserAvatarUrl,
                onClick = { viewModel.onEvent(LoopDetailEvent.AddCommentTapped(viewModel.loopId)) },
            )
        },
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LoopDetailContent(state = state, onEvent = viewModel::onEvent)
        }
    }

    val videoUrl = state.loop?.firebaseVideoUrl
    if (state.isVideoPlayerOpen && !videoUrl.isNullOrBlank()) {
        LoopDetailVideoDialog(
            videoUrl = videoUrl,
            onDismiss = { viewModel.onEvent(LoopDetailEvent.VideoDismissed) },
        )
    }
}

/**
 * The modal video player. iOS presents an `AVPlayerViewController` from
 * the media tap; Android keeps ExoPlayer (the library catalog has no
 * `AVPlayerViewController` equivalent) but adopts the same
 * **interaction** — the media area shows a poster, and the player
 * lives in a modal.
 */
@Composable
private fun LoopDetailVideoDialog(
    videoUrl: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            VideoPlayerView(
                videoUrl = videoUrl,
                autoPlay = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
