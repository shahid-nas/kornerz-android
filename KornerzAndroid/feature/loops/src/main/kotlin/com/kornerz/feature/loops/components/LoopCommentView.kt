package com.kornerz.feature.loops.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.LoopComment
import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.model.User
import com.kornerz.core.ui.components.KornerzNetworkAvatar
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import kotlinx.datetime.Instant

/**
 * Single comment row used by the loop detail screen and the reply
 * thread. Renders the avatar, the comment body, and the reply / like /
 * delete affordances.
 */
@Composable
fun LoopCommentView(
    info: LoopCommentInfo,
    color: Color,
    canDelete: Boolean = false,
    onReply: (LoopCommentInfo) -> Unit = {},
    onLike: (LoopCommentInfo) -> Unit = {},
    onDelete: (LoopCommentInfo) -> Unit = {},
    replyEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space8, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        KornerzNetworkAvatar(
            imageUrl = info.user?.photoUrl,
            size = 40.dp,
        )
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.user?.displayName ?: "User",
                style = MaterialTheme.typography.titleSmall,
                color = color,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = info.comment.comment.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimeAgo(info.comment.creationDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                Icon(
                    imageVector = if (info.isLikedByUser) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (info.isLikedByUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = info.likesCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (replyEnabled) {
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "Reply",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onReply(info) },
                    )
                }
            }
        }
        if (canDelete) {
            IconButton(onClick = { onDelete(info) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatTimeAgo(instant: Instant?): String {
    if (instant == null) return ""
    val now = kotlinx.datetime.Clock.System.now()
    val seconds = (now - instant).inWholeSeconds
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3600}h"
        seconds < 604_800 -> "${seconds / 86_400}d"
        else -> "${seconds / 604_800}w"
    }
}

@Preview(showBackground = true, locale = "en")
@Composable
private fun LoopCommentViewPreview() {
    KornerzTheme(darkTheme = false) {
        LoopCommentView(
            info = LoopCommentInfo(
                comment = LoopComment(comment = "Hello world!", creationDate = Instant.parse("2025-01-01T00:00:00Z")),
                user = User(id = "u1", email = "u@x.com", displayName = "Ada", nickname = "ada"),
                isLikedByUser = false,
                likesCount = 4,
            ),
            color = Color(0xFF5D47ED),
        )
    }
}
