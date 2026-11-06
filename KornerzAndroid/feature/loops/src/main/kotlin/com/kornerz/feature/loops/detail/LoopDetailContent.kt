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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Community
import com.kornerz.core.model.Loop
import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.model.LoopDetailInfo
import com.kornerz.core.model.User
import com.kornerz.core.ui.components.DescriptionContent
import com.kornerz.core.ui.components.HomeShimmerPlaceholder
import com.kornerz.core.ui.components.KornerzErrorState
import com.kornerz.core.ui.components.KornerzExpandableText
import com.kornerz.core.ui.components.KornerzHtmlText
import com.kornerz.core.ui.components.KornerzTriangleImage
import com.kornerz.core.ui.components.TriangleCorner
import com.kornerz.core.ui.components.resolveDescriptionContent
import com.kornerz.core.ui.components.timeAgoSinceDate
import com.kornerz.core.ui.theme.KornerzCardPalette
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.loops.components.LoopCommentView

/**
 * Stateless renderer for the loop detail screen (architecture §2.1).
 *
 * The layout is a 1:1 port of iOS `LoopDetailView.swift`: the media
 * area, the title row, the description, the stats row, the comments
 * header, the comment list and the empty-comments state — in that
 * order, driven entirely by [LoopDetailState] with no business logic
 * in the composable.
 *
 * The **back affordance** floats over the media area — iOS makes the
 * navigation bar transparent and hangs a leading control over the
 * media (`LoopDetailController.setupCustomBackButton`), so the button
 * is painted on top of the content rather than in a top bar that would
 * push the media down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopDetailContent(
    state: LoopDetailState,
    onEvent: (LoopDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = KornerzCardPalette.colorFromHex(state.resolvedAccentColorHex)
        ?: Color(0xFFE27D5A)

    Box(modifier = modifier.fillMaxSize()) {
        when (val detail = state.detail) {
            is AsyncValue.Loading -> ShimmerDetail(accent = accent)
            is AsyncValue.Error -> KornerzErrorState(
                message = "Failed to load loop",
                onRetry = { onEvent(LoopDetailEvent.Retry) },
            )
            is AsyncValue.Success -> DetailSuccess(
                detail = detail.data,
                state = state,
                accent = accent,
                onEvent = onEvent,
            )
        }
        LoopDetailBackButton(
            onBack = { onEvent(LoopDetailEvent.BackTapped) },
            modifier = Modifier
                .align(Alignment.TopStart)
                // The media runs under the status bar (iOS parity), so the
                // floating control keeps itself clear of it — the
                // Android counterpart of the transparent navigation bar,
                // whose content sits below the status bar.
                .statusBarsPadding()
                .padding(vertical = Spacing.space8),
        )
    }
}

/**
 * The back affordance — the Android counterpart of iOS
 * `LoopDetailController.setupCustomBackButton`.
 *
 * iOS draws a 30 pt leading control over the media area. Android
 * keeps the platform chevron every other back affordance in the app
 * uses (`ReplyContent`, `NotificationsScreen`, `CommunityDetailScreen`)
 * and paints it on a translucent disc, so it stays legible over both
 * the accent-coloured placeholder and an arbitrary photo.
 */
@Composable
private fun LoopDetailBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onBack,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier
                .size(36.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                .padding(8.dp),
        )
    }
}

/**
 * The loading state — iOS `.redacted(reason: .placeholder)`.
 *
 * Only the media, the title and the stats sections are redacted; the
 * description, the comments header and the comment list are **not**
 * rendered at all while the loop is loading.
 */
@Composable
private fun ShimmerDetail(accent: Color) {
    HomeShimmerPlaceholder {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(accent.copy(alpha = 0.4f)),
            )
            Column(
                modifier = Modifier.padding(Spacing.space8),
                verticalArrangement = Arrangement.spacedBy(Spacing.space4),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(20.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                HorizontalDivider()
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailSuccess(
    detail: LoopDetailInfo,
    state: LoopDetailState,
    accent: Color,
    onEvent: (LoopDetailEvent) -> Unit,
) {
    val loop = detail.loop
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onEvent(LoopDetailEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.space16),
        ) {
            item {
                MediaSection(
                    loop = loop,
                    owner = detail.owner,
                    accent = accent,
                    onEvent = onEvent,
                )
            }
            item {
                TitleRow(
                    loop = loop,
                    owner = detail.owner,
                    community = detail.community,
                    accent = accent,
                    isLikedByUser = state.isLikedByUser,
                    isLikeInProgress = state.isLikeInProgress,
                    onEvent = onEvent,
                )
            }
            item {
                DescriptionSection(
                    loop = loop,
                    accent = accent,
                )
            }
            item {
                StatsRow(
                    viewCounts = loop.viewCounts ?: 0,
                    commentsCount = state.commentsCount,
                    likesCount = state.likesCount,
                    isLikedByUser = state.isLikedByUser,
                )
            }
            item { CommentsHeader() }
            when (val comments = state.comments) {
                is AsyncValue.Loading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.space8),
                    ) {
                        Text(
                            text = "Loading comments…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is AsyncValue.Error -> item {
                    KornerzErrorState(
                        message = "Failed to load comments",
                        onRetry = { onEvent(LoopDetailEvent.Retry) },
                    )
                }
                is AsyncValue.Success -> {
                    if (comments.data.isEmpty()) {
                        item { EmptyCommentsState() }
                    } else {
                        items(comments.data, key = { it.comment.id ?: it.hashCode().toString() }) { info ->
                            LoopCommentView(
                                info = info,
                                accent = accent,
                                canDelete = state.canDelete(info),
                                onReply = {
                                    onEvent(LoopDetailEvent.CommentReplyTapped(it.comment.id.orEmpty()))
                                },
                                onLike = { onEvent(LoopDetailEvent.CommentLikeToggled(it)) },
                                onDelete = {
                                    onEvent(LoopDetailEvent.CommentDeleteConfirmed(it.comment.id.orEmpty()))
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * The media area — 250dp, full-bleed, painted with the per-loop accent
 * colour so it doubles as the placeholder.
 *
 * A video loop renders its **poster** with a centred 70dp
 * `play.circle.fill` badge; the player itself lives in a modal
 * (see `LoopDetailVideoDialog`), never inline.
 */
@Composable
private fun MediaSection(
    loop: Loop,
    owner: User?,
    accent: Color,
    onEvent: (LoopDetailEvent) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(accent)
            .clickable { onEvent(LoopDetailEvent.MediaTapped(loop.id.orEmpty())) },
    ) {
        val poster = if (loop.isVideo) {
            loop.iOsThumbnailUrl
        } else {
            loop.iOsImageUrl ?: loop.iOsThumbnailUrl
        }
        if (poster != null) {
            SubcomposeAsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (loop.isVideo) {
            Icon(
                imageVector = Icons.Filled.PlayCircleFilled,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(70.dp),
            )
        }
        owner?.let { resolved ->
            KornerzTriangleImage(
                imageUrl = resolved.thumbnailURL ?: resolved.photoUrl,
                size = 55.dp,
                cornerRadius = 8.dp,
                corner = TriangleCorner.BottomLeft,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            )
        }
    }
}

/**
 * The title row — the title, the publisher / "in" / community /
 * timestamp meta row and the 36dp like toggle.
 *
 * The like **count** is deliberately absent: it lives in the stats
 * row, matching iOS.
 */
@Composable
private fun TitleRow(
    loop: Loop,
    owner: User?,
    community: Community?,
    accent: Color,
    isLikedByUser: Boolean,
    isLikeInProgress: Boolean,
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
                text = loop.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                owner?.let { resolved ->
                    Text(
                        text = resolved.nickname.ifEmpty { resolved.displayName },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                community?.let { resolved ->
                    Text(
                        text = "in",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = resolved.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        ),
                        color = accent,
                        modifier = Modifier.clickable {
                            onEvent(LoopDetailEvent.KornerTapped(resolved.id))
                        },
                    )
                }
                val createdAt = loop.creationDate ?: loop.createdAt
                if (createdAt != null) {
                    Text(
                        text = timeAgoSinceDate(createdAt),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        IconButton(
            onClick = { onEvent(LoopDetailEvent.LikeToggled) },
            enabled = !isLikeInProgress,
            modifier = Modifier.size(36.dp),
        ) {
            if (isLikeInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = if (isLikedByUser) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Filled.FavoriteBorder
                    },
                    contentDescription = if (isLikedByUser) "Unlike" else "Like",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * Number of description lines shown before the user expands it.
 */
private const val DESCRIPTION_LINE_LIMIT = 3

/**
 * The description — the HTML branch when the loop carries HTML, the
 * expandable plain-text branch otherwise, and nothing at all when the
 * loop has no description.
 *
 * The branch selection mirrors iOS `LoopDetailView.descriptionView`:
 * HTML when available, plain text otherwise.
 */
@Composable
private fun DescriptionSection(loop: Loop, accent: Color) {
    val content = resolveDescriptionContent(loop.description, loop.descriptionHtml) ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
    ) {
        when (content) {
            is DescriptionContent.Html -> KornerzHtmlText(
                html = content.html,
                accentColor = accent,
                collapsedLineLimit = DESCRIPTION_LINE_LIMIT,
            )

            is DescriptionContent.Plain -> KornerzExpandableText(
                text = content.text,
                accentColor = accent,
                lineLimit = DESCRIPTION_LINE_LIMIT,
            )
        }
    }
}

/**
 * The stats row — edge-to-edge between two dividers, with 16dp icons
 * and 25dp gaps.
 */
@Composable
private fun StatsRow(
    viewCounts: Int,
    commentsCount: Int,
    likesCount: Int,
    isLikedByUser: Boolean,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(25.dp),
        ) {
            Stat(Icons.Filled.RemoveRedEye, viewCounts, muted)
            Stat(Icons.AutoMirrored.Filled.Message, commentsCount, muted)
            Stat(
                icon = if (isLikedByUser) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                count = likesCount,
                tint = muted,
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun Stat(icon: ImageVector, count: Int, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = count.toString(),
            color = tint,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        )
    }
}

/** The "Comments" section header. */
@Composable
private fun CommentsHeader() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Comments",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.space8),
        )
        HorizontalDivider()
    }
}

/** The empty-comments state — iOS `no_comment_message`. */
@Composable
private fun EmptyCommentsState() {
    Text(
        text = "No comments yet. Be the first!",
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.space8),
    )
}

@Preview(showBackground = true, name = "Loop detail — loaded")
@Composable
private fun LoopDetailContentLoadedPreview() {
    KornerzTheme(darkTheme = false) {
        LoopDetailContent(
            state = previewState(),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, name = "Loop detail — empty comments")
@Composable
private fun LoopDetailContentEmptyCommentsPreview() {
    KornerzTheme(darkTheme = true) {
        LoopDetailContent(
            state = previewState(comments = AsyncValue.Success(emptyList())),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, name = "Loop detail — shimmer")
@Composable
private fun LoopDetailContentShimmerPreview() {
    KornerzTheme(darkTheme = false) {
        LoopDetailContent(
            state = LoopDetailState(),
            onEvent = {},
        )
    }
}

private fun previewState(
    comments: AsyncValue<List<LoopCommentInfo>> = AsyncValue.Success(emptyList()),
): LoopDetailState = LoopDetailState(
    detail = AsyncValue.Success(
        LoopDetailInfo(
            loop = Loop(
                id = "l1",
                title = "How engines work",
                description = "A short description that wraps to two lines of text under the title.",
                viewCounts = 124,
                commentsCount = 7,
                likesCount = 22,
            ),
            owner = User(id = "u1", email = "u@x.com", displayName = "Ana", nickname = "ana"),
            community = Community(id = "c1", communityId = "c1", name = "Brazil Travel"),
        ),
    ),
    comments = comments,
    likesCount = 22,
    commentsCount = 7,
)
