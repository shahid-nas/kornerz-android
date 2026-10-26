package com.kornerz.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.kornerz.core.ui.theme.KornerzTheme
import kotlin.math.sqrt

/**
 * Rounded-square image rendered as a 45°-rotated diamond. 1:1 port of iOS
 * `DiamondImageView` (`kornerz/Kornerz/Components/DiamondImageView.swift`).
 *
 * iOS keeps the *image* upright and rotates only the **clip shape**
 * (`RoundedRectangle(radius).rotation(45°).scale(1/√2)`), so the diamond is
 * inscribed in the `size × size` frame and the image fills it.
 *
 * The same result is produced here by rotating + scaling one layer
 * (which turns the rounded-square clip into the diamond mask) and undoing
 * that transform exactly **once**, in a single wrapper, around all of the
 * content. The counter-transform must not be repeated per slot: Coil's
 * `loading`/`error` placeholders are children of `SubcomposeAsyncImage`, so
 * they already inherit the wrapper's transform — rotating them again is what
 * made the placeholder appear tilted by 45°.
 *
 * When [imageUrl] is `null` or Coil fails, the component falls back to
 * [fallbackAsset] (a `R.drawable.*` resource resolved by name at
 * runtime) or, if that's `null` too, the Material `Person` icon. The
 * "user" default mirrors iOS `DiamondImageView(imageURL: .custom("user"), …)`.
 */
@Composable
fun KornerzDiamondImage(
    imageUrl: String?,
    fallbackAsset: String? = null,
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    /**
     * The fill painted behind the artwork, inside the diamond.
     *
     * `null` keeps the default (the theme's `surfaceVariant`). iOS
     * `DiamondImageView` paints its background only for the *remote
     * image* case (`webImage`), while the local/system image case is
     * transparent — so a caller that mirrors iOS can pass
     * `Color.Transparent` for a placeholder.
     */
    background: Color? = null,
) {
    // Pythagoras: a `size × size` square rotated 45° has a diagonal of
    // `size * √2`. Scaling the rotated layer by `1/√2` brings the diagonal
    // back to `size`, i.e. the diamond is inscribed in the frame exactly like
    // the iOS clip shape (`sqrt(size * size / 2) / size`).
    val diamondScale = 1f / sqrt(2f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                // Rotating + scaling this layer is what turns the rounded
                // square clip below into the diamond mask.
                .rotate(45f)
                .scale(diamondScale)
                .clip(RoundedCornerShape(cornerRadius))
                .background(background ?: MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            // Undo the layer transform once, for every piece of content
            // (loaded image *and* Coil's loading/error placeholders), so the
            // artwork always stays upright.
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(1f / diamondScale)
                    .rotate(-45f),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl != null) {
                    SubcomposeAsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        // `FillBounds` (vs `Crop`) ignores the source's
                        // aspect ratio and stretches the image to cover
                        // the entire diamond. The previous `Crop` setting
                        // rendered the image as `Fit` for any source
                        // whose aspect ratio didn't match the diamond's,
                        // which produced the visible letterbox inside the
                        // rotated shape.
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.size(size),
                        loading = { FallbackBadgeContent(fallbackAsset, size) },
                        error = { FallbackBadgeContent(fallbackAsset, size) },
                    )
                } else {
                    FallbackBadgeContent(fallbackAsset, size)
                }
            }
        }
    }
}

@Composable
private fun FallbackBadgeContent(
    fallbackAsset: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val resolved = fallbackAsset?.let { name ->
        val ctx = LocalContext.current
        ctx.resources.getIdentifier(name, "drawable", ctx.packageName).takeIf { it != 0 }
    }
    if (resolved != null) {
        SubcomposeAsyncImage(
            model = resolved,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = modifier.size(size),
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(size / 2),
        )
    }
}

@Preview(showBackground = true, name = "Diamond — light")
@Composable
private fun KornerzDiamondImageLightPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzDiamondImage(
            imageUrl = null,
            fallbackAsset = null,
            size = 52.dp,
            cornerRadius = 10.dp,
        )
    }
}

@Preview(showBackground = true, name = "Diamond — dark")
@Composable
private fun KornerzDiamondImageDarkPreview() {
    KornerzTheme(darkTheme = true) {
        KornerzDiamondImage(
            imageUrl = null,
            fallbackAsset = null,
            size = 52.dp,
            cornerRadius = 10.dp,
        )
    }
}
