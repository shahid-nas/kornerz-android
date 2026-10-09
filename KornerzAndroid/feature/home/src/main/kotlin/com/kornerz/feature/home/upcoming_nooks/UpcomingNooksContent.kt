package com.kornerz.feature.home.upcoming_nooks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.Nook
import com.kornerz.core.ui.components.HomeShimmerPlaceholder
import com.kornerz.core.ui.components.KornerzErrorState
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Horizontal carousel of upcoming nooks. 1:1 port of iOS
 * `UpcomingNooksView.swift`. Each tile is a rounded card with a
 * "Thu, Oct 9 · 6:00 PM" label and a LIVE chip when [Nook.isLive].
 */
@Composable
fun UpcomingNooksContent(
    nooks: List<Nook>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Upcoming Nooks",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space4),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.space8),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            items(nooks, key = { it.id }) { nook ->
                NookCard(nook = nook, onClick = onClick)
            }
        }
    }
}

@Composable
private fun NookCard(nook: Nook, onClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick(nook.id) }
            .padding(Spacing.space4),
    ) {
        if (nook.isLive) {
            Text(
                text = "LIVE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error),
            )
        }
        Text(
            text = formatDate(nook),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = nook.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
fun UpcomingNooksShimmerSection() {
    HomeShimmerPlaceholder(modifier = Modifier.padding(Spacing.space8)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
        )
    }
}

@Composable
fun UpcomingNooksRetrySection(onRetry: () -> Unit) {
    KornerzErrorState(message = "Upcoming nooks failed to load", onRetry = onRetry)
}

private fun formatDate(nook: Nook): String {
    val zone = TimeZone.currentSystemDefault()
    val dateTime = nook.startDate.toLocalDateTime(zone)
    val day = dateTime.dayOfWeek.name.take(3)
    val month = dateTime.month.name.take(3)
    val hour = dateTime.hour
    val minute = dateTime.minute.toString().padStart(2, '0')
    val ampm = if (hour >= 12) "PM" else "AM"
    val h12 = ((hour + 11) % 12 + 1)
    return "$day, $month ${dateTime.dayOfMonth} · $h12:$minute $ampm"
}

@Preview(showBackground = true)
@Composable
private fun UpcomingNooksPreview() {
    KornerzTheme(darkTheme = false) {
        UpcomingNooksContent(
            nooks = listOf(
                Nook(
                    id = "n1",
                    communityId = "c1",
                    title = "Founders sync",
                    startDate = Clock.System.now(),
                    isLive = true,
                ),
            ),
            onClick = {},
        )
    }
}
