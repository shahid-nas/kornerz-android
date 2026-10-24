package com.kornerz.feature.loops.reply

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.feature.loops.components.LoopAddCommentBar

/**
 * Entry Composable for the reply thread (inventory 6.10).
 *
 * 1:1 port of iOS `LoopReplyScreen.swift`: the thread shows the parent
 * comment pinned above the replies, and the **add-comment bar is
 * pinned below** — the same bar the loop detail screen uses. Tapping
 * it opens the create-comment sheet in reply mode (iOS
 * `showAddCommentSheet(type: .replyToComment(loopId, commentInfo))`).
 *
 * The screen never references a `NavController` (architecture §10): the
 * ViewModel raises a [ReplyNavEvent] and the host route resolves it —
 * see [onNavEvent], wired by `AppShell`'s Loops tab.
 */
@Composable
fun ReplyScreen(
    modifier: Modifier = Modifier,
    viewModel: ReplyViewModel = hiltViewModel(),
    onNavEvent: (ReplyNavEvent) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event -> onNavEvent(event) }
    }

    // The system back gesture takes the same route as the header's
    // back arrow — both raise `ReplyEvent.BackTapped`, which the
    // ViewModel turns into a single `ReplyNavEvent.Pop`.
    BackHandler(enabled = true) {
        viewModel.onEvent(ReplyEvent.BackTapped)
    }

    Scaffold(
        bottomBar = {
            LoopAddCommentBar(
                avatarUrl = state.currentUserAvatarUrl,
                onClick = { viewModel.onEvent(ReplyEvent.AddCommentTapped) },
            )
        },
        modifier = modifier,
    ) { padding ->
        ReplyContent(
            state = state,
            onEvent = viewModel::onEvent,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}
