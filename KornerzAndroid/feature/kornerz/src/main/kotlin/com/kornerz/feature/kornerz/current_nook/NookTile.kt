package com.kornerz.feature.kornerz.current_nook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kornerz.core.model.Nook
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Single nook tile, shared by the Current Nook section (`CurrentNookSection`)
 * and the Nooks list (`NooksListSection`) per spec `korner-detail/spec.md`
 * §4.9. 16:9 Coil cover (12dp corner radius), Satoshi Bold 14sp title,
 * Satoshi Regular 12sp formatted `startDate`, red dot when `isLive`.
 *
 * Mirrors iOS `KornerNookView.swift`.
 */
@Composable
fun NookTile(
    nook: Nook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            AsyncImage(
                model = nook.coverUrl,
                contentDescription = nook.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (nook.isLive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE74C3C)),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = nook.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = formatStartDate(nook.startDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun formatStartDate(instant: Instant): String {
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = ldt.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$month ${ldt.dayOfMonth}, ${ldt.year}"
}

@Preview(showBackground = true)
@Composable
private fun NookTilePreview() {
    KornerzTheme(darkTheme = false) {
        NookTile(
            nook = Nook(
                id = "n1",
                communityId = "c1",
                title = "Friday Fireside",
                startDate = Instant.parse("2026-10-10T19:00:00Z"),
                isLive = true,
            ),
            onClick = {},
        )
    }
}
