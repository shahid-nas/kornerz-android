package com.kornerz.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * Image clipped into a triangle with rounded inner corners. 1:1 port of
 * iOS `kornerz/Kornerz/SwiftUI/Components/TriangleImageView.swift`
 * together with `TriangleClippedShape.swift`.
 *
 * The clip is delegated to the shared [TriangleClippedShape] so the
 * loops module does not grow a second clipping implementation. When
 * [imageUrl] is `null` — or Coil fails to load it — a neutral
 * placeholder surface renders inside the same clipped shape, which is
 * what iOS gets from its `.background(.gray)`.
 */
@Composable
fun KornerzTriangleImage(
    imageUrl: String?,
    size: Dp,
    cornerRadius: Dp = 8.dp,
    corner: TriangleCorner = TriangleCorner.TopRight,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(TriangleClippedShape(corner = corner, cornerRadius = cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {},
                error = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "TriangleImage — light")
@Composable
private fun KornerzTriangleImageLightPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzTriangleImage(
            imageUrl = null,
            size = 55.dp,
            cornerRadius = 8.dp,
        )
    }
}

@Preview(showBackground = true, name = "TriangleImage — dark")
@Composable
private fun KornerzTriangleImageDarkPreview() {
    KornerzTheme(darkTheme = true) {
        KornerzTriangleImage(
            imageUrl = null,
            size = 55.dp,
            cornerRadius = 8.dp,
            corner = TriangleCorner.BottomLeft,
        )
    }
}
