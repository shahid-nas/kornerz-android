package com.kornerz.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.model.Community
import com.kornerz.core.ui.components.KornerzSquareTile as CoreKornerzSquareTile
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * The home carousel's community tile. 1:1 port of iOS
 * `SwiftUI/Common/KornerzSquareView.swift`: an accent-coloured rounded
 * card with the cover inset 5dp inside it and clipped by the top-right
 * triangle, and the community name on the accent-coloured half at the
 * bottom-leading edge. The implementation now lives in `core/ui`
 * ([CoreKornerzSquareTile]) so the Home tab, the Kornerz tab and the
 * signup "Pick your Kornerz" step all render the very same tile.
 */
@Composable
fun KornerzSquareTile(
    community: Community,
    index: Int,
    onClick: (Community) -> Unit,
    modifier: Modifier = Modifier,
) {
    CoreKornerzSquareTile(
        community = community,
        index = index,
        onClick = onClick,
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
