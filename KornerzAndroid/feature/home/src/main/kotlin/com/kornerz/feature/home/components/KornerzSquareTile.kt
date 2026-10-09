package com.kornerz.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.kornerz.core.model.Community
import com.kornerz.core.ui.components.TriangleClippedShape
import com.kornerz.core.ui.components.TriangleCorner
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.core.ui.theme.kornerzCardColor

/**
 * 135dp × 135dp square community tile used by the Your-Kornerz
 * carousel. Outer corners are 16dp, the top-right corner is clipped via
 * a rounded triangle (mirrors iOS `KornerzSquareView` /
 * `TriangleClippedShape.swift` at `cornerRadius = 13dp`).
 *
 * The cover image is `ContentScale.Crop`; the community name sits at
 * the bottom-leading edge in Satoshi Bold 12sp white with 8dp inset;
 * when [Community.themeColor] is `null` we rotate through the
 * [kornerzCardColor] palette (architecture §11.2.5).
 */
@Composable
fun KornerzSquareTile(
    community: Community,
    index: Int,
    onClick: (Community) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = parseHexColor(community.themeColor)
        ?: kornerzCardColor(index)
    Box(
        modifier = modifier
            .size(135.dp)
            .clip(TriangleClippedShape(TriangleCorner.TopRight, cornerRadius = 13.dp))
            .background(accent)
            .clickable { onClick(community) },
    ) {
        SubcomposeAsyncImage(
            model = community.coverUrl,
            contentDescription = community.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = community.name,
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

private fun parseHexColor(hex: String?): Color? {
    val cleaned = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
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
            community = Community(id = "c1", name = "Design", themeColor = "", coverUrl = null),
            index = 0,
            onClick = {},
        )
    }
}
