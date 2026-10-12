package com.kornerz.feature.loops.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import coil.compose.SubcomposeAsyncImage
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Loop
import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.ui.components.HomeShimmerPlaceholder
import com.kornerz.core.ui.components.KornerzEmptyState
import com.kornerz.core.ui.components.KornerzErrorState
import com.kornerz.core.ui.components.KornerzNetworkAvatar
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.loops.components.LoopCommentView
import com.kornerz.feature.loops.components.RandomColorService
import com.kornerz.feature.loops.video.VideoPlayerView
import kotlinx.datetime.Instant

/**
 * Stateless renderer for the loop detail screen (architecture §2.1).
 * Renders media + title row + description + stats + comments list +
 * add-comment affordance. Switches between shimmer / error / content
 * via the `AsyncValue` pattern (arch §2.2).
 */
@Composable
fun LoopDetailContent(
    state: LoopDetailState,
    onEvent: (LoopDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        when (val detail = state.detail) {
            is AsyncValue.Loading -> ShimmerDetail()
            is AsyncValue.Error -> KornerzErrorState(
                message = "Failed to load loop",
                onRetry = { onEvent(LoopDetailEvent.Retry) },
            )
            is AsyncValue.Success -> DetailSuccess(
                loop = detail.data,
                isLikedByUser = state.isLikedByUser,
                isLikeInProgress = state.isLikeInProgress,
                likesCount = state.likesCount,
                commentsCount = state.commentsCount,
                comments = state.comments,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun ShimmerDetail() {
    HomeShimmerPlaceholder {
        Column(
            modifier = Modifier.padding(Spacing.space8),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun DetailSuccess(
    loop: Loop,
    isLikedByUser: Boolean,
    isLikeInProgress: Boolean,
    likesCount: Int,
    commentsCount: Int,
    comments: AsyncValue<List<LoopCommentInfo>>,
    onEvent: (LoopDetailEvent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            MediaSection(loop = loop, onEvent = onEvent)
        }
        item {
            TitleRow(
                loop = loop,
                isLikedByUser = isLikedByUser,
                isLikeInProgress = isLikeInProgress,
                likesCount = likesCount,
                onEvent = onEvent,
            )
        }
        loop.description?.takeIf { it.isNotBlank() }?.let { description ->
            item {
                ExpandableText(text = description)
            }
        }
        item { StatsRow(viewCounts = loop.viewCounts ?: 0, commentsCount = commentsCount, likesCount = likesCount) }
        item { CommentsHeader() }
        when (val c = comments) {
            is AsyncValue.Loading -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.space8),
                ) { Text("Loading comments…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            is AsyncValue.Error -> item {
                KornerzErrorState(message = "Failed to load comments", onRetry = { onEvent(LoopDetailEvent.Retry) })
            }
            is AsyncValue.Success -> {
                if (c.data.isEmpty()) {
                    item { KornerzEmptyState(title = "No comments yet", subtitle = "Be the first to comment") }
                } else {
                    items(c.data) { info ->
                        LoopCommentView(
                            info = info,
                            color = RandomColorService.colorFor(info.comment.id?.hashCode() ?: 0),
                            onReply = { onEvent(LoopDetailEvent.CommentReplyTapped(it.comment.id.orEmpty())) },
                            onLike = { onEvent(LoopDetailEvent.CommentLikeToggled(it)) },
                            onDelete = { onEvent(LoopDetailEvent.CommentDeleteConfirmed(it.comment.id.orEmpty())) },
                        )
                    }
                }
            }
        }
        item { AddCommentRow(onClick = { onEvent(LoopDetailEvent.AddCommentTapped(loop.id.orEmpty())) }) }
    }
}

@Composable
private fun MediaSection(loop: Loop, onEvent: (LoopDetailEvent) -> Unit) {
    val loopId = loop.id.orEmpty()
    val accent = Color(0xFF5D47ED)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(accent)
            .clickable { onEvent(LoopDetailEvent.MediaTapped(loopId)) },
    ) {
        if (loop.isVideo) {
            VideoPlayerView(
                videoUrl = loop.iOsVideoUrl,
                autoPlay = false,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val image = loop.iOsImageUrl ?: loop.iOsThumbnailUrl
            if (image != null) {
                SubcomposeAsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Publisher avatar overlay (bottom-leading, 16dp padding).
        KornerzNetworkAvatar(
            imageUrl = null,
            size = 48.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        )
    }
}

@Composable
private fun TitleRow(
    loop: Loop,
    isLikedByUser: Boolean,
    isLikeInProgress: Boolean,
    likesCount: Int,
    onEvent: (LoopDetailEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = loop.title.orEmpty().ifEmpty { "Untitled loop" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.padding(top = 4.dp))
            Text(
                text = listOfNotNull(
                    loop.publisherId,
                    loop.communityId?.let { "in $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onEvent(LoopDetailEvent.LikeToggled) }) {
            if (isLikeInProgress) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else if (isLikedByUser) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Unlike",
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                )
            }
        }
        Text(
            text = likesCount.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val expanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier = modifier.padding(horizontal = Spacing.space8, vertical = 4.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded.value) Int.MAX_VALUE else 2,
        )
        if (text.length > 120) {
            androidx.compose.material3.TextButton(onClick = { expanded.value = !expanded.value }) {
                Text(if (expanded.value) "less" else "more")
            }
        }
    }
}

@Composable
private fun StatsRow(viewCounts: Int, commentsCount: Int, likesCount: Int) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space8, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(Icons.Filled.RemoveRedEye, viewCounts, muted)
        Spacer(Modifier.size(16.dp))
        Stat(Icons.Filled.ChatBubble, commentsCount, muted)
        Spacer(Modifier.size(16.dp))
        Stat(Icons.Filled.Favorite, likesCount, muted)
    }
}

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(4.dp))
        Text(text = count.toString(), color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CommentsHeader() {
    Column {
        HorizontalDivider()
        Text(
            text = "Comments",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(Spacing.space8),
        )
    }
}

@Composable
private fun AddCommentRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.space8)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KornerzNetworkAvatar(imageUrl = null, size = 32.dp)
        Spacer(Modifier.size(8.dp))
        Text(
            text = "Leave a comment",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, locale = "en")
@Composable
private fun LoopDetailContentPreview() {
    KornerzTheme(darkTheme = false) {
        LoopDetailContent(
            state = LoopDetailState(
                detail = AsyncValue.Success(
                    Loop(
                        id = "l1",
                        title = "How engines work",
                        description = "A short description that wraps to two lines of text under the title.",
                        firebaseImageUrl = null,
                        firebaseThumbnailUrl = null,
                        firebaseVideoUrl = null,
                        viewCounts = 124,
                        commentsCount = 7,
                        likesCount = 22,
                    ),
                ),
                comments = AsyncValue.Success(emptyList()),
            ),
            onEvent = {},
        )
    }
}
