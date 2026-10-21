package com.kornerz.feature.nooks.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.kornerz.core.ui.components.header.TabHeaderContent
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.nooks.components.LiveKornerCard
import com.kornerz.feature.nooks.components.NooksHeader
import com.kornerz.feature.nooks.components.ScheduledNookCard
import com.kornerz.feature.nooks.components.nooksAccentColor

/**
 * Stateless renderer for the Nooks tab list. 1:1 port of iOS
 * `NooksTabView.body`:
 *  - Single `ScrollView { VStack { ... } }` (`PullToRefreshBox` in
 *    Android) that contains:
 *      1. `HomeHeaderView` (the shared avatar + points —
 *         [TabHeaderContent])
 *      2. `TabHeaderView` (the "Nooks" title + Live/Upcoming toggle —
 *         [NooksHeader])
 *      3. The selected section's content (Live or Upcoming) rendered
 *         as plain Column children — **no nested scroll**. The
 *         parent `PullToRefreshBox` is the only scroll surface, so
 *         the `verticalScroll` / `fillMaxSize` from the previous
 *         `ScrollingList` (which produced the duplicated top
 *         padding on the first build) are gone.
 *
 *  Loading / error / empty layouts are handled per-section. Pull-to-
 *  refresh is wired at the screen level (see [NooksScreen]).
 */
@Composable
fun NooksContent(
    state: NooksState,
    onEvent: (NooksEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Two-tier layout: the shared header (avatar + points) and the
    // "Nooks" sub-header are sticky at the top; only the list of
    // Live / Upcoming nooks scrolls. This is the iOS NooksTabView
    // decomposition `ScrollView { VStack { header; tabHeader;
    // scroll(items) } }` rewritten so the scroll surface is the
    // section only — matches the design review preference that the
    // header row never scrolls out of view.
    Column(modifier = modifier.fillMaxSize()) {
        TabHeaderContent(
            state = state.header,
            onAvatarTap = { onEvent(NooksEvent.AvatarTapped) },
        )
        NooksHeader(
            title = state.selectedType.titleNooks,
            selectedType = state.selectedType,
            onSelect = { type -> onEvent(NooksEvent.SelectType(type)) },
        )
        // The section's `verticalScroll` is the single scrollable
        // surface inside the parent `PullToRefreshBox`, so the
        // pull-to-refresh gesture still resolves to `NooksEvent.Refresh`.
        // `weight(1f)` makes the section take the remaining vertical
        // space so the scroll's bottom edge sits flush against the
        // shell's bottom tab bar (no extra padding between them).
        when (state.selectedType) {
            NooksType.Live -> LiveSection(
                state = state,
                onEvent = onEvent,
                modifier = Modifier.weight(1f),
            )
            NooksType.Upcoming -> UpcomingSection(
                state = state,
                onEvent = onEvent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LiveSection(
    state: NooksState,
    onEvent: (NooksEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.space4, vertical = Spacing.space4),
                verticalArrangement = Arrangement.spacedBy(Spacing.space2),
            ) {
                items.forEachIndexed { index, korner ->
                    LiveKornerCard(
                        korner = korner,
                        accentColor = nooksAccentColor(index),
                        onClick = { id -> onEvent(NooksEvent.LiveCardClicked(id)) },
                        onJoin = { sessionId -> onEvent(NooksEvent.JoinVideoCall(sessionId)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingSection(
    state: NooksState,
    onEvent: (NooksEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.space4, vertical = Spacing.space4),
                verticalArrangement = Arrangement.spacedBy(Spacing.space2),
            ) {
                items.forEachIndexed { index, item ->
                    ScheduledNookCard(
                        nook = item.nook,
                        publisher = item.publisher,
                        parentKornerName = item.parentKorner.name,
                        parentKornerCoverUrl = item.parentKorner.coverUrl,
                        accentColor = nooksAccentColor(index),
                        onClick = { id -> onEvent(NooksEvent.CardClicked(id)) },
                    )
                }
            }
        }
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
                        .height(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
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
