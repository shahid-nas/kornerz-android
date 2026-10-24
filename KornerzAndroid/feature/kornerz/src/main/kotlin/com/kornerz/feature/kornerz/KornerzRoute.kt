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
        /**
         * The Kornerz tab's feed route.
         *
         * It deliberately lives **outside** the `kornerz/` namespace: the
         * detail's pattern `kornerz/{kornerId}` matches every route of
         * the form `kornerz/<segment>`, and navigation resolves a route
         * against a graph's destinations in **id-hash order** — not
         * registration order — taking the first match. A first segment
         * of its own (`kornerz-feed/…`) can never be captured by a
         * `kornerz/{kornerId}`-style pattern.
         */
        const val route: String = "kornerz-feed/list"
    }
    data object Detail : KornerzRoute() {
        const val ARG_KORNER_ID: String = "kornerId"
        const val PATTERN: String = "kornerz/{$ARG_KORNER_ID}"
        fun build(kornerId: String): String = "kornerz/$kornerId"
    }
}
