package com.kornerz.feature.loops

import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.kornerz.core.model.LoopAccentPalette

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
        /**
         * The Loops tab's feed route.
         *
         * It deliberately lives **outside** the `loops/` namespace. The
         * detail's pattern `loops/{loopId}` matches every route of the
         * form `loops/<segment>`, and navigation resolves a route
         * against a graph's destinations in **id-hash order** — not
         * registration order — taking the first match. With the feed on
         * `loops/list` the detail pattern won that race and the tab
         * opened the loop detail for a loop called `list` instead of the
         * feed. A first segment of its own (`loops-feed/…`) can never be
         * captured by a `loops/{loopId}`-style pattern.
         */
        const val route: String = "loops-feed/list"
    }

    data object Detail : LoopsRoute() {
        const val ARG_LOOP_ID: String = "loopId"

        /**
         * Whether the loop detail hides the shell's bottom bar.
         *
         * iOS pushes the loop detail as a **full-screen** destination
         * from the Home carousel, the Korner detail and a deep link —
         * the tab bar is hidden there — while the Loops tab's own list
         * keeps it. The flag travels as a nav argument so the shell can
         * read it off the destination instead of guessing.
         */
        const val ARG_HIDE_BOTTOM_BAR: String = "hideBottomBar"

        /**
         * The per-loop accent colour, handed over by the screen that
         * opened the loop.
         *
         * iOS passes the **feed row's** colour to the detail screen
         * (`LoopsTabView.swift:114-118` →
         * `LoopsTabCoordinator.navigateToLoopDetail(_:color:)`), so the
         * detail paints itself in the very colour the user tapped.
         * Android carries the same colour as a nav argument: the six
         * hex digits, **without** the `#`, because a navigation route
         * is URI-shaped and a `#` would be read as the fragment
         * separator.
         *
         * Empty when the destination was opened without one — a deep
         * link, a notification — and the screen falls back to the
         * loop-id-derived palette entry.
         */
        const val ARG_ACCENT_COLOR_HEX: String = "accentColorHex"

        const val PATTERN: String =
            "loops/{$ARG_LOOP_ID}?$ARG_HIDE_BOTTOM_BAR={$ARG_HIDE_BOTTOM_BAR}" +
                "&$ARG_ACCENT_COLOR_HEX={$ARG_ACCENT_COLOR_HEX}"

        fun build(
            loopId: String,
            hideBottomBar: Boolean = false,
            accentColorHex: String = "",
        ): String {
            val route = "loops/$loopId?$ARG_HIDE_BOTTOM_BAR=$hideBottomBar"
            val color = LoopAccentPalette.routeArgument(accentColorHex)
            return if (color.isBlank()) route else "$route&$ARG_ACCENT_COLOR_HEX=$color"
        }
    }

    data object Comment : LoopsRoute() {
        const val ARG_LOOP_ID: String = "loopId"

        /**
         * The comment being replied to.
         *
         * Empty for a top-level comment; set when the create-comment
         * sheet is opened from the reply thread's add-comment bar —
         * iOS `CreateCommentType.replyToComment(loopId, commentInfo)`.
         */
        const val ARG_PARENT_COMMENT_ID: String = "parentCommentId"

        /**
         * The accent colour the detail screen was painted with — the
         * sheet is opened from that screen, so it inherits the colour
         * (iOS `CreateCommentScreen` renders the loop's title in
         * `loopInfo.color`).
         */
        const val ARG_ACCENT_COLOR_HEX: String = "accentColorHex"

        const val PATTERN: String =
            "loops/{$ARG_LOOP_ID}/comment?$ARG_PARENT_COMMENT_ID={$ARG_PARENT_COMMENT_ID}" +
                "&$ARG_ACCENT_COLOR_HEX={$ARG_ACCENT_COLOR_HEX}"

        fun build(
            loopId: String,
            parentCommentId: String = "",
            accentColorHex: String = "",
        ): String {
            val route = "loops/$loopId/comment?$ARG_PARENT_COMMENT_ID=$parentCommentId"
            val color = LoopAccentPalette.routeArgument(accentColorHex)
            return if (color.isBlank()) route else "$route&$ARG_ACCENT_COLOR_HEX=$color"
        }
    }

    data object Reply : LoopsRoute() {
        const val ARG_LOOP_ID: String = "loopId"
        const val ARG_COMMENT_ID: String = "commentId"

        /**
         * The accent colour the detail screen was painted with — the
         * reply thread is opened from that screen, so it inherits the
         * colour.
         */
        const val ARG_ACCENT_COLOR_HEX: String = "accentColorHex"

        const val PATTERN: String =
            "loops/{$ARG_LOOP_ID}/reply/{$ARG_COMMENT_ID}" +
                "?$ARG_ACCENT_COLOR_HEX={$ARG_ACCENT_COLOR_HEX}"

        fun build(
            loopId: String,
            commentId: String,
            accentColorHex: String = "",
        ): String {
            val route = "loops/$loopId/reply/$commentId"
            val color = LoopAccentPalette.routeArgument(accentColorHex)
            return if (color.isBlank()) route else "$route?$ARG_ACCENT_COLOR_HEX=$color"
        }
    }
}

val loopsDetailNavArguments = listOf(
    navArgument(LoopsRoute.Detail.ARG_LOOP_ID) { type = NavType.StringType },
    navArgument(LoopsRoute.Detail.ARG_HIDE_BOTTOM_BAR) {
        type = NavType.BoolType
        defaultValue = false
    },
    navArgument(LoopsRoute.Detail.ARG_ACCENT_COLOR_HEX) {
        type = NavType.StringType
        defaultValue = ""
    },
)

val loopsCommentNavArguments = listOf(
    navArgument(LoopsRoute.Comment.ARG_LOOP_ID) { type = NavType.StringType },
    navArgument(LoopsRoute.Comment.ARG_PARENT_COMMENT_ID) {
        type = NavType.StringType
        defaultValue = ""
    },
    navArgument(LoopsRoute.Comment.ARG_ACCENT_COLOR_HEX) {
        type = NavType.StringType
        defaultValue = ""
    },
)

val loopsReplyNavArguments = listOf(
    navArgument(LoopsRoute.Reply.ARG_LOOP_ID) { type = NavType.StringType },
    navArgument(LoopsRoute.Reply.ARG_COMMENT_ID) { type = NavType.StringType },
    navArgument(LoopsRoute.Reply.ARG_ACCENT_COLOR_HEX) {
        type = NavType.StringType
        defaultValue = ""
    },
)
