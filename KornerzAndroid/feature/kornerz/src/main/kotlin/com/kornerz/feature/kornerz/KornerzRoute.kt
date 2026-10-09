package com.kornerz.feature.kornerz

/**
 * Top-level route constants for the Kornerz tab's nested NavHost
 * (spec `kornerz-tab/spec.md`, design §"Decision: One feature module,
 * one nested NavHost inside the Kornerz tab").
 *
 * Sealed class with a single source of truth for the
 * `kornerz/list` and `kornerz/{kornerId}` route patterns.
 */
sealed class KornerzRoute {
    data object List : KornerzRoute() {
        const val route: String = "kornerz/list"
    }
    data object Detail : KornerzRoute() {
        const val ARG_KORNER_ID: String = "kornerId"
        const val PATTERN: String = "kornerz/{$ARG_KORNER_ID}"
        fun build(kornerId: String): String = "kornerz/$kornerId"
    }
}
