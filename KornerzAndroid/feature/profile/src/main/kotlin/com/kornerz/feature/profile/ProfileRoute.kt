package com.kornerz.feature.profile

import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument

/**
 * Top-level routes for the Profile destination (P6, task 9.1 / 9.2).
 * Every route is registered on the shell's root `NavHost` as a
 * `composable` (`View.route` is pushed, not a tab, matching the iOS
 * `UserProfileVC.hidesBottomBarWhenPushed = true` pattern).
 *
 * They are deliberately **not** hosted in a nested `NavHost`: a nested
 * host needs its own `NavHostController`, and a controller shared
 * across several outer back-stack entries crashes with
 * `IllegalStateException: ViewModelStore should be set before setGraph
 * call` when the screen is opened a second time.
 */
sealed class ProfileRoute {
    data object View : ProfileRoute() {
        const val route: String = "profile/view"
    }

    data object EditBasicInfo : ProfileRoute() {
        const val route: String = "profile/edit-basic-info"
    }

    data object ChangeBasicInfo : ProfileRoute() {
        const val ARG_FIELD: String = "field"
        const val PATTERN: String = "profile/edit-basic-info/{$ARG_FIELD}"
        fun build(field: String): String = "profile/edit-basic-info/$field"
    }

    data object ChangeBasicInfoSuccess : ProfileRoute() {
        const val ARG_MESSAGE: String = "message"
        const val PATTERN: String = "profile/edit-basic-info/success/{$ARG_MESSAGE}"
        fun build(message: String): String =
            "profile/edit-basic-info/success/${java.net.URLEncoder.encode(message, "UTF-8")}"
    }

    data object PrivacyPolicy : ProfileRoute() {
        const val route: String = "profile/privacy-policy"
    }
}

internal val profileChangeBasicInfoArguments = listOf(
    navArgument(ProfileRoute.ChangeBasicInfo.ARG_FIELD) { type = NavType.StringType },
)
