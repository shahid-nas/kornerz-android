package com.kornerz.core.ui.components.nooks

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Shared date formatter for the Nooks tab list (`ScheduledNookCard`).
 * Mirrors the iOS `Event.formatedStartDate` (Lato 11 regular →
 * Satoshi 11sp Regular, `EEE hh:mm a`).
 */
fun formatNookStartDate(instant: Instant): String {
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val day = ldt.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val hour = ldt.hour
    val minute = ldt.minute
    val ampm = if (hour < 12) "AM" else "PM"
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val mm = if (minute < 10) "0$minute" else "$minute"
    return "$day $hour12:$mm $ampm"
}
