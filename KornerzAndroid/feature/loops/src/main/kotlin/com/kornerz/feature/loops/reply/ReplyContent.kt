package com.kornerz.feature.loops.reply

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.LoopComment
import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.model.User
import com.kornerz.core.ui.theme.KornerzCardPalette
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.loops.components.LoopCommentView
import kotlinx.datetime.Instant

/**
 * The reply thread's content — 1:1 port of iOS `LoopReplyScreen.swift`.
 *
 * The **parent comment is pinned** above the list (iOS's sticky header
 * section: the comment + a divider), and the replies scroll beneath
 * it. The add-comment bar is **not** part of this content — it lives in
 * the screen's `Scaffold.bottomBar`, mirroring iOS's
 * `safeAreaInset(edge: .bottom)`.
 */
@Composable
fun ReplyContent(
    state: ReplyState,
    onEvent: (ReplyEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = KornerzCardPalette.colorFromHex(state.accentColorHex) ?: Color(0xFFE27D5A)
    Column(modifier = modifier.fillMaxSize()) {
        ReplyHeader(parent = state.parentComment, onBack = { onEvent(ReplyEvent.BackTapped) })
        when (val parent = state.parentComment) {
            is AsyncValue.Loading -> Text(
                text = "Loading…",
                modifier = Modifier.padding(Spacing.space8),
            )
            is AsyncValue.Error -> Text(
                text = "Failed to load",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(Spacing.space8),
            )
            is AsyncValue.Success -> {
                LoopCommentView(
                    info = parent.data,
                    accent = accent,
                    // The user is already replying to this comment —
                    // the reply affordance would be a no-op.
                    replyEnabled = false,
                    // The parent comment carries its own heart — the
                    // like toggle reaches it like any other row.
                    onLike = { onEvent(ReplyEvent.CommentLikeToggled(it)) },
                )
                HorizontalDivider()
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            when (val replies = state.replies) {
                is AsyncValue.Loading -> item { Text("Loading…", modifier = Modifier.padding(Spacing.space8)) }
                is AsyncValue.Error -> item {
                    Text("Failed to load replies", modifier = Modifier.padding(Spacing.space8))
                }
                is AsyncValue.Success -> {
                    if (replies.data.isEmpty()) {
                        item {
                            Text(
                                text = "No comments yet. Be the first!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(Spacing.space8),
                            )
                        }
                    } else {
                        // The list is live — a reply posted from the
                        // create-comment sheet shifts every row down —
                        // so the rows are keyed by their comment id, the
                        // same way the loop detail's list is. Without a
                        // key the swipe state would follow the row's
                        // position instead of the row.
                        items(replies.data, key = { it.comment.id ?: it.hashCode().toString() }) { info ->
                            LoopCommentView(
                                info = info,
                                accent = accent,
                                onLike = { onEvent(ReplyEvent.CommentLikeToggled(it)) },
                                onDelete = { onEvent(ReplyEvent.CommentDeleteConfirmed(it.comment.id.orEmpty())) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyHeader(parent: AsyncValue<LoopCommentInfo>, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.size(4.dp))
        Column {
            Text(text = "Reply", style = MaterialTheme.typography.titleMedium)
            if (parent is AsyncValue.Success) {
                Text(
                    text = parent.data.user?.displayName ?: "User",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, locale = "en")
@Composable
private fun ReplyContentPreview() {
    KornerzTheme(darkTheme = false) {
        ReplyContent(
            state = ReplyState(
                parentComment = AsyncValue.Success(
                    LoopCommentInfo(
                        comment = LoopComment(comment = "Parent comment", creationDate = Instant.parse("2025-01-01T00:00:00Z")),
                        user = User(id = "u1", email = "u@x.com", displayName = "Ada", nickname = "ada"),
                    ),
                ),
                replies = AsyncValue.Success(emptyList()),
            ),
            onEvent = {},
        )
    }
}
