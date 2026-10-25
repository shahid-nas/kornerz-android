package com.kornerz.feature.home.your_kornerz

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.model.Community
import com.kornerz.core.ui.components.HomeSectionHeader
import com.kornerz.core.ui.components.HomeShimmerPlaceholder
import com.kornerz.core.ui.components.KornerzErrorState
import com.kornerz.core.ui.components.KornerzSquareTileDefaults
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.home.components.KornerzCarousel

/**
 * Your-Kornerz carousel. 1:1 port of iOS
 * `YourKornerzSectionView.swift`. Wraps the shared [KornerzCarousel]
 * with a section title.
 */
@Composable
fun YourKornerzContent(
    items: List<Community>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Your Kornerz",
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        KornerzCarousel(items = items, onItemClick = { onItemClick(it.id) })
    }
}

@Composable
fun YourKornerzShimmerSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Your Kornerz",
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        HomeShimmerPlaceholder(modifier = Modifier.padding(Spacing.space8)) {
            Spacer(modifier = Modifier.height(KornerzSquareTileDefaults.TileSize))
        }
    }
}

@Composable
fun YourKornerzRetrySection(onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Your Kornerz",
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        KornerzErrorState(message = "Your Kornerz failed to load", onRetry = onRetry)
    }
}

@Preview(showBackground = true)
@Composable
private fun YourKornerzPreview() {
    KornerzTheme(darkTheme = false) {
        YourKornerzContent(
            items = listOf(
                Community(id = "c1", name = "Design", themeColor = "#E27D5A"),
                Community(id = "c2", name = "Engineering", themeColor = ""),
            ),
            onItemClick = {},
        )
    }
}
