package com.kornerz.feature.loops.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.Community
import com.kornerz.core.model.Loop
import com.kornerz.core.model.LoopDetailInfo
import com.kornerz.core.model.User
import com.kornerz.core.ui.components.KornerzDiamondImage
import com.kornerz.core.ui.components.KornerzTriangleImage
import com.kornerz.core.ui.components.timeAgoSinceDate
import com.kornerz.core.ui.theme.KornerzCardPalette
import com.kornerz.core.ui.theme.KornerzColors
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.loops.components.LoopCommentView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCommentContentNew(
    state: CreateCommentState,
    onEvent: (CreateCommentEvent) -> Unit,
    viewModel: CreateCommentViewModel? = null,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (state.isReply) {
                        Text(
                            text = "Reply",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onEvent(CreateCommentEvent.CancelTapped) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Black,
                        )
                    }
                },
                actions = {
                    if (viewModel?.isLoading == true || state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        val enabled = viewModel?.btnPostEnabled ?: (state.text.isNotBlank() && state.text.length <= 500)
                        TextButton(
                            onClick = {
                                if (viewModel != null) {
                                    viewModel.createComment()
                                } else {
                                    onEvent(CreateCommentEvent.SendTapped)
                                }
                            },
                            enabled = enabled,
                        ) {
                            Text(
                                text = "Post",
                                color = if (enabled) Color.Black else Color.Gray,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
                .verticalScroll(rememberScrollState()),
        ) {
            // The sheet's header is the comment being replied to when it
            // was opened in reply mode, and the loop's summary
            // otherwise — iOS `commentInfoView`.
            val parent = state.parentComment
            if (parent != null) {
                LoopCommentView(
                    info = parent,
                    accent = accentColor(state.resolvedAccentColorHex),
                    // The user is already replying to this comment.
                    replyEnabled = false,
                )
                Divider()
            } else {
                state.loopDetail?.let { detail ->
                    LoopInfoView(detail = detail, accent = accentColor(state.resolvedAccentColorHex))
                    Divider()
                }
            }

            Column(
                modifier = Modifier.padding(Spacing.space8),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UserInfoView(user = state.currentUser)

                CommentBox(
                    text = state.text,
                    onTextChanged = { onEvent(CreateCommentEvent.TextChanged(it)) },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    val count = viewModel?.characterCountInfo ?: "${500 - state.text.length}/500"
                    Text(
                        text = count,
                        fontSize = 10.sp,
                        color = Color.Gray,
                    )
                }
            }
        }
    }
}

/**
 * The loop's accent colour — the same colour the loop detail screen and
 * the reply thread use for this loop. Falls back to the palette's first
 * swatch when the hex cannot be resolved.
 */
private fun accentColor(hex: String?): Color =
    KornerzCardPalette.colorFromHex(hex) ?: Color(0xFFE27D5A)

@Composable
private fun LoopInfoView(detail: LoopDetailInfo, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.space8),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        KornerzTriangleImage(
            imageUrl = detail.owner?.thumbnailURL ?: detail.owner?.photoUrl,
            size = 36.dp,
            cornerRadius = 6.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = detail.loop.title ?: "",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                detail.owner?.let { owner ->
                    val name = owner.nickname.takeIf { it.isNotBlank() } ?: owner.displayName
                    Text(
                        text = name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                    )
                }
                detail.community?.let { community ->
                    Text(
                        text = "in",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                    )
                    Text(
                        text = community.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = accent,
                    )
                }
                detail.loop.creationDate?.let { date ->
                    Text(
                        text = timeAgoSinceDate(date),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray,
                    )
                }
            }
            Text(
                text = detail.loop.description ?: "",
                fontSize = 12.sp,
                color = Color.Black,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun UserInfoView(user: User?) {
    if (user != null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KornerzDiamondImage(
                imageUrl = user.thumbnailURL ?: user.photoUrl,
                size = 40.dp,
                cornerRadius = 10.dp,
            )
            val name = user.nickname.takeIf { it.isNotBlank() } ?: user.displayName
            Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
        }
    }
}

@Composable
private fun CommentBox(
    text: String,
    onTextChanged: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(KornerzColors.SandLight, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChanged,
            modifier = Modifier.fillMaxSize(),
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = Color(0xFF1B1A23),
            ),
        )
        if (text.isEmpty()) {
            Text(
                text = "Leave a comment",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateCommentContentNewPreview() {
    KornerzTheme {
        CreateCommentContentNew(
            state = CreateCommentState(
                text = "",
                loopDetail = LoopDetailInfo(
                    loop = Loop(
                        id = "1",
                        title = "Test Loop",
                        description = "This is a test loop description",
                        publisherId = "user1",
                        communityId = "comm1",
                    ),
                    owner = User(id = "user1", email = "test@test.com", displayName = "Test", nickname = "TestUser"),
                    community = Community(id = "comm1", name = "Test Community", code = "TC", description = null),
                ),
                currentUser = User(id = "current", email = "me@test.com", displayName = "Me", nickname = "Me"),
            ),
            onEvent = {},
        )
    }
}
