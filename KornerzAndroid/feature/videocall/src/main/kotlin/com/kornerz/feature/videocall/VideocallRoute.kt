package com.kornerz.feature.videocall

import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kornerz.feature.videocall.ui.VideoCallScreen

/**
 * Top-level route constants for the video call screen. Lives in the
 * Nooks tab's nested `NavHost` so the Join button from `LiveKornerCard`
 * can push into it without leaving the originating tab (design D7).
 */
sealed class VideocallRoute {
    data object Call : VideocallRoute() {
        const val ARG_SESSION_ID: String = VideoCallViewModel.ARG_SESSION_ID
        const val PATTERN: String = "videocall/{$ARG_SESSION_ID}"
        fun build(sessionId: String): String = "videocall/$sessionId"
    }
}

/**
 * NavArgument list for [VideocallRoute.Call.PATTERN] — single
 * `sessionId` `String` argument that the ViewModel pulls from the
 * SavedStateHandle.
 */
val videocallNavArguments = listOf(
    navArgument(VideocallRoute.Call.ARG_SESSION_ID) { type = NavType.StringType },
)

/**
 * `composable("videocall/{sessionId}")` factory. The VM reads
 * `sessionId` from `SavedStateHandle["sessionId"]`. Mirrors the
 * pattern used by `NookDetailRoute` / `NooksRoute.Detail.PATTERN`.
 */
fun androidx.navigation.NavGraphBuilder.videocallGraph(
    onNavEvent: (VideoCallNavEvent) -> Unit = {},
) {
    composable(
        route = VideocallRoute.Call.PATTERN,
        arguments = videocallNavArguments,
    ) {
        VideoCallScreen(
            onNavEvent = onNavEvent,
        )
    }
}