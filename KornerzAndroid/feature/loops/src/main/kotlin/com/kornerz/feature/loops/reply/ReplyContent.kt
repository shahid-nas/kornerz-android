package com.kornerz.feature.loops.reply

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.LoopComment
import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.model.User
import com.kornerz.core.ui.components.KornerzNetworkAvatar
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.loops.components.LoopCommentView
import com.kornerz.feature.loops.components.RandomColorService
import kotlinx.datetime.Instant

@Composable
fun ReplyContent(
    state: ReplyState,
    onEvent: (ReplyEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ReplyHeader(parent = state.parentComment, onBack = { onEvent(ReplyEvent.BackTapped) })
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            when (val parent = state.parentComment) {
                is AsyncValue.Loading -> item { Text("Loading…", modifier = Modifier.padding(Spacing.space8)) }
                is AsyncValue.Error -> item {
                    Text(
                        "Failed to load",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(Spacing.space8),
                    )
                }
                is AsyncValue.Success -> item {
                    LoopCommentView(
                        info = parent.data,
                        color = RandomColorService.colorFor(parent.data.comment.id?.hashCode() ?: 0),
                    )
                }
            }
            when (val replies = state.replies) {
                is AsyncValue.Loading -> item { Text("Loading…", modifier = Modifier.padding(Spacing.space8)) }
                is AsyncValue.Error -> item { Text("Failed to load replies", modifier = Modifier.padding(Spacing.space8)) }
                is AsyncValue.Success -> items(replies.data) { info ->
                    LoopCommentView(
                        info = info,
                        color = RandomColorService.colorFor(info.comment.id?.hashCode() ?: 0),
                        onLike = { onEvent(ReplyEvent.CommentLikeToggled(it)) },
                        onDelete = { onEvent(ReplyEvent.CommentDeleteConfirmed(it.comment.id.orEmpty())) },
                    )
                }
            }
        }
        ReplyInputRow(
            text = state.text,
            isSubmitting = state.isSubmitting,
            onChange = { onEvent(ReplyEvent.TextChanged(it)) },
            onSend = { onEvent(ReplyEvent.SendTapped) },
        )
    }
}

@Composable
private fun ReplyHeader(parent: AsyncValue<LoopCommentInfo>, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.space8),
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

@Composable
private fun ReplyInputRow(
    text: String,
    isSubmitting: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KornerzNetworkAvatar(imageUrl = null, size = 32.dp)
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            modifier = Modifier.weight(1f).height(56.dp),
            placeholder = { Text("Write a reply…") },
            enabled = !isSubmitting,
            singleLine = true,
        )
        Spacer(Modifier.size(4.dp))
        IconButton(onClick = onSend, enabled = text.isNotBlank() && !isSubmitting) {
            Icon(Icons.Filled.Send, contentDescription = "Send")
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
