package com.kornerz.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * Shimmer effect overlay. Wraps any child content with a diagonal
 * gradient that animates back and forth, mirroring iOS
 * `.redacted(reason: .placeholder)`. The shimmer is rendered with a
 * translucent overlay over the actual content (which uses the same
 * layout the section would otherwise render) — exactly like SwiftUI's
 * `.redacted` keeps the same bounds and replaces inner glyphs with a
 * gray block.
 *
 * Modifier is last per architecture §4.
 */
@Composable
fun HomeShimmerPlaceholder(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-translate",
    )
    Box(modifier = modifier.clip(shape)) {
        content()
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            Color.Transparent,
                        ),
                        start = Offset(translate - 250f, translate - 250f),
                        end = Offset(translate, translate),
                    ),
                ),
        )
    }
}

@Preview(showBackground = true, name = "Shimmer — light")
@Composable
private fun HomeShimmerPlaceholderLightPreview() {
    KornerzTheme(darkTheme = false) {
        HomeShimmerPlaceholder(modifier = Modifier.size(120.dp)) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}

@Preview(showBackground = true, name = "Shimmer — dark")
@Composable
private fun HomeShimmerPlaceholderDarkPreview() {
    KornerzTheme(darkTheme = true) {
        HomeShimmerPlaceholder(modifier = Modifier.size(120.dp)) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}
