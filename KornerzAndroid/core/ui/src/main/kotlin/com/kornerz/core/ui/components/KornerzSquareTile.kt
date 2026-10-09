package com.kornerz.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.kornerz.core.model.Korner
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.core.ui.theme.kornerzCardColor

/**
 * 135dp × 135dp square tile used by every carousel on the Kornerz tab
 * (Your / Recommended / New this week). Mirrors iOS
 * `KornerzSquareView.swift` / `TriangleClippedShape.swift`: outer
 * corners are 16dp, the top-right corner is clipped via a rounded
 * triangle at `cornerRadius = 13dp`.
 *
 * Cover image is `ContentScale.Crop`; the Korner name sits at the
 * bottom-leading edge in Satoshi Bold 12sp white with 8dp inset. When
 * [Korner.themeColor] is blank we rotate through the
 * [kornerzCardColor] palette.
 */
@Composable
fun KornerzSquareTile(
    korner: Korner,
    index: Int,
    onClick: (Korner) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = parseHexColor(korner.themeColor) ?: kornerzCardColor(index)
    Box(
        modifier = modifier
            .size(135.dp)
            .clip(TriangleClippedShape(TriangleCorner.TopRight, cornerRadius = 13.dp))
            .background(accent)
            .clickable { onClick(korner) },
    ) {
        SubcomposeAsyncImage(
            model = korner.coverUrl,
            contentDescription = korner.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = korner.name,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.White,
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Spacing.space4, bottom = Spacing.space4),
        )
    }
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

private fun parseHexColor(hex: String): Color? {
    val cleaned = hex.removePrefix("#").takeIf { it.length == 6 } ?: return null
    return try {
        Color(android.graphics.Color.parseColor("#$cleaned"))
    } catch (_: Throwable) {
        null
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
