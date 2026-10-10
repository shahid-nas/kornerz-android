package com.kornerz.feature.nooks.list

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Korner
import com.kornerz.core.model.Nook
import com.kornerz.core.model.UpcomingNookItem
import com.kornerz.core.model.User
import com.kornerz.core.ui.components.HomeShimmerPlaceholder
import com.kornerz.core.ui.components.KornerzEmptyState
import com.kornerz.core.ui.components.KornerzErrorState
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.nooks.components.LiveKornerCard
import com.kornerz.feature.nooks.components.NooksHeader
import com.kornerz.feature.nooks.components.ScheduledNookCard
import com.kornerz.feature.nooks.components.nooksAccentColor

/**
 * Stateless renderer for the Nooks tab list. 1:1 port of iOS
 * `NooksTabView.body`:
 *  - Header with the "Nooks" title + a Live/Upcoming toggle
 *    ([NooksHeader] mirrors iOS `TabHeaderView`).
 *  - Only the section matching `state.selectedType` is rendered
 *    (mirrors iOS `if viewModel.nooksType == .live { kornerNooks }
 *    else { upcommingNooks }`).
 *  - Loading / error / empty layouts are handled per-section.
 *  - Pull-to-refresh is wired at the screen level (see
 *    [NooksScreen]) to match iOS `.refreshable`.
 */
@Composable
fun NooksContent(
    state: NooksState,
    onEvent: (NooksEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        NooksHeader(
            title = "Nooks",
            selectedType = state.selectedType,
            onSelect = { type -> onEvent(NooksEvent.SelectType(type)) },
        )
        Spacer(modifier = Modifier.height(Spacing.space2))
        when (state.selectedType) {
            NooksType.Live -> LiveSection(state = state, onEvent = onEvent)
            NooksType.Upcoming -> UpcomingSection(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun LiveSection(state: NooksState, onEvent: (NooksEvent) -> Unit) {
    when (val value = state.live) {
        is AsyncValue.Loading -> ShimmerRows()
        is AsyncValue.Error -> KornerzErrorState(
            message = value.throwable.message ?: "We couldn't load live nooks",
            onRetry = { onEvent(NooksEvent.Retry) },
        )
        is AsyncValue.Success -> {
            val items = value.data.items
            if (items.isEmpty()) {
                EmptyNooks()
                return
            }
            ScrollingList {
                items.forEachIndexed { index, korner ->
                    LiveKornerCard(
                        korner = korner,
                        accentColor = nooksAccentColor(index),
                        onClick = { id -> onEvent(NooksEvent.LiveCardClicked(id)) },
                    )
                    Spacer(modifier = Modifier.height(Spacing.space2))
                }
            }
        }
    }
}

@Composable
private fun UpcomingSection(state: NooksState, onEvent: (NooksEvent) -> Unit) {
    when (val value = state.upcoming) {
        is AsyncValue.Loading -> ShimmerRows()
        is AsyncValue.Error -> KornerzErrorState(
            message = value.throwable.message ?: "We couldn't load upcoming nooks",
            onRetry = { onEvent(NooksEvent.Retry) },
        )
        is AsyncValue.Success -> {
            val items = value.data.items
            if (items.isEmpty()) {
                EmptyNooks()
                return
            }
            ScrollingList {
                items.forEachIndexed { index, item ->
                    ScheduledNookCard(
                        nook = item.nook,
                        publisher = item.publisher,
                        parentKornerName = item.parentKorner.name,
                        accentColor = nooksAccentColor(index),
                        onClick = { id -> onEvent(NooksEvent.CardClicked(id)) },
                    )
                    Spacer(modifier = Modifier.height(Spacing.space2))
                }
            }
        }
    }
}

@Composable
private fun ScrollingList(content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = Spacing.space4),
    ) {
        content()
    }
}

@Composable
private fun EmptyNooks() {
    KornerzEmptyState(
        title = "No upcoming nooks",
        subtitle = "Check back later or join a Korner to see upcoming nooks",
    )
}

@Composable
private fun ShimmerRows() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space4, vertical = Spacing.space4),
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        repeat(4) {
            HomeShimmerPlaceholder(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "NooksContent — Loading")
@Composable
private fun NooksContentLoadingPreview() {
    KornerzTheme(darkTheme = false) {
        NooksContent(state = NooksState(), onEvent = {})
    }
}

@Preview(showBackground = true, name = "NooksContent — Populated Live")
@Composable
private fun NooksContentPopulatedLivePreview() {
    KornerzTheme(darkTheme = false) {
        NooksContent(
            state = NooksState(
                selectedType = NooksType.Live,
                live = AsyncValue.Success(
                    LiveSectionState(
                        items = listOf(
                            previewKorner(id = "c1", name = "Design Korner"),
                            previewKorner(id = "c2", name = "Indie Founders"),
                        ),
                    ),
                ),
                upcoming = AsyncValue.Success(
                    UpcomingSectionState(
                        items = listOf(
                            previewUpcoming(
                                nookId = "n1",
                                title = "Friday Fireside",
                                communityId = "c1",
                                communityName = "Design Korner",
                            ),
                        ),
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, name = "NooksContent — Populated Upcoming")
@Composable
private fun NooksContentPopulatedUpcomingPreview() {
    KornerzTheme(darkTheme = false) {
        NooksContent(
            state = NooksState(
                selectedType = NooksType.Upcoming,
                live = AsyncValue.Success(LiveSectionState()),
                upcoming = AsyncValue.Success(
                    UpcomingSectionState(
                        items = listOf(
                            previewUpcoming(
                                nookId = "n1",
                                title = "Tomorrow's Talk",
                                communityId = "c1",
                                communityName = "Design Korner",
                            ),
                            previewUpcoming(
                                nookId = "n2",
                                title = "Next Week AMA",
                                communityId = "c1",
                                communityName = "Design Korner",
                            ),
                        ),
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, name = "NooksContent — Empty")
@Composable
private fun NooksContentEmptyPreview() {
    KornerzTheme(darkTheme = false) {
        NooksContent(
            state = NooksState(
                selectedType = NooksType.Live,
                live = AsyncValue.Success(LiveSectionState()),
                upcoming = AsyncValue.Success(UpcomingSectionState()),
            ),
            onEvent = {},
        )
    }
}

private fun previewKorner(id: String, name: String) = Korner(
    id = id,
    name = name,
    themeColor = "#E27D5A",
    membersCount = 42,
)

private fun previewUpcoming(
    nookId: String,
    title: String,
    communityId: String,
    communityName: String,
): UpcomingNookItem = UpcomingNookItem(
    nook = Nook(
        id = nookId,
        communityId = communityId,
        title = title,
        startDate = kotlinx.datetime.Instant.parse("2026-10-10T19:00:00Z"),
        type = "open",
        publisherId = "p1",
    ),
    publisher = User(
        id = "p1",
        email = "p@example.com",
        displayName = "Ada",
        nickname = "ada",
        userCode = "p1",
        photoUrl = null,
    ),
    parentKorner = Korner(
        id = communityId,
        name = communityName,
        themeColor = "#E27D5A",
    ),
)
