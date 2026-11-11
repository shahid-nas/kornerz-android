package com.kornerz.feature.home.recent_loops

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.Loop
import com.kornerz.core.ui.R
import com.kornerz.core.ui.components.HomeSectionHeader
import com.kornerz.core.ui.components.HomeShimmerPlaceholder
import com.kornerz.core.ui.components.KornerzErrorState
import com.kornerz.core.ui.theme.KornerzCardPalette
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.home.loop.LoopItem
import kotlinx.datetime.Instant

/**
 * Vertical list of recent loops ("Recent at Kornerz"). 1:1 port of
 * iOS `RecentLoopsView.swift:42-54`, which wraps a `LazyVStack(spacing:
 * 16)` of `LoopItemView`s under the section header. The per-row accent
 * color cycles through `loopAccentColor(index)` matching iOS's
 * `colorService.color(for: index)`.
 *
 * The row's accent colour travels with the tap — the detail screen
 * inherits it, so the loop keeps one colour across the feed and the
 * detail (iOS hands the row's colour to the detail screen).
 */
@Composable
fun RecentLoopsContent(
    loops: List<Loop>,
    onClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Recent at Kornerz",
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            loops.forEachIndexed { index, loop ->
                LoopItem(
                    loop = loop,
                    accentIndex = index,
                    onClick = { id -> onClick(id, KornerzCardPalette.hexForIndex(index)) },
                )
            }
        }
    }
}

@Composable
fun RecentLoopsShimmerSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Recent at Kornerz",
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        HomeShimmerPlaceholder(modifier = Modifier.padding(Spacing.space8)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
fun RecentLoopsRetrySection(onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Recent at Kornerz",
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        KornerzErrorState(message = "Recent at Kornerz failed to load", onRetry = onRetry)
    }
}

@Preview(showBackground = true)
@Composable
private fun RecentLoopsPreview() {
    KornerzTheme(darkTheme = false) {
        RecentLoopsContent(
            loops = listOf(
                Loop(
                    id = "l1",
                    title = "How engines work",
                    createdAt = Instant.parse("2025-09-01T00:00:00Z"),
                    description = "Short summary of how a 4-stroke engine cycles.",
                    communityName = "Engineering",
                    viewCounts = 124,
                    commentsCount = 7,
                    likesCount = 22,
                ),
                Loop(
                    id = "l2",
                    title = "Sketching for non-designers",
                    createdAt = Instant.parse("2025-10-01T00:00:00Z"),
                    communityName = "Design",
                    viewCounts = 42,
                    commentsCount = 1,
                    likesCount = 4,
                ),
            ),
            onClick = { _, _ -> },
        )
    }
}
