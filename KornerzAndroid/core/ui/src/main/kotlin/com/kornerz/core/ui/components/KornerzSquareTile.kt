package com.kornerz.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.kornerz.core.model.Korner
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.KornerzCardPalette
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.core.ui.theme.kornerzCardColor

/**
 * Layout tokens of the iOS `KornerzSquareView.Constants`.
 */
object KornerzSquareTileDefaults {
    /** iOS `Constants.size` — the side of the cover image. */
    val ImageSize: Dp = 135.dp

    /** iOS `Constants.padding` — the accent frame around the cover. */
    val CardPadding: Dp = 5.dp

    /** The rendered card: the cover plus the accent frame on each side. */
    val TileSize: Dp = ImageSize + CardPadding * 2

    /** iOS `Constants.radius` — the card's own corner radius. */
    val CardRadius: Dp = 16.dp

    /** iOS `Constants.radius - 3` — the triangle clip's corner radius. */
    val TriangleRadius: Dp = 13.dp

    /** iOS `Constants.titlePadding`. */
    val TitlePadding: Dp = 8.dp

    /** iOS `Constants.fontSize`. */
    val TitleFontSize: TextUnit = 12.sp

    /** iOS `.background(.gray)` — the surface behind the cover. */
    val Placeholder: Color = Color(0xFF808080)
}

/**
 * The Kornerz carousel tile. 1:1 port of iOS
 * `SwiftUI/Common/KornerzSquareView.swift`.
 *
 * iOS does **not** fill the tile with the accent colour and lay the
 * cover on top of it. It stacks an accent-coloured rounded card with the
 * cover inset inside that card:
 *
 * ```
 * TriangleImageView(image: .url(community.imageURL),
 *                   size: 135, radius: 13, type: .topRight)
 *     .padding(5)
 *     .background(RoundedRectangle(cornerRadius: 16).foregroundStyle(color))
 *     .overlay(alignment: .bottomLeading) { title }
 * ```
 *
 * so the rendered tile is 145 × 145: a 135 × 135 cover clipped by the
 * top-right triangle (the bottom-left half of the cover is cut away and
 * shows the accent colour), a 5dp accent frame around it, and the name
 * on the accent-coloured half at the bottom-leading edge.
 *
 * @param name the tile's title, overlaid on the bottom-leading edge.
 * @param coverUrl the cover image; `null` renders the placeholder.
 * @param accent the card's background colour.
 * @param onClick invoked when the tile is tapped.
 * @param size the side of the cover image (iOS `Constants.size`); the
 *  card itself is `size + 2 × [CardPadding]`.
 */
@Composable
fun KornerzSquareImageTile(
    name: String,
    coverUrl: String?,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = KornerzSquareTileDefaults.ImageSize,
) {
    Box(
        modifier = modifier
            .size(size + KornerzSquareTileDefaults.CardPadding * 2)
            .clip(RoundedCornerShape(KornerzSquareTileDefaults.CardRadius))
            .background(accent)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(KornerzSquareTileDefaults.CardPadding)
                .clip(
                    TriangleClippedShape(
                        corner = TriangleCorner.TopRight,
                        cornerRadius = KornerzSquareTileDefaults.TriangleRadius,
                    ),
                )
                .background(KornerzSquareTileDefaults.Placeholder),
        ) {
            if (coverUrl != null) {
                SubcomposeAsyncImage(
                    model = coverUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    },
                    error = {},
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = KornerzSquareTileDefaults.TitleFontSize,
                color = Color.White,
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(KornerzSquareTileDefaults.TitlePadding)
                .width(size * 2 / 3),
        )
    }
}

/**
 * [KornerzSquareImageTile] bound to a [Korner]. The accent colour is
 * the Korner's own theme colour, falling back to the rotating
 * [kornerzCardColor] palette (iOS `RandomColorService.color(for:)`).
 */
@Composable
fun KornerzSquareTile(
    korner: Korner,
    index: Int,
    onClick: (Korner) -> Unit,
    modifier: Modifier = Modifier,
) {
    KornerzSquareImageTile(
        name = korner.name,
        coverUrl = korner.coverUrl,
        accent = KornerzCardPalette.colorFromHex(korner.themeColor)
            ?: kornerzCardColor(index),
        onClick = { onClick(korner) },
        modifier = modifier,
    )
}

/**
 * Horizontal carousel of [KornerzSquareTile]s — the shared row used by
 * all three Kornerz tab sections. Mirrors iOS
 * `KornerzCarouselView.swift`.
 */
@Composable
fun KornerzCarousel(
    items: List<Korner>,
    onItemClick: (Korner) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.space8),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        itemsIndexed(items, key = { _, k -> k.id }) { index, korner ->
            KornerzSquareTile(
                korner = korner,
                index = index,
                onClick = onItemClick,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun KornerzSquareTilePreview() {
    KornerzTheme(darkTheme = false) {
        KornerzSquareTile(
            korner = Korner(id = "c1", name = "Design", themeColor = "#E27D5A"),
            index = 0,
            onClick = {},
        )
    }
}
