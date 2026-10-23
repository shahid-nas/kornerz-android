package com.kornerz.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * The shared retry surface. Mirrors iOS `Components/RetryView.swift`
 * (`kornerz/Kornerz/Components/RetryView.swift`).
 *
 * iOS renders, in a vertically-centred stack with 8-pt spacing:
 *  - the error message at 12 pt, centred and wrapping over multiple
 *    lines;
 *  - below it, a capsule button labelled `Retry again` — the English
 *    value of iOS's `retry_again` localisation key — using
 *    `.buttonStyle(.borderedProminent)` with `.tint(.gray)` and a
 *    20-pt corner radius.
 *
 * The component is deliberately **not** tied to a single error type: it
 * takes the message, a retry callback and an optional modifier.
 *
 * Colours come from the theme's `colorScheme` and the type from
 * `MaterialTheme.typography` — no literal hex anywhere.
 *
 * **This is a sibling of [KornerzErrorState], not a replacement for
 * it.** `KornerzErrorState` renders an `Outlined` "Retry" button and is
 * still used by the screens that adopted it in P2–P6; the list screens
 * standardise onto this component instead.
 */
@Composable
fun KornerzRetryView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.space4),
            modifier = Modifier.padding(Spacing.space8),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(CORNER_RADIUS),
                contentPadding = PaddingValues(
                    horizontal = Spacing.space8,
                    vertical = Spacing.space2,
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(
                    text = RETRY_LABEL,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** The English value of iOS's `retry_again` localisation key. */
const val RETRY_LABEL: String = "Retry again"

/** iOS's `.cornerRadius(20)`. */
private val CORNER_RADIUS = 20.dp

@Preview(showBackground = true)
@Composable
private fun KornerzRetryViewPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzRetryView(
            message = "Failed to load",
            onRetry = {},
        )
    }
}
