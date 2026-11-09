package com.kornerz.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * Image clipped into a triangle with rounded inner corners. 1:1 port of
 * iOS `kornerz/Kornerz/SwiftUI/Components/TriangleImageView.swift`
 * together with `TriangleClippedShape.swift`.
 *
 * The clip is delegated to the shared [TriangleClippedShape] so the
 * loops module does not grow a second clipping implementation.
 *
 * When [imageUrl] is `null`, blank, or fails to load, the component
 * falls back to [fallbackAsset] — a `R.drawable.*` resource resolved by
 * name at runtime, e.g. `"ic_user_placeholder"` — and to the Material
 * person glyph when that asset cannot be resolved. This mirrors
 * `KornerzDiamondImage`'s fallback chain, so an account without a
 * thumbnail never renders as an empty shape (iOS shows the
 * `userplaceholder` artwork in the same situation).
 */
@Composable
fun KornerzTriangleImage(
    imageUrl: String?,
    size: Dp,
    cornerRadius: Dp = 8.dp,
    corner: TriangleCorner = TriangleCorner.TopRight,
    fallbackAsset: String? = null,
    modifier: Modifier = Modifier,
) {
    val fallbackPainter = rememberFallbackPainter(fallbackAsset)
    val url = imageUrl?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .size(size)
            .clip(TriangleClippedShape(corner = corner, cornerRadius = cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            if (fallbackPainter != null) {
                Image(
                    painter = fallbackPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size / 2),
                )
            }
        } else {
            // `AsyncImage` over `SubcomposeAsyncImage`: the painter-based
            // loader does not subcompose on every state change, which
            // keeps the avatar cheap inside the scrolling feed.
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = fallbackPainter,
            )
        }
    }
}

/**
 * Resolves a drawable by name into a [Painter].
 *
 * The name is looked up once per [fallbackAsset] value and the painter
 * is remembered by Compose, so the resource lookup does not repeat on
 * every recomposition. A name that resolves to nothing (`0`) yields
 * `null`, which keeps the caller on its own fallback.
 */
@Composable
private fun rememberFallbackPainter(fallbackAsset: String?): Painter? {
    val context = LocalContext.current
    val drawableId = remember(fallbackAsset, context) {
        fallbackAsset
            ?.let { context.resources.getIdentifier(it, "drawable", context.packageName) }
            ?.takeIf { it != 0 }
    }
    return drawableId?.let { painterResource(it) }
}

@Preview(showBackground = true, name = "TriangleImage — light")
@Composable
private fun KornerzTriangleImageLightPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzTriangleImage(
            imageUrl = null,
            size = 55.dp,
            cornerRadius = 8.dp,
            fallbackAsset = "ic_user_placeholder",
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
            fallbackAsset = "ic_user_placeholder",
        )
    }
}
