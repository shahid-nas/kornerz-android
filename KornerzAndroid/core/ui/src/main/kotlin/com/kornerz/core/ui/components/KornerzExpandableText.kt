package com.kornerz.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * iOS's estimated characters per line, used by the overflow heuristic.
 * Mirrors `estimatedCharactersPerLine` in
 * `kornerz/Kornerz/Components/ExpandableText.swift`.
 */
private const val ESTIMATED_CHARACTERS_PER_LINE = 50

/** iOS's expand/collapse animation duration. */
private const val EXPANSION_ANIMATION_MILLIS = 300

/**
 * iOS's overflow heuristic, ported 1:1.
 *
 * `ExpandableText.swift` estimates the rendered line count as
 * `text.count / 50 + text.components(separatedBy: .newlines).count` and
 * treats the text as overflowing when that estimate is **greater** than
 * the line limit. `components(separatedBy: .newlines).count` is the number
 * of newline-separated chunks, i.e. the number of rendered lines — which
 * is why a single-line 120-character body is considered overflowing
 * (120 / 50 = 2, plus the one line it occupies = 3 > 2) while a
 * single-line 90-character body is not (90 / 50 = 1, plus one line = 2,
 * which is not greater than 2).
 */
internal fun isTextOverflowing(text: String, lineLimit: Int): Boolean =
    text.length / ESTIMATED_CHARACTERS_PER_LINE + text.lines().size > lineLimit

/**
 * Collapsible text block. 1:1 port of iOS
 * `kornerz/Kornerz/Components/ExpandableText.swift`: the body is
 * truncated to [lineLimit] lines and an accent-tinted toggle appears
 * underneath when the body overflows. Tapping the toggle expands the
 * body to its full height over 300 ms with an ease-in-out animation and
 * flips the label.
 *
 * The expansion state is owned by the component — callers never hoist
 * the flag (iOS keeps it in `@State` too). [initiallyExpanded] only sets
 * the starting value; the toggle still owns every subsequent change.
 */
@Composable
fun KornerzExpandableText(
    text: String,
    lineLimit: Int = 2,
    accentColor: Color,
    expandedLabel: String = "View More",
    collapsedLabel: String = "View Less",
    initiallyExpanded: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember(text, initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    val isOverflowing = remember(text, lineLimit) { isTextOverflowing(text, lineLimit) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            style = textStyle,
            color = textColor,
            maxLines = if (isExpanded) Int.MAX_VALUE else lineLimit,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.animateContentSize(
                animationSpec = tween(
                    durationMillis = EXPANSION_ANIMATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            ),
        )
        if (isOverflowing) {
            Text(
                text = if (isExpanded) collapsedLabel else expandedLabel,
                style = textStyle,
                color = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
            )
        }
    }
}

@Preview(showBackground = true, name = "ExpandableText — collapsed (light)")
@Composable
private fun KornerzExpandableTextCollapsedLightPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzExpandableText(
            text = LONG_PREVIEW_TEXT,
            accentColor = Color(0xFFE27D5A),
        )
    }
}

@Preview(showBackground = true, name = "ExpandableText — collapsed (dark)")
@Composable
private fun KornerzExpandableTextCollapsedDarkPreview() {
    KornerzTheme(darkTheme = true) {
        KornerzExpandableText(
            text = LONG_PREVIEW_TEXT,
            accentColor = Color(0xFFEBAA4A),
        )
    }
}

@Preview(showBackground = true, name = "ExpandableText — expanded (light)")
@Composable
private fun KornerzExpandableTextExpandedLightPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzExpandableText(
            text = LONG_PREVIEW_TEXT,
            accentColor = Color(0xFFE27D5A),
            initiallyExpanded = true,
        )
    }
}

@Preview(showBackground = true, name = "ExpandableText — expanded (dark)")
@Composable
private fun KornerzExpandableTextExpandedDarkPreview() {
    KornerzTheme(darkTheme = true) {
        KornerzExpandableText(
            text = LONG_PREVIEW_TEXT,
            accentColor = Color(0xFFEBAA4A),
            initiallyExpanded = true,
        )
    }
}

private const val LONG_PREVIEW_TEXT =
    "This is a longer text that should be truncated to show the expandable " +
        "functionality. It contains multiple sentences and should definitely " +
        "exceed the line limit we have set."
