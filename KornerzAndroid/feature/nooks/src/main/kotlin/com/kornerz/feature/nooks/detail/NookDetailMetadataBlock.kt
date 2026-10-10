package com.kornerz.feature.nooks.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kornerz.core.ui.components.HomeShimmerPlaceholder
import com.kornerz.core.ui.theme.Spacing

/**
 * Metadata block: red "LIVE" badge (10dp circle + Satoshi Bold 11sp
 * "LIVE" label) when `isLive = true`, 40dp circular Coil avatar for
 * the parent Korner, parent Korner name in Satoshi Medium 14sp.
 *
 * Mirrors iOS `NookDetailView` metadata block.
 */
@Composable
fun NookDetailMetadataBlock(
    isLive: Boolean,
    parentKornerName: String,
    parentKornerCoverUrl: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        if (isLive) {
            LiveBadge()
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            HomeShimmerPlaceholder(modifier = Modifier.size(40.dp)) {
                Box(modifier = Modifier.size(40.dp))
            }
            AsyncImage(
                model = parentKornerCoverUrl,
                contentDescription = parentKornerName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
        }
        Spacer(modifier = Modifier.width(Spacing.space2))
        Text(
            text = parentKornerName,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun LiveBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFFE74C3C)),
        )
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
            color = MaterialTheme.colorScheme.error,
        )
    }
}
