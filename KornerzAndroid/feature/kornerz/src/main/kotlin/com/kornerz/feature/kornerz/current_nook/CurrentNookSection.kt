package com.kornerz.feature.kornerz.current_nook

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.model.Nook
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.core.ui.components.nooks.LiveKornerCard
import com.kornerz.core.ui.components.nooks.nooksAccentColor
import kotlinx.datetime.Instant

/**
 * Current Nook section of the Korner detail screen (spec
 * `korner-detail/spec.md` §4.7). Renders a single [LiveKornerCard]
 * from `feature/nooks/.../components/`.
 *
 * The accent color is `nooksAccentColor(0)` so the card matches
 * the first row of the Nooks tab list (iOS parity — the iOS app
 * uses the same per-index color for the same nook wherever it
 * appears).
 */
@Composable
fun CurrentNookSection(
    nook: Nook,
    parentKornerName: String,
    parentKornerThemeColor: String,
    onNookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = Spacing.space4)) {
        LiveKornerCard(
            nook = nook,
            parentKornerName = parentKornerName,
            accentColor = nooksAccentColor(0),
            onClick = onNookClick,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CurrentNookSectionPreview() {
    KornerzTheme(darkTheme = false) {
        CurrentNookSection(
            nook = Nook(
                id = "n1",
                communityId = "c1",
                title = "Friday Fireside",
                startDate = Instant.parse("2026-10-10T19:00:00Z"),
                isLive = true,
            ),
            parentKornerName = "Design Korner",
            parentKornerThemeColor = "#E27D5A",
            onNookClick = {},
        )
    }
}
