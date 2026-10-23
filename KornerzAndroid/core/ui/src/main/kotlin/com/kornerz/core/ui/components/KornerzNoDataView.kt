package com.kornerz.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * The shared no-data surface. Mirrors iOS `Components/NoDataView.swift`
 * (`kornerz/Kornerz/Components/NoDataView.swift`).
 *
 * iOS renders a 75 × 75-pt illustration above a Satoshi-14 title, in a
 * stack with 50 pt of vertical padding.
 *
 * The illustration is a **parameter**, so a designer-supplied asset can
 * be substituted without changing the component; the default is the
 * Material inbox icon (iOS's default is the `tray.fill` SF Symbol, and
 * the two carry the same meaning).
 *
 * **This is a sibling of [KornerzEmptyState], not a replacement for
 * it.** `KornerzEmptyState` is the generic empty state adopted in
 * P2–P6 and remains available; the list screens that standardise on
 * the iOS-named surfaces use this one.
 */
@Composable
fun KornerzNoDataView(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Inbox,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = NO_DATA_VERTICAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(NO_DATA_ILLUSTRATION_SIZE),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

/** iOS's `.frame(width: 75, height: 75)`. */
private val NO_DATA_ILLUSTRATION_SIZE = 75.dp

/** iOS's `.padding(.vertical, 50)`. */
private val NO_DATA_VERTICAL_PADDING = 50.dp

@Preview(showBackground = true)
@Composable
private fun KornerzNoDataViewPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzNoDataView(title = "No communities yet")
    }
}
