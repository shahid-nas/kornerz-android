package com.kornerz.feature.auth

/**
 * Sealed list of every route in the `auth_graph` plus the typed factory
 * functions the `NavHost` builder uses (architecture §4). Avoids stringly
 * typed routes scattered across the module.
 */
sealed class AuthRoute(val route: String) {

    data object Welcome : AuthRoute("auth/welcome")

    data object Login : AuthRoute("auth/login")

    data object Signup : AuthRoute("auth/signup/{step}") {
        const val ARG_STEP = "step"
        fun build(step: Int): String = "auth/signup/$step"
    }

    data object PrivacyPolicy : AuthRoute("auth/privacy_policy")

    data object ForgotPassword : AuthRoute("auth/forgot_password")

    data object OtpConfirm : AuthRoute("auth/otp_confirm/{email}") {
        const val ARG_EMAIL = "email"
        fun build(email: String): String = "auth/otp_confirm/${email}"
    }

    data object ChangePassword : AuthRoute("auth/change_password/{email}/{token}") {
        const val ARG_EMAIL = "email"
        const val ARG_TOKEN = "token"
        fun build(email: String, token: String): String =
            "auth/change_password/$email/$token"
    }
}