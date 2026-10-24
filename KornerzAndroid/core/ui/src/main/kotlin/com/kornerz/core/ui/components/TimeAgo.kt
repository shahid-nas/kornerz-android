package com.kornerz.core.ui.components

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Relative-time formatter shared by every screen that renders a
 * timestamp. 1:1 Android counterpart of iOS
 * `kornerz/Kornerz/Extensions/Date+Extension.swift`'s
 * `timeAgoSinceDate()`.
 *
 * The formatter renders the **largest fitting unit** from seconds,
 * minutes, hours, days, weeks, months and years, and returns an empty
 * string for a `null` instant.
 *
 * This is the single relative-time implementation in the app: the loops
 * module's private `formatTimeAgo` helper was removed and replaced by
 * this one, so the loop detail title row and the comment rows cannot
 * disagree about how a timestamp reads.
 *
 * @param instant the moment to describe; `null` renders an empty string.
 * @param now the reference moment — injectable so the formatter can be
 * tested deterministically.
 */
fun timeAgoSinceDate(
    instant: Instant?,
    now: Instant = Clock.System.now(),
): String {
    if (instant == null) return ""

    val earlier = if (instant < now) instant else now
    val duration = now - earlier

    val seconds = duration.inWholeSeconds
    val minutes = duration.inWholeMinutes
    val hours = duration.inWholeHours
    val days = duration.inWholeDays

    val zone = TimeZone.currentSystemDefault()
    val start = earlier.toLocalDateTime(zone)
    val end = now.toLocalDateTime(zone)
    // Calendar-aware month count, matching iOS's `Calendar` component
    // decomposition: a month only counts once the day-of-month has been
    // reached.
    var months = (end.year - start.year) * 12 + (end.monthNumber - start.monthNumber)
    if (end.dayOfMonth < start.dayOfMonth) months -= 1
    val years = months / 12

    return when {
        years >= 1 -> "${years}y ago"
        months >= 1 -> "${months}mo ago"
        days >= 7 -> "${days / 7}w ago"
        days >= 1 -> "${days}d ago"
        hours >= 1 -> "${hours}h ago"
        minutes >= 1 -> "${minutes}m ago"
        seconds >= 1 -> "${seconds}s ago"
        else -> "1s ago"
    }
}
