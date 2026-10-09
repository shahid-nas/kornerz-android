package com.kornerz.feature.home.components

import kotlinx.datetime.Instant

fun Instant.timeAgoSince(now: Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())): String {
    val diffMs = (now.toEpochMilliseconds() - this.toEpochMilliseconds()).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    val week = 7 * day
    val month = 30L * day
    val year = 365L * day

    return when {
        diffMs < 2 * minute -> "1 minute ago"
        diffMs < hour -> "${diffMs / minute} minutes ago"
        diffMs < 2 * hour -> "an hour ago"
        diffMs < day -> "${diffMs / hour} hours ago"
        diffMs < 2 * day -> "yesterday"
        diffMs < week -> "${diffMs / day} days ago"
        diffMs < 2 * week -> "1 week ago"
        diffMs < month -> "${diffMs / week} weeks ago"
        diffMs < 2 * month -> "1 month ago"
        diffMs < year -> "${diffMs / month} months ago"
        diffMs < 2 * year -> "1 year ago"
        else -> "${diffMs / year} years ago"
    }
}
