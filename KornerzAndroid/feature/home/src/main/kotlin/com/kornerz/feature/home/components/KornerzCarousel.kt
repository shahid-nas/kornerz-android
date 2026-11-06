package com.kornerz.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.Community
import com.kornerz.core.ui.theme.KornerzCardPalette
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * Horizontal carousel of 135dp square community tiles. 1:1 port of iOS
 * `KornerzCarouselView.swift`. Used by Your-Kornerz and (in future
 * phases) the public community carousel. Modifier is last per
 * architecture §4.
 *
 * [onItemClick] hands the tapped community **and** the accent hex the
 * tile was painted with, so the caller can pass that very colour to
 * the detail screen it opens (iOS parity — `YourKornerzSectionViewModel
 * .communitySelected` hands the tile's colour to the Korner detail).
 */
@Composable
fun KornerzCarousel(
    items: List<Community>,
    onItemClick: (Community, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.space8),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        itemsIndexed(items, key = { _, c -> c.id }) { index, community ->
            KornerzSquareTile(
                community = community,
                index = index,
                onClick = {
                    onItemClick(community, KornerzCardPalette.hexForAccent(community.themeColor, index))
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun KornerzCarouselPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzCarousel(
            items = listOf(
                Community(id = "c1", name = "Design", themeColor = "#E27D5A"),
                Community(id = "c2", name = "Engineering", themeColor = ""),
            ),
            onItemClick = { _, _ -> },
        )
    }
}
