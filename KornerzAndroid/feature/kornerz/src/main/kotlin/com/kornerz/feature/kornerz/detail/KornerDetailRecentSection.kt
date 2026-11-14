package com.kornerz.feature.kornerz.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.components.HomeSectionHeader
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * The "Recently in &lt;community&gt;" header of the Korner detail
 * screen. 1:1 port of iOS `KornerDetailView.recentHeader`
 * (`SwiftUI/Dashboard/Korner/KornerDetail/KornerDetailView.swift`).
 *
 * iOS renders a `HomeSectionHeader` titled "recently in {community}"
 * followed by the community's latest loops, or an empty state with a
 * tray glyph and "no loops available" when there are none.
 *
 * The rows reuse `feature/loops`' `LoopItemView`, so the chrome matches
 * the Loops tab and the Home carousel row for row — the same one-way
 * `feature/kornerz` → `feature/loops` edge the Home tab already uses.
 *
 * The header and the empty state are separate composables because the
 * block is emitted as **items of the screen's own `LazyColumn`** — see
 * `KornerDetailContent.kornerDetailRecentItems`. A nested
 * `LazyColumn` inside the screen's scrolling column was measured with
 * an infinite height constraint and crashed the screen.
 */
@Composable
fun KornerDetailRecentHeader(
    communityName: String,
    modifier: Modifier = Modifier,
) {
    HomeSectionHeader(
        title = "Recently in $communityName",
        modifier = modifier.padding(horizontal = Spacing.space8),
    )
}

/**
 * The "no loops available" state. iOS draws a 48-pt `tray` glyph in
 * grey above a 16-pt medium grey message, 40 pt below the header.
 */
@Composable
internal fun EmptyRecentLoops(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space8)
            .padding(top = Spacing.space24),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(Spacing.space8))
        Text(
            text = "No loops available",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, name = "Korner detail recent — empty")
@Composable
private fun KornerDetailRecentSectionEmptyPreview() {
    KornerzTheme(darkTheme = false) {
        Column {
            KornerDetailRecentHeader(communityName = "Design Korner")
            EmptyRecentLoops()
        }
    }
}
