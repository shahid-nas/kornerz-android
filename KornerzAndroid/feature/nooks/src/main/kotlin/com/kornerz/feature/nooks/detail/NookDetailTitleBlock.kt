package com.kornerz.feature.nooks.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.theme.Spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Nook title + localized start date (Satoshi Bold 24sp / Regular 14sp
 * per iOS `NookDetailView.swift`). The detail screen uses the
 * "MMM d, yyyy" date format (the list cards use the
 * "EEE hh:mm a" format — see `formatNookStartDate` in
 * `feature/nooks/components/FormatStartDate.kt`).
 */
@Composable
fun NookDetailTitleBlock(
    title: String,
    startDate: Instant,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = formatDetailDate(startDate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun formatDetailDate(instant: Instant): String {
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = ldt.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$month ${ldt.dayOfMonth}, ${ldt.year}"
}
