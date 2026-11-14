package com.kornerz.feature.loops.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.RemoveRedEye
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.kornerz.core.ui.components.KornerzTriangleImage
import com.kornerz.core.ui.components.TriangleCorner
import com.kornerz.core.ui.components.timeAgoSinceDate
import com.kornerz.core.ui.theme.KornerzTheme
import kotlinx.datetime.Instant

/**
 * Shared loop item row used by the Loops tab list, the Korner detail
 * "Recently in &lt;community&gt;" section, and the Home tab's
 * "Loop of the day" / "Recent at Kornerz" sections (one-way
 * `feature/home` → `feature/loops` dependency per design.md D9).
 *
 * 1:1 port of iOS `kornerz/Kornerz/SwiftUI/Dashboard/Loops/LoopsTab/
 * LoopItemView.swift`, which is the single loop row every iOS surface
 * renders.
 *
 * Renders:
 *   - 100×100dp thumbnail (Coil-loaded with shimmer placeholder, tinted
 *     [color] background when the URL is null).
 *   - 55dp `KornerzTriangleImage` overlay at the bottom-leading corner
 *     of the thumbnail (4dp padding inside) — iOS draws the owner's
 *     avatar as a bottom-left triangle via `TriangleImageView`, whose
 *     default corner is `.bottomLeft`.
 *   - `Column` on the trailing edge with title, community / time-ago
 *     row, description, and a 3-stat row.
 */
@Composable
fun LoopItemView(
    data: LoopCardData,
    color: Color,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(data.id) },
        verticalAlignment = Alignment.Top,
    ) {
        LoopThumbnail(data = data, accent = color, modifier = Modifier.size(100.dp))
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = data.title.ifEmpty { "Untitled loop" },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            data.communityName?.takeIf { it.isNotBlank() }?.let { community ->
                Spacer(Modifier.padding(top = 2.dp))
                Text(
                    text = "in $community · ${timeAgoSinceDate(data.creationDate)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } ?: Text(
                text = timeAgoSinceDate(data.creationDate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            data.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(Modifier.padding(top = 2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
            StatsRow(viewCounts = data.viewCounts, commentsCount = data.commentsCount, likesCount = data.likesCount)
        }
    }
}

@Composable
private fun LoopThumbnail(
    data: LoopCardData,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent),
    ) {
        if (data.thumbnailUrl != null) {
            SubcomposeAsyncImage(
                model = data.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                loading = { Box(modifier = Modifier.matchParentSize().background(accent)) },
                error = { Box(modifier = Modifier.matchParentSize().background(accent)) },
            )
        }
        if (data.isVideo) {
            Icon(
                imageVector = Icons.Filled.PlayCircleFilled,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }
        KornerzTriangleImage(
            imageUrl = data.publisherThumbnailUrl,
            size = LOOP_OWNER_AVATAR_SIZE,
            cornerRadius = LOOP_OWNER_AVATAR_RADIUS,
            corner = TriangleCorner.BottomLeft,
            fallbackAsset = "ic_user_placeholder",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp),
        )
    }
}

@Composable
private fun StatsRow(
    viewCounts: Int,
    commentsCount: Int,
    likesCount: Int,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatChip(Icons.Filled.RemoveRedEye, viewCounts, muted)
        Spacer(Modifier.padding(start = 25.dp))
        StatChip(Icons.Filled.ChatBubble, commentsCount, muted)
        Spacer(Modifier.padding(start = 25.dp))
        StatChip(Icons.Filled.Favorite, likesCount, muted)
    }
}

@Composable
private fun StatChip(icon: ImageVector, count: Int, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.padding(start = 4.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = tint,
        )
    }
}

/**
 * The owner avatar's frame size — iOS `LoopItemView.Constants.userImageSize`.
 */
private val LOOP_OWNER_AVATAR_SIZE = 55.dp

/**
 * The owner avatar's corner radius — iOS `LoopItemView.Constants.cornerRadius`.
 */
private val LOOP_OWNER_AVATAR_RADIUS = 12.dp

@Preview(showBackground = true, locale = "en")
@Composable
private fun LoopItemViewPreview() {
    KornerzTheme(darkTheme = false) {
        LoopItemView(
            data = LoopCardData(
                id = "l1",
                title = "How engines work",
                description = "A short description that wraps to two lines of text under the title.",
                descriptionHtml = null,
                communityId = "design",
                communityName = "Engineering",
                publisherId = "u1",
                publisherNickname = "Ada",
                publisherThumbnailUrl = null,
                theme = null,
                thumbnailUrl = null,
                imageUrl = null,
                videoUrl = "",
                isVideo = false,
                viewCounts = 124,
                commentsCount = 7,
                likesCount = 22,
                creationDate = Instant.parse("2025-09-01T00:00:00Z"),
            ),
            color = Color(0xFF5D47ED),
            onClick = {},
        )
    }
}
