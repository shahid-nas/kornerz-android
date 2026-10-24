package com.kornerz.feature.loops.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.components.KornerzDiamondImage
import com.kornerz.core.ui.theme.KornerzColors
import com.kornerz.core.ui.theme.Spacing

/**
 * The pinned add-comment bar — a divider, the signed-in user's 50dp
 * diamond avatar and the "Leave a comment" capsule.
 *
 * The bar lives in the screen's `Scaffold.bottomBar`, **outside** the
 * scrolling content, so it stays above the system navigation inset and
 * never scrolls away — the Android equivalent of iOS's
 * `safeAreaInset(edge: .bottom)`.
 *
 * Shared by the loop detail screen and the reply thread: iOS renders
 * the same bar in both (`LoopDetailView.swift` and `LoopReplyScreen.swift`'s
 * `addCommentView`), and in both the bar opens the create-comment sheet
 * — as a top-level comment on the detail screen, as a reply to the
 * thread's parent comment in the reply thread.
 */
@Composable
fun LoopAddCommentBar(
    avatarUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KornerzDiamondImage(
                imageUrl = avatarUrl,
                size = 40.dp,
                cornerRadius = 10.dp,
            )
            Spacer(Modifier.size(Spacing.space8))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(KornerzColors.SandLight)
                    .padding(horizontal = Spacing.space12, vertical = Spacing.space8),
            ) {
                Text(
                    text = "Leave a comment",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
