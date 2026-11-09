package com.kornerz.feature.community.admin.createforum

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kornerz.core.ui.components.KornerzExpandableText
import com.kornerz.core.ui.components.KornerzHtmlText
import com.kornerz.core.ui.components.KornerzTriangleImage
import com.kornerz.core.ui.components.TriangleCorner
import com.kornerz.core.ui.theme.Satoshi
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.community.R

/**
 * Step 5 of the create-forum wizard — the publish preview.
 *
 * 1:1 port of iOS `AddForumPublishScreen`
 * (`kornerz/Kornerz/HomeVC/AddForum/AddForumPublishScreen.swift`).
 *
 * The iOS screen is a `List` previewing the loop as it will appear in
 * the feed:
 *
 * - a 250 dp media block, clipped edge-to-edge, with a 70 dp
 *   `play.circle.fill` overlay when a video is attached and the
 *   publisher's triangle avatar in the bottom-leading corner;
 * - the title at 20 sp, followed by a meta row reading
 *   `{nickname} in {community} · {time ago}` with the community name
 *   in blue;
 * - the description, rendered as HTML when the rich-text editor
 *   produced markup and as plain expandable text otherwise, collapsed
 *   to two lines;
 * - a stats row — eye, comment and heart icons, all counting zero —
 *   between two dividers.
 *
 * The publish action is a full-width capsule button pinned to the
 * bottom of the screen, and the whole screen dims behind an upload
 * progress card while the media uploads.
 */
@Composable
fun CreateForumPublishStep(
    title: String,
    description: String,
    descriptionHtml: String?,
    imageUri: Uri?,
    hasVideo: Boolean,
    publisherNickname: String,
    publisherAvatarUrl: String?,
    communityName: String,
    onPlayVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surface),
    ) {
        PublishMediaBlock(
            imageUri = imageUri,
            hasVideo = hasVideo,
            publisherAvatarUrl = publisherAvatarUrl,
            onPlayVideo = onPlayVideo,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
        )

        PublishTitleRow(
            title = title,
            publisherNickname = publisherNickname,
            communityName = communityName,
        )

        PublishDescriptionRow(
            description = description,
            descriptionHtml = descriptionHtml,
        )

        PublishStatsRow()
    }
}

/**
 * The media block at the top of the preview.
 *
 * iOS renders the selected image with `.scaledToFill()` inside a
 * 250 dp-tall, full-width frame that is `.clipped()` to its bounds,
 * overlays a 70 dp `play.circle.fill` when a video is attached, and
 * pins the publisher's triangle avatar to the bottom-leading corner
 * (`AddForumPublishScreen.swift:159-190`).
 *
 * That avatar is a **bottom-left** triangle: iOS's `TriangleImageView`
 * defaults to `type: .bottomLeft`
 * (`kornerz/Kornerz/SwiftUI/Components/TriangleImageView.swift:16`),
 * so the Android port asks [KornerzTriangleImage] for that corner
 * instead of the component's own `TopRight` default.
 */
@Composable
private fun PublishMediaBlock(
    imageUri: Uri?,
    hasVideo: Boolean,
    publisherAvatarUrl: String?,
    onPlayVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Gray.copy(alpha = 0.2f))
            .clickable(enabled = hasVideo, onClick = onPlayVideo),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = stringResource(R.string.create_forum_cd_cover_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (hasVideo) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = stringResource(R.string.create_forum_cd_play_video),
                tint = Color.White,
                modifier = Modifier.size(70.dp),
            )
        }
        KornerzTriangleImage(
            imageUrl = publisherAvatarUrl,
            size = 55.dp,
            cornerRadius = 8.dp,
            corner = TriangleCorner.BottomLeft,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Spacing.space12),
        )
    }
}

/**
 * The title and meta row.
 *
 * iOS renders the title at 20 sp medium weight, then a 12 sp row with
 * the publisher's nickname, the word "in", the community name in blue,
 * and the relative timestamp in grey
 * (`AddForumPublishScreen.swift:95-128`).
 */
@Composable
private fun PublishTitleRow(
    title: String,
    publisherNickname: String,
    communityName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space8, vertical = Spacing.space6),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = Satoshi,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(Spacing.space2))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = publisherNickname,
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.create_forum_in),
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = communityName,
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = Color.Blue,
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.FavoriteBorder,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(36.dp),
        )
    }
}

/**
 * The description row.
 *
 * iOS renders the description's HTML in a `WebView` when the
 * rich-text editor produced markup, and falls back to an expandable
 * plain-text block otherwise, collapsed to two lines
 * (`AddForumPublishScreen.swift:130-157`).
 */
@Composable
private fun PublishDescriptionRow(
    description: String,
    descriptionHtml: String?,
    modifier: Modifier = Modifier,
) {
    val html = descriptionHtml?.trim()?.takeIf { it.isNotEmpty() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space8),
    ) {
        if (html != null) {
            KornerzHtmlText(
                html = html,
                accentColor = Color.Blue,
                collapsedLineLimit = DESCRIPTION_LINE_LIMIT,
                expandedLabel = stringResource(R.string.create_forum_view_more),
                collapsedLabel = stringResource(R.string.create_forum_view_less),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        } else if (description.isNotBlank()) {
            KornerzExpandableText(
                text = description,
                lineLimit = DESCRIPTION_LINE_LIMIT,
                accentColor = Color.Blue,
                expandedLabel = stringResource(R.string.create_forum_view_more),
                collapsedLabel = stringResource(R.string.create_forum_view_less),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.space8))
    }
}

/**
 * The stats row.
 *
 * iOS renders three zeroed counters — views, comments and likes — each
 * prefixed by an icon, between two dividers
 * (`AddForumPublishScreen.swift:208-245`).
 */
@Composable
private fun PublishStatsRow(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.space8),
            horizontalArrangement = Arrangement.spacedBy(25.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatCounter(
                icon = { tint ->
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            StatCounter(
                icon = { tint ->
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            StatCounter(
                icon = { tint ->
                    Icon(
                        imageVector = Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        HorizontalDivider()
    }
}

/**
 * One zeroed stat counter — an icon followed by the count.
 */
@Composable
private fun StatCounter(
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        icon(Color.Gray)
        Text(
            text = "0",
            fontFamily = Satoshi,
            fontSize = 12.sp,
            color = Color.Gray,
        )
    }
}

/** iOS `ExpandableText` collapses the description to two lines. */
private const val DESCRIPTION_LINE_LIMIT = 2
