package com.kornerz.feature.nooks.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.Korner
import com.kornerz.core.model.Nook
import com.kornerz.core.model.NookDetail
import com.kornerz.core.ui.components.HomeShimmerPlaceholder
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import kotlinx.datetime.Instant

/**
 * Stateless renderer for the Nook detail screen. Composes the cover
 * banner + title block + metadata block + back-to-Korner row into a
 * scrollable column. Loading / error / not-found layouts are handled
 * by the host (`NookDetailScreen`) per architecture §2.2.
 */
@Composable
fun NookDetailContent(
    detail: NookDetail,
    onEvent: (NookDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        NookDetailCoverBanner(
            coverUrl = detail.nook.coverUrl,
            contentDescription = detail.nook.title,
        )
        NookDetailTitleBlock(
            title = detail.nook.title,
            startDate = detail.nook.startDate,
        )
        NookDetailMetadataBlock(
            isLive = detail.nook.isLive,
            parentKornerName = detail.korner.name,
            parentKornerCoverUrl = detail.korner.coverUrl,
        )
        Spacer(modifier = Modifier.height(Spacing.space12))
        BackToKornerRow(
            parentKornerName = detail.korner.name,
            onClick = { onEvent(NookDetailEvent.BackToKornerClicked) },
        )
    }
}

/** Loading skeleton for the four Nook detail sections. */
@Composable
fun NookDetailContentShimmer(modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            HomeShimmerPlaceholder(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .padding(horizontal = Spacing.space8, vertical = Spacing.space4)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

/** "Nook not found" empty state with a Back affordance. */
@Composable
fun NookDetailNotFound(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.space8),
    ) {
        Text(
            text = "Nook not found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "This nook may have ended or been removed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, name = "NookDetailContent — Populated")
@Composable
private fun NookDetailContentPreview() {
    KornerzTheme(darkTheme = false) {
        NookDetailContent(
            detail = NookDetail(
                nook = Nook(
                    id = "n1",
                    communityId = "c1",
                    title = "Friday Fireside",
                    startDate = Instant.parse("2026-10-10T19:00:00Z"),
                    isLive = true,
                ),
                korner = Korner(
                    id = "c1",
                    name = "Design Korner",
                    themeColor = "#E27D5A",
                ),
            ),
            onEvent = {},
        )
    }
}
