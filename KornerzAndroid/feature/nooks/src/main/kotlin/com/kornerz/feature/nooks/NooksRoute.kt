package com.kornerz.feature.nooks

import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/**
 * Top-level route constants for the Nooks tab's nested NavHost.
 *
 * Two destinations — `nooks/list` (root feed) and `nooks/{nookId}`
 * (per-tab detail route). The same `nooks/{nookId}` pattern is also
 * exposed inside the Kornerz tab's nested NavHost so the Korner
 * detail's nook tiles can push into it without leaving the Kornerz
 * tab.
 */
sealed class NooksRoute {
    data object List : NooksRoute() {
        const val route: String = "nooks/list"
    }

    data object Detail : NooksRoute() {
        const val ARG_NOOK_ID: String = "nookId"
        const val PATTERN: String = "nooks/{$ARG_NOOK_ID}"
        fun build(nookId: String): String = "nooks/$nookId"
    }
}

/**
 * NavArgument list for [NooksRoute.Detail.PATTERN] — single `nookId`
 * `String` argument that the ViewModel pulls from the SavedStateHandle.
 */
val nooksDetailNavArguments = listOf(
    navArgument(NooksRoute.Detail.ARG_NOOK_ID) { type = NavType.StringType },
)
