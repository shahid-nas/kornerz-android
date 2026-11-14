package com.kornerz.feature.kornerz.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.components.HomeSectionHeader
import com.kornerz.core.ui.components.KornerzLoadingMoreView
import com.kornerz.core.ui.theme.KornerzCardPalette
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.loops.components.LoopCardData
import com.kornerz.feature.loops.components.LoopItemView
import kotlinx.datetime.Instant

/**
 * The "Recently in &lt;community&gt;" section of the Korner detail
 * screen. 1:1 port of iOS `KornerDetailView.recentHeader` +
 * `recentContents` (`SwiftUI/Dashboard/Korner/KornerDetail/KornerDetailView.swift`).
 *
 * iOS renders a `HomeSectionHeader` titled "recently in {community}"
 * followed by the community's latest loops, or an empty state with a
 * tray glyph and "no loops available" when there are none.
 *
 * The rows reuse `feature/loops`' `LoopItemView`, so the chrome matches
 * the Loops tab and the Home carousel row for row — the same one-way
 * `feature/kornerz` → `feature/loops` edge the Home tab already uses.
 */
@Composable
fun KornerDetailRecentSection(
    communityName: String,
    cards: List<LoopCardData>,
    onLoopClick: (loopId: String, accentColorHex: String) -> Unit,
    isLoadingMore: Boolean = false,
    hasMore: Boolean = true,
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    LaunchedEffect(listState, cards.size, hasMore, isLoadingMore) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && lastVisible >= cards.lastIndex - LOAD_MORE_THRESHOLD
        }.collect { nearEnd ->
            if (nearEnd) currentOnLoadMore()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Recently in $communityName",
            modifier = Modifier.padding(horizontal = Spacing.space8),
        )
        Spacer(modifier = Modifier.height(Spacing.space4))
        if (cards.isEmpty()) {
            EmptyRecentLoops()
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.space8),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.space8),
            ) {
                items(items = cards, key = { it.id }) { card ->
                    val index = cards.indexOf(card)
                    val accent = KornerzCardPalette.color(index)
                    LoopItemView(
                        data = card,
                        color = accent,
                        onClick = { onLoopClick(card.id, KornerzCardPalette.hexForIndex(index)) },
                        modifier = Modifier.padding(horizontal = Spacing.space8),
                    )
                }
                if (isLoadingMore) {
                    item(key = "recent-loops-loading-more") {
                        KornerzLoadingMoreView()
                    }
                }
            }
        }
    }
}

/**
 * The "no loops available" state. iOS draws a 48-pt `tray` glyph in
 * grey above a 16-pt medium grey message, 40 pt below the header.
 */
@Composable
private fun EmptyRecentLoops() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space8)
            .padding(top = Spacing.space24),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(Spacing.space8))
        Text(
            text = "No loops available",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val LOAD_MORE_THRESHOLD = 2

@Preview(showBackground = true, name = "Korner detail recent — empty")
@Composable
private fun KornerDetailRecentSectionEmptyPreview() {
    KornerzTheme(darkTheme = false) {
        KornerDetailRecentSection(
            communityName = "Design Korner",
            cards = emptyList(),
            onLoopClick = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "Korner detail recent — with loops")
@Composable
private fun KornerDetailRecentSectionPreview() {
    KornerzTheme(darkTheme = false) {
        KornerDetailRecentSection(
            communityName = "Design Korner",
            cards = listOf(
                LoopCardData(
                    id = "l1",
                    title = "How engines work",
                    description = "A short description.",
                    descriptionHtml = null,
                    communityId = "design",
                    communityName = "Design Korner",
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
                    creationDate = Instant.parse("2026-10-20T00:00:00Z"),
                ),
            ),
            onLoopClick = { _, _ -> },
        )
    }
}
