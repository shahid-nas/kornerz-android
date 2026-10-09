package com.kornerz.feature.kornerz.nooks_list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.model.Nook
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.kornerz.current_nook.NookTile

/**
 * Vertical list of upcoming nooks (spec `korner-detail/spec.md` §4.8).
 * Reuses [NookTile] from `current_nook/` — single shared composable,
 * zero duplication. Ordered by `startDate` ascending — the caller is
 * responsible for sorting the input list (the repository returns
 * nooks as-is from Firestore; sorting happens here in the VM/content
 * layer to keep the network call lazy).
 *
 * Section is hidden when the scheduled nooks list is empty per spec
 * §"Nooks list is hidden when empty".
 */
@Composable
fun NooksListSection(
    nooks: List<Nook>,
    onNookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordered = remember(nooks) { nooks.sortedBy { it.startDate } }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = Spacing.space4)) {
        Text(
            text = "Nooks",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        ordered.forEach { nook ->
            NookTile(nook = nook, onClick = { onNookClick(nook.id) })
        }
    }
}

private fun <T> remember(value: T, calculation: () -> List<Nook>): List<Nook> = value.let { calculation() }

@Preview(showBackground = true)
@Composable
private fun NooksListSectionPreview() {
    KornerzTheme(darkTheme = false) {
        NooksListSection(
            nooks = listOf(
                Nook(id = "n1", communityId = "c1", title = "Tomorrow's Talk", startDate = kotlinx.datetime.Instant.parse("2026-10-11T19:00:00Z")),
                Nook(id = "n2", communityId = "c1", title = "Next Week AMA", startDate = kotlinx.datetime.Instant.parse("2026-10-17T19:00:00Z")),
            ),
            onNookClick = {},
        )
    }
}
