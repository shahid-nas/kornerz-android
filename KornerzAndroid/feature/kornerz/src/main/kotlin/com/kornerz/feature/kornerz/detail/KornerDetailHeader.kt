package com.kornerz.feature.kornerz.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kornerz.core.model.Korner
import com.kornerz.core.ui.theme.Spacing

/**
 * Header banner for the Korner detail screen. 16:9 Coil cover, name
 * overlaid at bottom-leading in white, 4dp bottom accent strip in the
 * theme color, full-width "Join" / "Joined" `FilledTonalButton` below.
 *
 * Mirrors iOS `KornerDetailHeaderView.swift` section of
 * `KornerDetailView.swift`. Tap on the CTA forwards
 * `KornerDetailEvent.JoinCtaClicked`.
 */
@Composable
fun KornerDetailHeader(
    korner: Korner,
    onJoinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            AsyncImage(
                model = korner.coverUrl,
                contentDescription = korner.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                    ),
            )
            Text(
                text = korner.name,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.space6),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter)
                    .background(parseThemeColor(korner.themeColor)),
            )
        }
        FilledTonalButton(
            onClick = onJoinClick,
            enabled = !korner.isJoined,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
        ) {
            Text(text = if (korner.isJoined) "Joined" else "Join")
        }
    }
}

@Composable
fun KornerDetailHeaderShimmer() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.space8),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

private fun parseThemeColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")) }
        .getOrDefault(Color(0xFFE27D5A))
