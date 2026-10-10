package com.kornerz.feature.nooks.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kornerz.core.ui.components.HomeShimmerPlaceholder

/**
 * Full-width 16:9 Coil cover banner for the Nook detail screen. No
 * parallax and no overlaid title — same pattern as
 * `KornerDetailHeader` cover from P3 but stripped of the title +
 * bottom accent strip.
 *
 * Renders a [HomeShimmerPlaceholder] matching the cover dimensions
 * while the Coil image is loading.
 */
@Composable
fun NookDetailCoverBanner(
    coverUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        HomeShimmerPlaceholder(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize())
        }
        AsyncImage(
            model = coverUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
