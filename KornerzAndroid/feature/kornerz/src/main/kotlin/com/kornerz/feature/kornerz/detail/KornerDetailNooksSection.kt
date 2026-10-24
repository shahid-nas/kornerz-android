package com.kornerz.feature.kornerz.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.Nook
import com.kornerz.core.ui.components.KornerzMiniCapsuleButton
import com.kornerz.core.ui.components.KornerzTriangleImage
import com.kornerz.core.ui.components.TriangleCorner
import com.kornerz.core.ui.components.header.KornerzSectionTabHeader
import com.kornerz.core.ui.components.nooks.formatNookStartDate
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import kotlinx.datetime.Instant

/**
 * The "Nooks" section of the Korner detail screen. 1:1 port of iOS
 * `SwiftUI/Dashboard/Korner/Sections/KornerDetailNooksView.swift`.
 *
 * iOS layout:
 * ```
 * VStack {
 *     TabHeaderView(image: nook-icon, title: "nooks", selectedTab: $selectedNookTab)
 *     if selectedNookTab == .current {
 *         HStack { KornerCurrentNookView(...).frame(width: itemWidth(), height: 100); Spacer() }
 *             .padding(.horizontal)
 *     } else {
 *         if upCommingNooks.isEmpty { emptyView }
 *         else {
 *             ScrollView(.horizontal) {
 *                 LazyHStack { ForEach(upCommingNooks) { KornerNookView(...).frame(width: itemWidth(), height: 100) } }
 *                     .padding(.horizontal)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * The screen used to render **two** stacked sections on Android — a
 * "Current Nook" card and a vertical "Nooks" list. iOS renders a
 * single tabbed section whose "Upcoming" tab is a horizontal
 * carousel, so the Android port now does the same: one tab strip, a
 * single live card under "Current", and a 70%-wide carousel under
 * "Upcoming".
 *
 * Both cards are painted in the **Korner's theme colour** (iOS
 * `viewModel.color`), not in the per-index accent palette the Nooks
 * tab uses.
 */
@Composable
fun KornerDetailNooksSection(
    nooks: List<Nook>,
    selectedTab: KornerDetailNookTab,
    onTabSelected: (KornerDetailNookTab) -> Unit,
    themeColor: Color,
    onNookClick: (String) -> Unit,
    onJoinClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        KornerzSectionTabHeader(
            title = "Nooks",
            icon = Icons.Filled.Groups,
            tabs = KornerDetailNookTab.entries.map { it.title },
            selectedTabIndex = KornerDetailNookTab.entries.indexOf(selectedTab),
            onTabSelected = { index -> onTabSelected(KornerDetailNookTab.entries[index]) },
        )
        Spacer(modifier = Modifier.height(Spacing.space4))
        when (selectedTab) {
            KornerDetailNookTab.Current -> {
                val liveNook = nooks.firstOrNull { it.isLive }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.space8),
                ) {
                    KornerCurrentNookCard(
                        // iOS always renders the card, even when the
                        // Korner has no live session — it then shows
                        // the "no user in session" message.
                        nook = liveNook,
                        color = themeColor,
                        onJoin = { onJoinClick(liveNook?.id.orEmpty()) },
                        modifier = Modifier
                            .width(kornerDetailItemWidth())
                            .height(KORNER_DETAIL_CARD_HEIGHT),
                    )
                }
            }
            KornerDetailNookTab.Upcoming -> {
                val upcoming = nooks.filterNot { it.isLive }
                if (upcoming.isEmpty()) {
                    Text(
                        text = "No upcoming nooks",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
                    )
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = Spacing.space8),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
                    ) {
                        items(upcoming, key = { it.id }) { nook ->
                            KornerNookCard(
                                nook = nook,
                                color = themeColor,
                                onJoin = { onJoinClick(nook.id) },
                                onClick = { onNookClick(nook.id) },
                                modifier = Modifier
                                    .width(kornerDetailItemWidth())
                                    .height(KORNER_DETAIL_CARD_HEIGHT),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The live-nook card. 1:1 port of iOS `KornerCurrentNookView`
 * (`SwiftUI/Dashboard/Korner/Sections/KornerCurrentNookView.swift`):
 * a rounded 12-dp block in the theme colour with the session title on
 * top, the Join capsule and the "spots left" counter underneath.
 */
@Composable
private fun KornerCurrentNookCard(
    nook: Nook?,
    color: Color,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = nook?.title ?: "No user in session",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = Color.White,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (nook?.isLive == true) {
                LiveBadge()
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KornerzMiniCapsuleButton(text = "Join", onClick = onJoin)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "9 spots left",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = Color.White,
            )
        }
    }
}

/**
 * The upcoming-nook card. 1:1 port of iOS `KornerNookView`
 * (`SwiftUI/Dashboard/Korner/Sections/KornerNookView.swift`): the
 * publisher thumbnail and the Join capsule on the leading edge, the
 * start date, the title and the session line on the trailing edge.
 *
 * Note the card has **no** parent-Korner cover column — that column
 * belongs to the Nooks tab's `ScheduledNookView`, not to the Korner
 * detail's card.
 */
@Composable
private fun KornerNookCard(
    nook: Nook,
    color: Color,
    onJoin: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KornerzTriangleImage(
                imageUrl = nook.coverUrl,
                size = 55.dp,
                cornerRadius = 8.dp,
                corner = TriangleCorner.BottomLeft,
            )
            Spacer(modifier = Modifier.height(Spacing.space2))
            KornerzMiniCapsuleButton(text = "Join", onClick = onJoin)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = formatNookStartDate(nook.startDate),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = Color.White,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (nook.isLive) {
                    LiveBadge()
                }
            }
            Text(
                text = nook.title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = Color.White,
                maxLines = 2,
            )
            Text(
                text = "No user in session",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

/**
 * The "LIVE" pill — iOS renders it as black 10-sp bold text on a
 * white capsule.
 */
@Composable
private fun LiveBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Color.Black,
        )
    }
}

/**
 * The width of a nook card — iOS `itemWidth()` returns
 * `AppHelper.shared.getScreenSize().width * 0.7`, i.e. 70% of the
 * screen width.
 */
@Composable
private fun kornerDetailItemWidth(): Dp =
    (LocalConfiguration.current.screenWidthDp * 0.7f).dp

private val KORNER_DETAIL_CARD_HEIGHT = 100.dp

@Preview(showBackground = true, name = "Korner detail nooks — current")
@Composable
private fun KornerDetailNooksSectionCurrentPreview() {
    KornerzTheme(darkTheme = false) {
        KornerDetailNooksSection(
            nooks = listOf(
                Nook(
                    id = "n1",
                    communityId = "c1",
                    title = "Friday Fireside",
                    startDate = Instant.parse("2026-10-10T19:00:00Z"),
                    isLive = true,
                ),
            ),
            selectedTab = KornerDetailNookTab.Current,
            onTabSelected = {},
            themeColor = parseKornerThemeColor("#E27D5A"),
            onNookClick = {},
            onJoinClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Korner detail nooks — upcoming")
@Composable
private fun KornerDetailNooksSectionUpcomingPreview() {
    KornerzTheme(darkTheme = false) {
        KornerDetailNooksSection(
            nooks = listOf(
                Nook(
                    id = "n1",
                    communityId = "c1",
                    title = "Friday Fireside",
                    startDate = Instant.parse("2026-10-10T19:00:00Z"),
                    isLive = true,
                ),
                Nook(id = "n2", communityId = "c1", title = "Next Week AMA", startDate = Instant.parse("2026-10-17T19:00:00Z")),
                Nook(id = "n3", communityId = "c1", title = "Design critique", startDate = Instant.parse("2026-10-24T19:00:00Z")),
            ),
            selectedTab = KornerDetailNookTab.Upcoming,
            onTabSelected = {},
            themeColor = parseKornerThemeColor("#E27D5A"),
            onNookClick = {},
            onJoinClick = {},
        )
    }
}
