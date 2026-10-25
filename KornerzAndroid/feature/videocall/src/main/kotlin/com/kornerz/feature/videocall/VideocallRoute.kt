package com.kornerz.feature.videocall

import android.net.Uri
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
        const val ARG_COMMUNITY_NAME: String = VideoCallViewModel.ARG_COMMUNITY_NAME
        const val ARG_COMMUNITY_ID: String = VideoCallViewModel.ARG_COMMUNITY_ID
        const val PATTERN: String =
            "videocall/{$ARG_SESSION_ID}" +
                "?$ARG_COMMUNITY_NAME={$ARG_COMMUNITY_NAME}" +
                "&$ARG_COMMUNITY_ID={$ARG_COMMUNITY_ID}"
        /**
         * Builds the destination. [communityName] is optional and shows
         * up in the call screen's top toolbar, mirroring iOS
         * `VideoCallViewModel.community.communityName`. [communityId] is
         * the reference the Klouders points award is filed against
         * (iOS `VideoCallViewModel.addGamificationPoints()`).
         */
        fun build(
            sessionId: String,
            communityName: String? = null,
            communityId: String? = null,
        ): String = buildString {
            append("videocall/")
            append(Uri.encode(sessionId))
            val query = buildList {
                if (!communityName.isNullOrBlank()) {
                    add("$ARG_COMMUNITY_NAME=${Uri.encode(communityName)}")
                }
                if (!communityId.isNullOrBlank()) {
                    add("$ARG_COMMUNITY_ID=${Uri.encode(communityId)}")
                }
            }
            if (query.isNotEmpty()) {
                append("?")
                append(query.joinToString("&"))
            }
        }
    }

    companion object {
        /**
         * Route prefix shared by every video call destination. The shell
         * matches on it to decide that the call screen paints
         * edge-to-edge (the `Scaffold`'s system-bar insets are skipped
         * for it).
         */
        const val ROUTE_PREFIX: String = "videocall/"
    }
}

/**
 * NavArgument list for [VideocallRoute.Call.PATTERN] — the
 * `sessionId` `String` argument plus the optional `communityName` and
 * `communityId` query arguments. All are pulled from the
 * `SavedStateHandle`.
 */
val videocallNavArguments = listOf(
    navArgument(VideocallRoute.Call.ARG_SESSION_ID) { type = NavType.StringType },
    navArgument(VideocallRoute.Call.ARG_COMMUNITY_NAME) {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    },
    navArgument(VideocallRoute.Call.ARG_COMMUNITY_ID) {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    },
)

/**
 * `composable("videocall/{sessionId}?communityName={communityName}")`
 * factory. The VM reads `sessionId` and `communityName` from
 * `SavedStateHandle`. Mirrors the pattern used by `NookDetailRoute` /
 * `NooksRoute.Detail.PATTERN`.
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
