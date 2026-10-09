package com.kornerz.feature.kornerz.current_nook

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
import kotlinx.datetime.Instant

/**
 * Current Nook section of the Korner detail screen (spec
 * `korner-detail/spec.md` §4.7). Section is hidden when no
 * nook has `isLive = true` per spec §"Current Nook is hidden when no
 * live nook exists".
 */
@Composable
fun CurrentNookSection(
    nook: Nook,
    onNookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = Spacing.space4)) {
        Text(
            text = "Current Nook",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        NookTile(nook = nook, onClick = { onNookClick(nook.id) })
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
            onNookClick = {},
        )
    }
}
