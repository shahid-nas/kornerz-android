package com.kornerz.feature.shell

/**
 * The Korner-detail destination the shell is asked to open.
 *
 * The Korner detail lives inside the Kornerz tab's **nested**
 * `NavHost`, so the shell cannot push it on the root controller — it
 * writes the target to the pending holder and the nested controller
 * consumes it.
 *
 * @param kornerId the Korner to open — its Firestore document id.
 * @param themeColorHex the accent colour the opening screen painted
 * the Korner tile with, as a `#RRGGBB` hex string. The detail screen
 * inherits it, so the Korner keeps one colour across the carousel and
 * the detail (iOS passes the tile's colour to the detail screen the
 * same way — `HomeTabCoordinator.navigateToKornerDetail(_:color:)`).
 * Empty when the opening screen has no colour — a deep link, a nook
 * detail — and the detail falls back to the Korner document's own
 * theme colour.
 */
data class KornerDetailTarget(
    val kornerId: String,
    val themeColorHex: String = "",
)
