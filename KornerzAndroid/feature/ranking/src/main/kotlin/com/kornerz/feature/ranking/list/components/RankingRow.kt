package com.kornerz.feature.ranking.list.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.Rank
import com.kornerz.core.model.UserRanking
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.ranking.R

/**
 * The leaderboard's header row and its rows.
 *
 * Mirrors iOS `RankingScreen.swift`
 * (`kornerz/Kornerz/HomeVC/Ranking/RankingScreen.swift:32-71`):
 *
 * ```
 * HStack {
 *     madalView(ranking, index: index)   // 75-pt slot, 40×40 medal or numeral
 *     Text(ranking.userName).bold()
 *     Spacer()
 *     Text("\(ranking.points)").frame(width: 75)
 * }
 * ```
 *
 * The three columns are fixed the same way: the leading slot is 50 dp
 * wide, the trailing points slot is 75 dp wide, the name column takes
 * what is left, and the columns are separated by an 8 dp gap.
 */

/**
 * The width of the leading slot (medal or rank numeral) — 50 dp, so the
 * 40 dp medal sits closer to the `#` than iOS's 75-pt slot does.
 */
val RankingLeadingSlotWidth = 50.dp

/** The width of the trailing points slot — iOS `.frame(width: 75)`. */
val RankingTrailingSlotWidth = 75.dp

/** The size of the medal image and of the rank-numeral box — iOS `.frame(width: 40, height: 40)`. */
val RankingMedalSize = 40.dp

/**
 * The leaderboard's header row: `#`, `Name`, `Points` in three columns.
 *
 * The outer two columns are fixed width and the middle column is
 * flexible — iOS's `HStack { Text("#").frame(width: 75); Text("Name");
 * Spacer(); Text("Points").frame(width: 75) }`.
 *
 * Each label is aligned with the column it titles: `#` is **centred** in
 * the leading slot — SwiftUI's `.frame(width: 75)` centres its content,
 * and the medal / rank numeral below it is centred in the same slot, so
 * the icon reads as directly under the `#` — and `Points` is
 * **end-aligned** in the trailing slot, matching the right-aligned
 * points values of the rows below.
 *
 * The row uses the same column gap as [RankingRow], so the header labels
 * stay in their columns.
 */
@JvmOverloads
@Composable
fun RankingHeaderRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(RankingLeadingSlotWidth),
        )
        Text(
            text = "Name",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Points",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(RankingTrailingSlotWidth),
        )
    }
}

/**
 * One leaderboard row.
 *
 * @param ranking the row's data
 * @param position the row's 1-based position in the leaderboard
 */
@Composable
fun RankingRow(
    ranking: UserRanking,
    position: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = rankingRowDescription(
                position = position,
                name = ranking.userName,
                points = ranking.points,
            )
        },
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankingMedalSlot(
            rank = ranking.medal,
            position = position,
            modifier = Modifier.width(RankingLeadingSlotWidth),
        )
        Text(
            text = ranking.userName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = ranking.points.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.End,
            modifier = Modifier.width(RankingTrailingSlotWidth),
        )
    }
}

/**
 * The leading 75-dp slot: a 40 × 40 dp medal for the podium rows, and
 * the 1-based rank numeral — 16 pt, bold, in the secondary content
 * colour — for every row after them.
 *
 * The medal's content description is `null`: the row is exposed as a
 * **single** accessibility node (see [RankingRow]), so the medal must
 * not contribute a node of its own.
 */
@Composable
private fun RankingMedalSlot(
    rank: Rank,
    position: Int,
    modifier: Modifier = Modifier,
) {
    val medal = rank.medalDrawable()
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (medal != null) {
            Image(
                painter = painterResource(medal),
                contentDescription = null,
                modifier = Modifier.size(RankingMedalSize),
            )
        } else {
            // The numeral's box is width-fixed but **height-driven**: a
            // fixed height would clip the text when the system font
            // scale is increased (`app-polish` spec — no text container
            // on a P10 screen has a fixed height that clips its
            // content).
            Box(
                modifier = Modifier.width(RankingMedalSize),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = position.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The medal drawable for a rank tier, or `null` for [Rank.NONE] — the
 * tier that renders the rank numeral instead.
 *
 * The assets are the iOS `medal-gold` / `medal-silver` / `medal-bronze`
 * images, ported to Android vector drawables (P10 task 5.5).
 */
@DrawableRes
private fun Rank.medalDrawable(): Int? = when (this) {
    Rank.GOLD -> R.drawable.medal_gold
    Rank.SILVER -> R.drawable.medal_silver
    Rank.BRONZE -> R.drawable.medal_bronze
    Rank.NONE -> null
}

/**
 * The spoken description of a leaderboard row: the position, the user's
 * name and the points value, together.
 *
 * The row is one accessibility node, so the description has to carry
 * all three facts — TalkBack would otherwise read the numeral, the
 * name and the points as disconnected fragments.
 */
fun rankingRowDescription(position: Int, name: String, points: Int): String =
    "$position. $name, $points points"

@Preview(showBackground = true)
@Composable
private fun RankingHeaderRowPreview() {
    KornerzTheme(darkTheme = false) {
        RankingHeaderRow(modifier = Modifier.width(320.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun RankingPodiumRowPreview() {
    KornerzTheme(darkTheme = false) {
        RankingRow(
            ranking = UserRanking(
                userId = "user-1",
                userName = "Ana",
                points = 320,
                medal = Rank.GOLD,
            ),
            position = 1,
            modifier = Modifier.width(320.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RankingNonPodiumRowPreview() {
    KornerzTheme(darkTheme = false) {
        RankingRow(
            ranking = UserRanking(
                userId = "user-7",
                userName = "Bo",
                points = 12,
                medal = Rank.NONE,
            ),
            position = 7,
            modifier = Modifier.width(320.dp),
        )
    }
}
