package com.kornerz.feature.kornerz.nooks_list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.model.Nook
import com.kornerz.core.model.User
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.core.ui.components.nooks.ScheduledNookCard
import com.kornerz.core.ui.components.nooks.nooksAccentColor
import kotlinx.datetime.Instant

/**
 * Vertical list of upcoming nooks (spec `korner-detail/spec.md` §4.8).
 * Renders [ScheduledNookCard] from `feature/nooks/.../components/`
 * using the iOS-shaped card layout (colored background, triangle
 * cover, "Add" button). Mirrors the Nooks tab "Upcoming" section
 * row-by-row.
 */
@Composable
fun NooksListSection(
    nooks: List<Nook>,
    parentKornerName: String,
    parentKornerThemeColor: String,
    parentKornerCoverUrl: String? = null,
    onNookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordered = remember(nooks) { nooks.sortedBy { it.startDate } }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = Spacing.space4)) {
        ordered.forEachIndexed { index, nook ->
            ScheduledNookCard(
                nook = nook,
                publisher = null,
                parentKornerName = parentKornerName,
                parentKornerCoverUrl = parentKornerCoverUrl,
                accentColor = nooksAccentColor(index + 1),
                onClick = onNookClick,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NooksListSectionPreview() {
    KornerzTheme(darkTheme = false) {
        NooksListSection(
            nooks = listOf(
                Nook(id = "n1", communityId = "c1", title = "Tomorrow's Talk", startDate = Instant.parse("2026-10-11T19:00:00Z")),
                Nook(id = "n2", communityId = "c1", title = "Next Week AMA", startDate = Instant.parse("2026-10-17T19:00:00Z")),
            ),
            parentKornerName = "Design Korner",
            parentKornerThemeColor = "#E27D5A",
            onNookClick = {},
        )
    }
}
