package com.kornerz.feature.home.loop

import androidx.compose.foundation.background
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
import com.kornerz.feature.loops.components.LoopItemView
import com.kornerz.feature.loops.data.toCardData
import kotlinx.datetime.Instant

/**
 * The Home tab's "Loop of the day" section. 1:1 port of iOS
 * `LoopOfDaySectionView.swift:40-45`, which renders the shared
 * `LoopItemView` under the section header.
 *
 * The row itself is `feature/loops`' `LoopItemView` — the same
 * component the Loops tab, the Korner detail's "Recently in
 * &lt;community&gt;" section, and the Home "Recent at Kornerz" section
 * render, so the loop chrome is identical on every surface (iOS has a
 * single `LoopItemView` for all four).
 */
@Composable
fun LoopOfDayContent(
    loop: Loop,
    onClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Loop of the day",
            iconRes = R.drawable.ic_home_section_loop,
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        val accentHex = KornerzCardPalette.hexForIndex(0)
        LoopItemView(
            data = loop.toCardData(community = null, owner = null),
            color = KornerzCardPalette.color(0),
            onClick = { id -> onClick(id, accentHex) },
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space4),
        )
    }
}

@Composable
fun LoopOfDayShimmerSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Loop of the day",
            iconRes = R.drawable.ic_home_section_loop,
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
fun LoopOfDayRetrySection(onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Loop of the day",
            iconRes = R.drawable.ic_home_section_loop,
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        KornerzErrorState(message = "Loop of the day failed to load", onRetry = onRetry)
    }
}

@Preview(showBackground = true)
@Composable
private fun LoopOfDayPreview() {
    KornerzTheme(darkTheme = false) {
        LoopOfDayContent(
            loop = Loop(
                id = "l1",
                title = "How engines work",
                thumbnailUrl = null,
                color = "#5D47ED",
                createdAt = Instant.parse("2025-09-01T00:00:00Z"),
                publisherName = "Ada Lovelace",
                publisherAvatarUrl = null,
                description = "A short description that wraps to two lines of text under the title.",
                communityId = "design",
                communityName = "Design Kornerz",
                theme = "Tech",
                viewCounts = 124,
                commentsCount = 7,
                likesCount = 22,
            ),
            onClick = { _, _ -> },
        )
    }
}
