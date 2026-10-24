package com.kornerz.feature.shell

/**
 * The loop-detail destination the shell is asked to open.
 *
 * The loop detail lives inside the Loops tab's **nested** `NavHost`, so
 * the shell cannot push it on the root controller — it writes the
 * target to the pending holder and the nested controller consumes it.
 *
 * @param loopId the loop to open.
 * @param hideBottomBar whether the shell's bottom bar is hidden while
 * the destination is on screen. iOS presents the loop detail as a
 * full-screen destination from the Home carousel, the Korner detail and
 * a deep link — the tab bar is hidden there — while the Loops tab's own
 * list keeps it.
 * @param accentColorHex the per-loop accent colour the opening screen
 * painted the loop with, as a `#RRGGBB` hex string. The detail screen
 * inherits it, so the loop keeps one colour across the feed and the
 * detail (iOS passes the feed row's colour to the detail screen).
 * Empty when the opening screen has no colour — a deep link, a
 * notification — and the detail falls back to the loop-id-derived
 * palette entry.
 */
data class LoopDetailTarget(
    val loopId: String,
    val hideBottomBar: Boolean = false,
    val accentColorHex: String = "",
)
