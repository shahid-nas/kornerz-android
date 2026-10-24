package com.kornerz.feature.loops.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.LoopComment
import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.model.User
import com.kornerz.core.ui.components.KornerzDiamondImage
import com.kornerz.core.ui.components.KornerzExpandableText
import com.kornerz.core.ui.components.timeAgoSinceDate
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import kotlinx.datetime.Instant

/**
 * Single comment row used by the loop detail screen and the reply
 * thread. 1:1 port of iOS `LoopCommentView.swift`:
 *  - a **36dp** diamond-clipped avatar with a 16dp gap;
 *  - a header row with the nickname (Satoshi Medium 12sp) and the
 *    timestamp (Satoshi 10sp, gray);
 *  - an **expandable** body;
 *  - an action row with a 15dp `message.fill` + the reply count and a
 *    15dp `heart` / `heart.fill` + the like count, 25dp apart, the
 *    heart **red** when the comment is liked.
 *
 * Deletion is a **trailing swipe action** — destructive, `trash` icon,
 * full-swipe — and is offered **only** for the signed-in user's own
 * comments ([canDelete]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopCommentView(
    info: LoopCommentInfo,
    accent: Color,
    canDelete: Boolean = false,
    onReply: (LoopCommentInfo) -> Unit = {},
    onLike: (LoopCommentInfo) -> Unit = {},
    onDelete: (LoopCommentInfo) -> Unit = {},
    replyEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // The row never settles away on its own — the ViewModel owns the
    // comment list — so the swipe only fires the delete action.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && canDelete) {
                onDelete(info)
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { DeleteBackground() },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canDelete,
        modifier = modifier.fillMaxWidth(),
    ) {
        CommentRow(
            info = info,
            accent = accent,
            replyEnabled = replyEnabled,
            onReply = onReply,
            onLike = onLike,
        )
    }
}

/**
 * The row's content — everything except the swipe affordance.
 */
@Composable
private fun CommentRow(
    info: LoopCommentInfo,
    accent: Color,
    replyEnabled: Boolean,
    onReply: (LoopCommentInfo) -> Unit,
    onLike: (LoopCommentInfo) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.space8),
        verticalAlignment = Alignment.Top,
    ) {
        KornerzDiamondImage(
            imageUrl = info.user?.thumbnailURL ?: info.user?.photoUrl,
            size = 36.dp,
            cornerRadius = 8.dp,
        )
        Spacer(Modifier.size(Spacing.space8))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = info.user?.nickname?.ifEmpty { info.user?.displayName.orEmpty() }
                        ?.ifEmpty { "User" } ?: "User",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = timeAgoSinceDate(info.comment.creationDate),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(Spacing.space2))
            KornerzExpandableText(
                text = info.comment.comment.orEmpty(),
                accentColor = accent,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            )
            Spacer(Modifier.size(Spacing.space4))
            Row(
                horizontalArrangement = Arrangement.spacedBy(25.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (replyEnabled) {
                    ActionCount(
                        icon = Icons.AutoMirrored.Filled.Message,
                        count = info.repliesCount,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = "Replies",
                        onClick = { onReply(info) },
                    )
                }
                ActionCount(
                    icon = if (info.isLikedByUser) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    count = info.likesCount,
                    tint = if (info.isLikedByUser) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    contentDescription = "Likes",
                    onClick = { onLike(info) },
                )
            }
        }
    }
}

/**
 * One action of the row's action row — a 15dp icon plus its count.
 */
@Composable
private fun ActionCount(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(15.dp)
                .clickable(onClick = onClick),
        )
        Spacer(Modifier.size(Spacing.space2))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The destructive trailing swipe background.
 */
@Composable
private fun DeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.error),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier
                .padding(Spacing.space8)
                .size(24.dp),
        )
    }
}

@Preview(showBackground = true, locale = "en")
@Composable
private fun LoopCommentViewPreview() {
    KornerzTheme(darkTheme = false) {
        LoopCommentView(
            info = LoopCommentInfo(
                comment = LoopComment(
                    id = "c1",
                    comment = "Hello world!",
                    creationDate = Instant.parse("2025-01-01T00:00:00Z"),
                    repliesCount = 2,
                ),
                user = User(id = "u1", email = "u@x.com", displayName = "Ada", nickname = "ada"),
                isLikedByUser = true,
                likesCount = 4,
            ),
            accent = Color(0xFFE27D5A),
        )
    }
}
