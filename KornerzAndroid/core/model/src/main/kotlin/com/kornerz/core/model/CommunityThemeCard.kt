package com.kornerz.core.model

/**
 * A single theme option in the 2x3 picker grid (P7, `community-domain`
 * spec). Bundles the [theme] with a localized display label and its
 * gradient color palette so the picker doesn't have to re-derive the
 * colors per render.
 */
data class CommunityThemeCard(
    val theme: CommunityTheme,
    val displayLabel: String,
    val gradientBrush: List<Long>,
) {
    companion object {
        /**
         * The full picker grid — one card per [CommunityTheme] entry
         * in enum-declaration order.
         */
        val all: List<CommunityThemeCard> = CommunityTheme.entries.map { theme ->
            CommunityThemeCard(
                theme = theme,
                displayLabel = theme.displayName,
                gradientBrush = theme.gradientBrush,
            )
        }
    }
}
