package com.kornerz.feature.kornerz.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Korner
import com.kornerz.core.model.KornerDetail
import com.kornerz.core.model.Nook
import com.kornerz.core.model.User
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.feature.kornerz.about.AboutSection
import com.kornerz.feature.kornerz.current_nook.CurrentNookSection
import com.kornerz.feature.kornerz.members.MembersSection
import com.kornerz.feature.kornerz.nooks_list.NooksListSection
import kotlinx.datetime.Instant

/**
 * Stateless renderer for the Korner detail screen. Composes the
 * header banner + the four optional sections (About, Current Nook,
 * Nooks list, Members) into a scrollable column. Wrapped by
 * `KornerDetailScreen`, which handles the loading/error/full layout
 * via the wrapped [AsyncValue] pattern (architecture §2.2).
 */
@Composable
fun KornerDetailContent(
    detail: KornerDetail,
    onEvent: (KornerDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        KornerDetailHeader(
            korner = detail.korner,
            onJoinClick = { onEvent(KornerDetailEvent.JoinCtaClicked) },
        )
        detail.korner.description
            ?.takeIf { it.isNotBlank() }
            ?.let { AboutSection(description = it) }
        detail.nooks.firstOrNull { it.isLive }
            ?.let { CurrentNookSection(nook = it, onNookClick = { id -> onEvent(KornerDetailEvent.NookTileClicked(id)) }) }
        val scheduled = detail.nooks.filterNot { it.isLive }
        if (scheduled.isNotEmpty()) {
            NooksListSection(nooks = scheduled, onNookClick = { id -> onEvent(KornerDetailEvent.NookTileClicked(id)) })
        }
        if (detail.members.isNotEmpty()) {
            MembersSection(members = detail.members, onMemberClick = { id -> onEvent(KornerDetailEvent.MemberTileClicked(id)) })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun KornerDetailContentPreview() {
    KornerzTheme(darkTheme = false) {
        KornerDetailContent(
            detail = KornerDetail(
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
            onEvent = {},
        )
    }
}

