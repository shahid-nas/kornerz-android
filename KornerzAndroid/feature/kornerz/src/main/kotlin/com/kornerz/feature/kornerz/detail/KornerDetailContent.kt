package com.kornerz.feature.kornerz.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Community
import com.kornerz.core.model.Korner
import com.kornerz.core.model.KornerDetail
import com.kornerz.core.model.Nook
import com.kornerz.core.model.User
import com.kornerz.core.ui.components.KornerzLoadingMoreView
import com.kornerz.core.ui.theme.KornerzCardPalette
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.loops.components.LoopCardData
import com.kornerz.feature.loops.components.LoopItemView
import com.kornerz.feature.loops.data.toCardData
import kotlinx.datetime.Instant

/**
 * Stateless renderer for the Korner detail screen.
 *
 * Mirrors the iOS `KornerDetailView.contentView` block order:
 * ```
 * LazyVStack {
 *     KornerDetailHeaderView(viewModel:)
 *     KornerDetailNooksView(viewModel:)
 *     KornerUsersView(viewModel:)
 *     recentHeader
 *     recentContents
 * }
 * ```
 *
 * The About block is **not** inline on iOS — the header's "About ⌄"
 * affordance presents it as a modal sheet (`KornerAboutView`), so the
 * Android port presents [KornerAboutSheet] from the screen instead of
 * rendering it between the header and the Nooks section.
 *
 * iOS separates the blocks with the `LazyVStack`'s default 8-pt
 * spacing; a plain Compose `Column` has no such default, so the
 * screen's `LazyColumn` reproduces it with
 * `Arrangement.spacedBy(Spacing.space4)`.
 *
 * The screen is a **single** `LazyColumn`. The blocks that used to
 * live in a `Column(verticalScroll(...))` — most importantly the
 * "Recently in" section, whose rows are a nested `LazyColumn` — are
 * items of this one list, because a vertically scrollable component
 * nested inside another vertically scrollable component is measured
 * with an infinite height constraint and crashes the screen.
 */
@Composable
fun KornerDetailContent(
    state: KornerDetailState,
    onEvent: (KornerDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = (state.detail as? AsyncValue.Success)?.data ?: return
    val korner = detail.korner
    // The colour the screen paints itself with: the one the opening
    // screen handed over (the tile's colour), falling back to the
    // Korner document's own theme colour.
    val themeColor = parseKornerThemeColor(state.resolvedThemeColorHex)

    // The "Recently in" rows reuse the Loops tab's row chrome, so the
    // cards are projected through the shared `LoopMapper`.
    val community = remember(korner) {
        Community(
            id = korner.id,
            code = korner.id,
            communityId = korner.communityId,
            name = korner.name,
            firebaseImageURL = korner.coverUrl,
        )
    }
    val publishers = remember(state.kornerstones, detail.members) {
        state.kornerstones + detail.members
    }
    val recentCards = remember(state.recentLoops, community, publishers) {
        state.recentLoops.map { loop ->
            val publisher = publishers.firstOrNull { it.userCode != null && it.userCode == loop.publisherId }
            loop.toCardData(community, publisher)
        }
    }

    val listState = rememberLazyListState()
    val currentOnLoadMore by rememberUpdatedState { onEvent(KornerDetailEvent.RecentLoopsLoadMore) }

    // The "Recently in" block is the last thing on the screen, so the
    // next page of loops is requested once the list scrolls close to
    // its end. The trigger watches the **screen's** list state — the
    // recent loops are items of the same list, so their own scroll
    // position no longer exists.
    LaunchedEffect(listState, recentCards.size, state.hasMoreRecentLoops, state.isLoadingMoreRecentLoops) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            state.hasMoreRecentLoops &&
                !state.isLoadingMoreRecentLoops &&
                totalItems > 0 &&
                lastVisible >= totalItems - RECENT_LOAD_MORE_THRESHOLD
        }.collect { nearEnd ->
            if (nearEnd) currentOnLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        item(key = "korner-detail-header") {
            KornerDetailHeader(
                korner = korner,
                themeColor = themeColor,
                isFollowing = korner.isJoined,
                onBackClick = { onEvent(KornerDetailEvent.BackClicked) },
                onFollowClick = { onEvent(KornerDetailEvent.FollowClicked) },
                onAboutClick = { onEvent(KornerDetailEvent.AboutClicked) },
            )
        }
        item(key = "korner-detail-nooks") {
            KornerDetailNooksSection(
                korner = korner,
                nooks = detail.nooks,
                selectedTab = state.selectedNookTab,
                onTabSelected = { onEvent(KornerDetailEvent.NookTabSelected(it)) },
                themeColor = themeColor,
                onNookClick = { onEvent(KornerDetailEvent.NookTileClicked(it)) },
                onJoinClick = { onEvent(KornerDetailEvent.JoinCtaClicked(it)) },
            )
        }
        item(key = "korner-detail-users") {
            KornerDetailUsersSection(
                kornerstones = state.kornerstones,
                klouders = detail.members,
                selectedTab = state.selectedUserTab,
                onTabSelected = { onEvent(KornerDetailEvent.UserTabSelected(it)) },
                onUserClick = { onEvent(KornerDetailEvent.MemberTileClicked(it)) },
                onViewAllClick = { onEvent(KornerDetailEvent.ViewAllClicked(it)) },
                themeColor = themeColor,
            )
        }
        kornerDetailRecentItems(
            communityName = korner.name,
            cards = recentCards,
            isLoadingMore = state.isLoadingMoreRecentLoops,
            onLoopClick = { loopId, accentColorHex ->
                onEvent(KornerDetailEvent.LoopTileClicked(loopId, accentColorHex))
            },
        )
    }
}

/**
 * The "Recently in &lt;community&gt;" block, emitted as **items of the
 * screen's own list**.
 *
 * The rows used to live in a nested `LazyColumn`, which cannot be
 * measured inside a vertically scrolling parent — the nested list
 * received an infinite height constraint and the screen crashed with
 * *"Vertically scrollable component was measured with an infinity
 * maximum height constraints"*. Emitting the rows straight into the
 * screen's list is the fix the crash message itself recommends, and it
 * keeps the block scrolling with the rest of the page.
 */
private fun LazyListScope.kornerDetailRecentItems(
    communityName: String,
    cards: List<LoopCardData>,
    isLoadingMore: Boolean,
    onLoopClick: (loopId: String, accentColorHex: String) -> Unit,
) {
    item(key = "korner-detail-recent-header") {
        KornerDetailRecentHeader(communityName = communityName)
    }
    if (cards.isEmpty()) {
        item(key = "korner-detail-recent-empty") {
            EmptyRecentLoops()
        }
        return
    }
    items(items = cards, key = { "korner-detail-recent-${it.id}" }) { card ->
        val index = cards.indexOf(card)
        val accent = KornerzCardPalette.color(index)
        LoopItemView(
            data = card,
            color = accent,
            onClick = { onLoopClick(card.id, KornerzCardPalette.hexForIndex(index)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.space8),
        )
    }
    if (isLoadingMore) {
        item(key = "korner-detail-recent-loading-more") {
            KornerzLoadingMoreView()
        }
    }
}

/**
 * How many items from the end of the screen's list still count as
 * "close enough to the end" for the next page of recent loops to be
 * requested.
 */
private const val RECENT_LOAD_MORE_THRESHOLD = 2

@Preview(showBackground = true)
@Composable
private fun KornerDetailContentPreview() {
    KornerzTheme(darkTheme = false) {
        KornerDetailContent(
            state = KornerDetailState(
                detail = AsyncValue.Success(
                    KornerDetail(
                        korner = Korner(
                            id = "c1",
                            name = "Design Korner",
                            themeColor = "#E27D5A",
                            description = "A place to talk design.",
                            membersCount = 2,
                        ),
                        nooks = listOf(
                            Nook(id = "n1", communityId = "c1", title = "Friday Fireside", startDate = Instant.parse("2026-10-10T19:00:00Z"), isLive = true),
                            Nook(id = "n2", communityId = "c1", title = "Next Week AMA", startDate = Instant.parse("2026-10-17T19:00:00Z")),
                        ),
                        members = listOf(
                            User(id = "u1", email = "a@a.com", displayName = "Ada", nickname = "ada"),
                        ),
                    ),
                ),
                kornerstones = listOf(
                    User(id = "u0", email = "z@z.com", displayName = "Zoe", nickname = "zoe"),
                ),
            ),
            onEvent = {},
        )
    }
}
