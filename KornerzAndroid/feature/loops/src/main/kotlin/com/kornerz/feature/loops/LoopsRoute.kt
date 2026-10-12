package com.kornerz.feature.loops

import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument

/**
 * Top-level routes for the Loops tab's nested `NavHost`. The host
 * shell (`feature/shell/.../AppShell.kt`) registers these inside a
 * per-tab `NavHost` so the bottom navigation bar stays visible across
 * all four destinations.
 *
 * Mirrors the iOS `LoopsTabCoordinator` push pattern.
 */
sealed class LoopsRoute {
    data object List : LoopsRoute() {
        const val route: String = "loops/list"
    }

    data object Detail : LoopsRoute() {
        const val ARG_LOOP_ID: String = "loopId"
        const val PATTERN: String = "loops/{$ARG_LOOP_ID}"
        fun build(loopId: String): String = "loops/$loopId"
    }

    data object Comment : LoopsRoute() {
        const val ARG_LOOP_ID: String = "loopId"
        const val PATTERN: String = "loops/{$ARG_LOOP_ID}/comment"
        fun build(loopId: String): String = "loops/$loopId/comment"
    }

    data object Reply : LoopsRoute() {
        const val ARG_LOOP_ID: String = "loopId"
        const val ARG_COMMENT_ID: String = "commentId"
        const val PATTERN: String = "loops/{$ARG_LOOP_ID}/reply/{$ARG_COMMENT_ID}"
        fun build(loopId: String, commentId: String): String =
            "loops/$loopId/reply/$commentId"
    }
}

val loopsDetailNavArguments = listOf(
    navArgument(LoopsRoute.Detail.ARG_LOOP_ID) { type = NavType.StringType },
)

val loopsCommentNavArguments = listOf(
    navArgument(LoopsRoute.Comment.ARG_LOOP_ID) { type = NavType.StringType },
)

val loopsReplyNavArguments = listOf(
    navArgument(LoopsRoute.Reply.ARG_LOOP_ID) { type = NavType.StringType },
    navArgument(LoopsRoute.Reply.ARG_COMMENT_ID) { type = NavType.StringType },
)
