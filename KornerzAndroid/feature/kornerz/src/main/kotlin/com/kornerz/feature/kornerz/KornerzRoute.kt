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

        /**
         * The Korner's accent colour, handed over by the screen that
         * opened the detail.
         *
         * iOS hands the **tile's** colour to the detail screen
         * (`HomeTabCoordinator.navigateToKornerDetail(_:color:)` and
         * `KornerzTabCoordinator.navigateToCommunityDetail(_:color:)`),
         * so the detail paints itself in the very colour the user
         * tapped. Android carries the same colour as a nav argument:
         * the six hex digits, **without** the `#`, because a navigation
         * route is URI-shaped and a `#` would be read as the fragment
         * separator.
         *
         * Empty when the destination was opened without one — a deep
         * link, a nook detail — and the screen falls back to the
         * Korner document's own theme colour.
         */
        const val ARG_THEME_COLOR_HEX: String = "themeColorHex"

        const val PATTERN: String =
            "kornerz/{$ARG_KORNER_ID}?$ARG_THEME_COLOR_HEX={$ARG_THEME_COLOR_HEX}"

        /**
         * The route of the Korner detail.
         *
         * The colour argument is optional — it is omitted when the
         * opening screen had no colour (a deep link, a nook detail),
         * and the screen then falls back to the Korner document's own
         * theme colour.
         */
        fun build(kornerId: String, themeColorHex: String = ""): String {
            val route = "kornerz/$kornerId"
            val color = themeColorHex.trim().removePrefix("#")
            return if (color.isBlank()) route else "$route?$ARG_THEME_COLOR_HEX=$color"
        }
    }
}
