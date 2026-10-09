package com.kornerz.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * Shared header used by the Kornerz tab root (and available to future
 * Kornerz features). Mirrors iOS `TabHeaderView.swift` — Satoshi Bold
 * 24sp title plus a 40dp tall placeholder area reserved for filter
 * chips that arrive in a follow-up phase.
 *
 * Phase 3 keeps the filter area static (no chips, no scrolling) so the
 * tab reads identically to its iOS counterpart without introducing
 * search/filter behavior that is out of scope.
 */
@Composable
fun KornerzHeaderView(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
        verticalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun KornerzHeaderViewLightPreview() {
    KornerzTheme(darkTheme = false) {
        KornerzHeaderView(title = "Kornerz")
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun KornerzHeaderViewDarkPreview() {
    KornerzTheme(darkTheme = true) {
        KornerzHeaderView(title = "Kornerz")
    }
}
