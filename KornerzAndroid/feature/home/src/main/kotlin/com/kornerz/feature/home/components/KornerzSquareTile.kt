package com.kornerz.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.model.Community
import com.kornerz.core.ui.components.KornerzSquareImageTile
import com.kornerz.core.ui.theme.KornerzCardPalette
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.kornerzCardColor

/**
 * The home carousel's community tile. 1:1 port of iOS
 * `SwiftUI/Common/KornerzSquareView.swift`: an accent-coloured rounded
 * card with the cover inset 5dp inside it and clipped by the top-right
 * triangle, and the community name on the accent-coloured half at the
 * bottom-leading edge. See [KornerzSquareImageTile] for the full
 * breakdown of the iOS composition.
 *
 * The accent colour is the community's own theme colour, falling back to
 * the rotating [kornerzCardColor] palette (iOS
 * `RandomColorService.color(for:)`).
 */
@Composable
fun KornerzSquareTile(
    community: Community,
    index: Int,
    onClick: (Community) -> Unit,
    modifier: Modifier = Modifier,
) {
    KornerzSquareImageTile(
        name = community.name,
        coverUrl = community.coverImageURL,
        accent = KornerzCardPalette.colorFromHex(community.themeColor)
            ?: kornerzCardColor(index),
        onClick = { onClick(community) },
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun KornerzSquareTilePreview() {
    KornerzTheme(darkTheme = false) {
        KornerzSquareTile(
            community = Community(id = "c1", name = "Design", themeColor = "", coverUrl = null),
            index = 0,
            onClick = {},
        )
    }
}
