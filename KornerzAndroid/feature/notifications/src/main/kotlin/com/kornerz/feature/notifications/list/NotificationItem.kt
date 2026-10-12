package com.kornerz.feature.notifications.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kornerz.core.ui.R
import com.kornerz.core.ui.theme.KornerzColors
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A single notification card (P6, task 6.5). Mirrors iOS
 * `NotificationItem` (lines 76–125 of `NotificationScreen.swift`).
 *
 * Visual spec (per `notifications-list` spec):
 * - 20-pt rounded card, `colorScheme.surfaceVariant` bg,
 *   1-px `colorScheme.outlineVariant` stroke
 * - 16-pt padding
 * - 40-pt circular avatar (Coil + `R.drawable.ic_community_placeholder`
 *   fallback — `notification.thumbnailURL` is always `null` per
 *   the `user-notification-domain` spec)
 * - Header text source: `notification.content` (community name)
 *   in 14-pt `PurpleGreyLight`
 * - Date: formatted from `notification.date` (Instant) in 10-pt
 *   `PurpleGreyLight`
 * - Description: Satoshi Black 16-pt, `InkLight`
 * - 20-pt vertical spacing between header block and description
 *   (mirrors iOS `VStack(spacing: 20)` line 81)
 */
@Composable
fun NotificationItem(
    row: UserNotificationRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notification = row.notification
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val cover = row.community?.coverUrl
                if (!cover.isNullOrEmpty()) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                    )
                } else {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.ic_community_placeholder),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = notification.content.orEmpty(),
                        color = KornerzColors.PurpleGreyLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = formatRelativeDate(notification.date),
                        color = KornerzColors.PurpleGreyLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = notification.description.orEmpty(),
                color = KornerzColors.InkLight,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/**
 * Formats an `Instant` into the user-facing date string used by the
 * notification row. Mirrors iOS
 * `notification.date?.dateValue().formatted()` (`NotificationScreen.swift`
 * line 115) — the iOS default uses the system locale, so we use the
 * device timezone + a short `MMM d, HH:mm` pattern.
 */
private fun formatRelativeDate(instant: kotlinx.datetime.Instant?): String {
    if (instant == null) return ""
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = local.month.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
    val day = local.dayOfMonth
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "$month $day, $hour:$minute"
}

@Suppress("unused")
private val now = Clock.System.now()
