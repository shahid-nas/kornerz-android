package com.kornerz.core.model

/**
 * The community theme palette, mirrors iOS `CommunityThemeId`. Each
 * value carries a 3-color `gradientBrush` (encoded as ARGB longs so
 * the domain model stays Compose-free) for the theme picker (P7,
 * `community-domain` spec).
 *
 * Use the `Brush.verticalGradient` extension in `feature/community`
 * to render the colors.
 */
enum class CommunityTheme(val rawValue: Int, val displayName: String) {
    DEFAULT(0, "Default"),
    RED(1, "Red"),
    BLUE(2, "Blue"),
    YELLOW(3, "Yellow"),
    PURPLE(4, "Purple"),
    GREEN(5, "Green");

    /**
     * Three colors rendered as a top-to-bottom vertical gradient. Each
     * entry is a packed ARGB Long (e.g. `0xFFFF6B6BL`) — Compose
     * converts these to `Color` via `Color(value.toULong().toLong())`
     * (mirrors the iOS `SelectCommunityThemeVC.SelectionView` colors).
     */
    val gradientBrush: List<Long>
        get() = when (this) {
            DEFAULT -> listOf(
                0xFFF2F4F6L,
                0xFFE9EAEEL,
                0xFFD1D3D8L,
            )
            RED -> listOf(
                0xFFFF6B6BL,
                0xFFEE5A52L,
                0xFFC53D3DL,
            )
            BLUE -> listOf(
                0xFF3B82F6L,
                0xFF2563EBL,
                0xFF1E40AFL,
            )
            YELLOW -> listOf(
                0xFFFBBF24L,
                0xFFF59E0BL,
                0xFFD97706L,
            )
            PURPLE -> listOf(
                0xFFA855F7L,
                0xFF9333EAL,
                0xFF7E22CEL,
            )
            GREEN -> listOf(
                0xFF22C55EL,
                0xFF16A34AL,
                0xFF15803DL,
            )
        }

    companion object {
        /**
         * Returns the matching [CommunityTheme] for [raw], or
         * [DEFAULT] for unknown values (matches iOS
         * `CommunityThemeId.from(rawValue:)`).
         */
        fun fromRawValue(raw: Int): CommunityTheme =
            entries.firstOrNull { it.rawValue == raw } ?: DEFAULT
    }
}
