package com.kornerz.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kornerz.core.ui.theme.Spacing

/**
 * The Kornerz brand button. Three variants — `Filled` (primary CTA),
 * `Outlined` (secondary CTA), and `Text` (tertiary / link). Modifier
 * is the last parameter (architecture §4). Use [enabled] to disable
 * while a request is in flight.
 *
 * [textColor] lets the caller override the Material theme default —
 * useful for the Welcome screen where both buttons sit on a
 * full-bleed background image and need white / black foreground.
 * Pass `null` (default) to fall back to the theme color.
 */
enum class KornerzButtonStyle { Filled, Outlined, Text }

@Composable
fun KornerzButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: KornerzButtonStyle = KornerzButtonStyle.Filled,
    textColor: Color? = null,
) {
    val height = 52.dp
    val baseModifier = modifier.fillMaxWidth().height(height)
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = textColor ?: MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
    val contentPadding = PaddingValues(
        horizontal = Spacing.space8,
        vertical = Spacing.space4,
    )
    when (style) {
        KornerzButtonStyle.Filled -> {
            val colors = if (textColor != null) {
                ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = textColor,
                )
            } else {
                ButtonDefaults.buttonColors()
            }
            Button(
                onClick = onClick,
                modifier = baseModifier,
                enabled = enabled,
                contentPadding = contentPadding,
                colors = colors,
                content = { content() },
            )
        }
        KornerzButtonStyle.Outlined -> {
            val borderColor = textColor ?: MaterialTheme.colorScheme.outline
            OutlinedButton(
                onClick = onClick,
                modifier = baseModifier,
                enabled = enabled,
                contentPadding = contentPadding,
                border = BorderStroke(width = 1.dp, color = borderColor),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = textColor ?: MaterialTheme.colorScheme.primary,
                ),
                content = { content() },
            )
        }
        KornerzButtonStyle.Text -> TextButton(
            onClick = onClick,
            modifier = baseModifier,
            enabled = enabled,
            content = { content() },
        )
    }
}