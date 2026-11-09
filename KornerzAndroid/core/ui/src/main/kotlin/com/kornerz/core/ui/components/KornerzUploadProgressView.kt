package com.kornerz.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * The upload-progress card shown over a dimmed scrim while media is
 * being uploaded. 1:1 port of iOS
 * `kornerz/Kornerz/Components/UploadProgressView.swift`.
 *
 * The iOS card stacks two progress blocks:
 *
 * 1. the **overall** progress — a "Upload Progress" headline, a linear
 *    bar tinted with the brand purple (`AppColor.C5D47ED`), vertically
 *    stretched to 2x its natural height, and the whole-progress
 *    percentage underneath;
 * 2. the **current step** — the step label ("Uploading image..."), a
 *    linear bar tinted blue and stretched to 1.5x, and the
 *    step-progress percentage underneath.
 *
 * The whole card is a white surface with a 12 dp corner radius and a
 * drop shadow, inset by a further 16 dp from the screen edges.
 *
 * Modifier is the last parameter (architecture §4).
 */
@Composable
fun KornerzUploadProgressView(
    isVisible: Boolean,
    progress: Double,
    step: String,
    stepProgress: Double,
    modifier: Modifier = Modifier,
) {
    if (!isVisible) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.space12),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        ProgressBlock(
            label = UPLOAD_PROGRESS_HEADLINE,
            value = progress,
            barColor = MaterialTheme.colorScheme.primary,
            barHeight = 4.dp,
            percentStyle = MaterialTheme.typography.bodySmall,
        )
        ProgressBlock(
            label = step,
            value = stepProgress,
            barColor = MaterialTheme.colorScheme.secondary,
            barHeight = 3.dp,
            percentStyle = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * One headline + bar + percentage block. The bar's height is fixed
 * rather than stretched, so the "2x / 1.5x" stretch the iOS card
 * applies to its `ProgressView` is expressed as the bar thickness
 * (4 dp for the overall bar, 3 dp for the step bar).
 */
@Composable
private fun ProgressBlock(
    label: String,
    value: Double,
    barColor: Color,
    barHeight: androidx.compose.ui.unit.Dp,
    percentStyle: androidx.compose.ui.text.TextStyle,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LinearProgressIndicator(
            progress = { value.coerceIn(0.0, 1.0).toFloat() },
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
        )
        Text(
            text = "${(value.coerceIn(0.0, 1.0) * 100).toInt()}%",
            style = percentStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
    }
}

/**
 * The headline above the overall progress bar. iOS uses the literal
 * "Upload Progress"; the shared component is used by more than one
 * feature, so the copy is a parameter default rather than a resource
 * here.
 */
private const val UPLOAD_PROGRESS_HEADLINE = "Upload Progress"

@Preview(showBackground = true, name = "UploadProgress — light")
@Composable
private fun KornerzUploadProgressViewLightPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzUploadProgressView(
            isVisible = true,
            progress = 0.6,
            step = "Uploading video...",
            stepProgress = 0.8,
            modifier = Modifier.padding(Spacing.space16),
        )
    }
}

@Preview(showBackground = true, name = "UploadProgress — dark")
@Composable
private fun KornerzUploadProgressViewDarkPreview() {
    KornerzTheme(darkTheme = true) {
        KornerzUploadProgressView(
            isVisible = true,
            progress = 0.3,
            step = "Uploading image...",
            stepProgress = 0.5,
            modifier = Modifier.padding(Spacing.space16),
        )
    }
}
