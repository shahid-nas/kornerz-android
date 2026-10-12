package com.kornerz.feature.home.loop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.kornerz.core.model.Loop
import com.kornerz.core.ui.components.HomeSectionHeader
import com.kornerz.core.ui.components.HomeShimmerPlaceholder
import com.kornerz.core.ui.components.KornerzDiamondImage
import com.kornerz.core.ui.components.KornerzErrorState
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.home.components.loopAccentColor
import com.kornerz.feature.home.components.timeAgoSince
import kotlinx.datetime.Instant

@Composable
fun LoopOfDayContent(
    loop: Loop,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Loop of the day",
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        LoopItem(
            loop = loop,
            accentIndex = 0,
            onClick = onClick,
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space4),
        )
    }
}

@Composable
fun LoopItem(
    loop: Loop,
    accentIndex: Int,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(loop.id.orEmpty()) },
        verticalAlignment = Alignment.Top,
    ) {
        LoopThumbnail(loop = loop, accentIndex = accentIndex, modifier = Modifier.size(100.dp))
        Spacer(modifier = Modifier.size(Spacing.space6))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = loop.title.orEmpty().ifEmpty { "Untitled loop" },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
            )
            loop.communityName?.takeIf { it.isNotBlank() }?.let { community ->
                Spacer(Modifier.padding(top = 2.dp))
                Text(
                    text = "in $community",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            loop.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(Modifier.padding(top = 2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
            loop.createdAt?.let { createdAt ->
                Text(
                    text = createdAt.timeAgoSince(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(top = Spacing.space2))
            StatsRow(loop = loop)
        }
    }
}

@Composable
private fun LoopThumbnail(
    loop: Loop,
    accentIndex: Int,
    modifier: Modifier = Modifier,
) {
    val accent = loopAccentColor(accentIndex)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent),
    ) {
        SubcomposeAsyncImage(
            model = loop.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
            loading = {
                Box(modifier = Modifier.matchParentSize().background(accent))
            },
            error = {
                Box(modifier = Modifier.matchParentSize().background(accent))
            },
        )
        if (loop.publisherAvatarUrl != null) {
            KornerzDiamondImage(
                imageUrl = loop.publisherAvatarUrl,
                fallbackAsset = null,
                size = 32.dp,
                cornerRadius = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun StatsRow(loop: Loop) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatChip(icon = Icons.Filled.RemoveRedEye, count = loop.viewCounts, tint = muted)
        Spacer(Modifier.padding(start = 25.dp))
        StatChip(icon = Icons.Filled.ChatBubble, count = loop.commentsCount, tint = muted)
        Spacer(Modifier.padding(start = 25.dp))
        StatChip(icon = Icons.Filled.Favorite, count = loop.likesCount, tint = muted)
    }
}

@Composable
private fun StatChip(icon: ImageVector, count: Int?, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.padding(start = 4.dp))
        Text(
            text = (count ?: 0).toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = tint,
        )
    }
}

@Composable
fun LoopOfDayShimmerSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Loop of the day",
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        HomeShimmerPlaceholder(modifier = Modifier.padding(Spacing.space8)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
fun LoopOfDayRetrySection(onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Loop of the day",
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        KornerzErrorState(message = "Loop of the day failed to load", onRetry = onRetry)
    }
}

@Preview(showBackground = true)
@Composable
private fun LoopOfDayPreview() {
    KornerzTheme(darkTheme = false) {
        LoopOfDayContent(
            loop = Loop(
                id = "l1",
                title = "How engines work",
                thumbnailUrl = null,
                color = "#5D47ED",
                createdAt = Instant.parse("2025-09-01T00:00:00Z"),
                publisherName = "Ada Lovelace",
                publisherAvatarUrl = null,
                description = "A short description that wraps to two lines of text under the title.",
                communityId = "design",
                communityName = "Design Kornerz",
                theme = "Tech",
                viewCounts = 124,
                commentsCount = 7,
                likesCount = 22,
            ),
            onClick = {},
        )
    }
}
