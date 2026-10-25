package com.kornerz.feature.kornerz.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Community
import com.kornerz.core.model.Korner
import com.kornerz.core.model.KornerDetail
import com.kornerz.core.model.Nook
import com.kornerz.core.model.User
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
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
 * [SectionGap] spacers reproduce it.
 */
@Composable
fun KornerDetailContent(
    state: KornerDetailState,
    onEvent: (KornerDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = (state.detail as? AsyncValue.Success)?.data ?: return
    val korner = detail.korner
    val themeColor = parseKornerThemeColor(korner.themeColor)

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        KornerDetailHeader(
            korner = korner,
            isFollowing = korner.isJoined,
            onBackClick = { onEvent(KornerDetailEvent.BackClicked) },
            onFollowClick = { onEvent(KornerDetailEvent.FollowClicked) },
            onAboutClick = { onEvent(KornerDetailEvent.AboutClicked) },
        )
        SectionGap()
        KornerDetailNooksSection(
            nooks = detail.nooks,
            selectedTab = state.selectedNookTab,
            onTabSelected = { onEvent(KornerDetailEvent.NookTabSelected(it)) },
            themeColor = themeColor,
            onNookClick = { onEvent(KornerDetailEvent.NookTileClicked(it)) },
            onJoinClick = { onEvent(KornerDetailEvent.JoinCtaClicked(it)) },
        )
        SectionGap()
        KornerDetailUsersSection(
            kornerstones = state.kornerstones,
            klouders = detail.members,
            selectedTab = state.selectedUserTab,
            onTabSelected = { onEvent(KornerDetailEvent.UserTabSelected(it)) },
            onUserClick = { onEvent(KornerDetailEvent.MemberTileClicked(it)) },
        )
        SectionGap()
        KornerDetailRecentSection(
            communityName = korner.name,
            cards = recentCards,
            onLoopClick = { loopId, accentColorHex ->
                onEvent(KornerDetailEvent.LoopTileClicked(loopId, accentColorHex))
            },
        )
    }
}

/**
 * The vertical gap between the screen's blocks.
 *
 * iOS stacks the hero, the Nooks section, the users section and the
 * "Recently in" block in a `LazyVStack` with no explicit spacing, so
 * the platform's default 8-pt gap separates them. The Android port
 * stacks the blocks in a plain `Column`, which has no such default,
 * so the gap is added explicitly here — the same 8 dp the Home tab
 * uses between its sections (`HomeContent`'s
 * `Arrangement.spacedBy(Spacing.space4)`).
 */
@Composable
private fun SectionGap() {
    Spacer(modifier = Modifier.height(Spacing.space4))
}

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
