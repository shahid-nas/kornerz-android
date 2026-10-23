package com.kornerz.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * The shared pagination footer. Mirrors iOS
 * `Components/LoadingMoreView.swift`
 * (`kornerz/Kornerz/Components/LoadingMoreView.swift`).
 *
 * iOS renders a horizontal stack with 16-pt spacing, centred and
 * full-width with padding: a `ProgressView` and, beside it, a label
 * whose text is the English value of iOS's `loading_more_items`
 * localisation key.
 *
 * Every paginated list screen standardises its footer on this
 * component, replacing the ad-hoc spinner each screen used to inline.
 */
@Composable
fun KornerzLoadingMoreView(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.space8),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(PROGRESS_SIZE))
        Text(
            text = LOADING_MORE_LABEL,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.space8),
        )
    }
}

/** The English value of iOS's `loading_more_items` localisation key. */
const val LOADING_MORE_LABEL: String = "Loading more items"

private val PROGRESS_SIZE = 24.dp

@Preview(showBackground = true)
@Composable
private fun KornerzLoadingMoreViewPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzLoadingMoreView()
    }
}
