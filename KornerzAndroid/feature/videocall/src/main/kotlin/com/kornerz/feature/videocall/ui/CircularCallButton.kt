package com.kornerz.feature.videocall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * The visual variant of a call control, mirroring iOS
 * `CircleButtonStyleConfig` (`kornerz/Kornerz/Components/CircleButtonStyle.swift`).
 *
 * - [Light] — `CircleButtonStyleConfig.light`: a solid pale disc with a
 *   dark glyph. Used by the end-call button.
 * - [Bordered] — `CircleButtonStyleConfig.bordered`: a transparent disc
 *   with a 2dp pale ring. Used by every other call control; when the
 *   control is *selected* the disc fills with [VideoCallPalette.Tile]
 *   and the glyph turns to [VideoCallPalette.SelectedControl].
 */
internal enum class CallButtonStyle {
    Light,
    Bordered,
}

/**
 * Circular call control. 1:1 port of iOS `CircularButton` +
 * `CircleButtonStyle` with the `videoCall` configuration
 * (50pt disc, 12pt padding, 26pt glyph).
 */
@Composable
internal fun CircularCallButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    style: CallButtonStyle = CallButtonStyle.Bordered,
    flipHorizontally: Boolean = false,
) {
    val background = when {
        selected -> VideoCallPalette.Tile
        style == CallButtonStyle.Light -> VideoCallPalette.Control
        else -> Color.Transparent
    }
    val foreground = when {
        selected -> VideoCallPalette.SelectedControl
        style == CallButtonStyle.Light -> VideoCallPalette.Tile
        else -> VideoCallPalette.Control
    }

    Box(
        modifier = modifier
            .size(CallButtonMetrics.DiscSize)
            .clip(CircleShape)
            .background(background)
            .then(
                if (style == CallButtonStyle.Bordered) {
                    Modifier.border(
                        width = CallButtonMetrics.BorderWidth,
                        color = foreground,
                        shape = CircleShape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = foreground,
            modifier = Modifier
                .size(CallButtonMetrics.IconSize)
                // Material's `pan_tool` is a right hand; iOS draws the
                // raised hand with `hand.raised`, which is the mirrored
                // (left hand) glyph. Mirroring the vector keeps the
                // icon set to one glyph and matches iOS.
                .then(
                    if (flipHorizontally) {
                        Modifier.scale(scaleX = -1f, scaleY = 1f)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

/**
 * Metrics taken from iOS `CircularButtonConfigs.videoCall`
 * (`size: 50`, `padding: 12`) — the glyph is `size - padding * 2`.
 */
internal object CallButtonMetrics {
    val DiscSize = 50.dp
    val IconSize = 26.dp
    val BorderWidth = 2.dp
}

@Preview(showBackground = true, name = "Call controls")
@Composable
private fun CircularCallButtonPreview() {
    KornerzTheme(darkTheme = false) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            CircularCallButton(
                icon = Icons.Filled.Close,
                contentDescription = "End call",
                onClick = {},
                style = CallButtonStyle.Light,
            )
            CircularCallButton(
                icon = Icons.Filled.Mic,
                contentDescription = "Toggle audio",
                onClick = {},
            )
            CircularCallButton(
                icon = Icons.Filled.Videocam,
                contentDescription = "Toggle video",
                onClick = {},
                selected = true,
            )
        }
    }
}
