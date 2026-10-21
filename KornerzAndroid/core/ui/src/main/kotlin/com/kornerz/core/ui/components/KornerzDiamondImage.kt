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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * 45°-rotated rounded-square image clip. 1:1 port of iOS
 * `DiamondImageView` (`kornerz/Kornerz/Components/DiamondImageView.swift`).
 *
 * The image (or fallback asset) is rendered into a rounded square of
 * [size] with [cornerRadius], then the whole layer is rotated 45° and
 * clipped back to the diamond extents. The visible bounds therefore
 * match iOS (`size × size` rotated 45° → diamond extents of `size`).
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
) {
    val clipShape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .rotate(45f)
                .clip(clipShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                    modifier = Modifier
                        .size(size)
                        .rotate(-45f),
                    loading = { FallbackBadgeContent(fallbackAsset, size) },
                    error = { FallbackBadgeContent(fallbackAsset, size) },
                )
            } else {
                FallbackBadgeContent(fallbackAsset, size, Modifier)
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
            modifier = modifier.size(size).rotate(-45f),
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
                .size(size / 2)
                .rotate(-45f),
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
