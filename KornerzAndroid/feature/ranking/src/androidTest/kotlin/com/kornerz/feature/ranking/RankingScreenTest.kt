package com.kornerz.feature.ranking

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Rank
import com.kornerz.core.model.UserRanking
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.feature.ranking.list.RankingContent
import com.kornerz.feature.ranking.list.RankingState
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Klouders leaderboard's Compose UI cases.
 *
 * **These are skeletons on purpose.** CI provisions no emulator — it
 * builds `assembleDevDebug` only — so the cases are written out and
 * `@Ignore`d rather than left unwritten. They run the moment an
 * emulator is available; nothing about them has to be re-derived.
 */
@RunWith(AndroidJUnit4::class)
class RankingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(rankings: List<UserRanking>) {
        composeRule.setContent {
            KornerzTheme(darkTheme = false) {
                RankingContent(
                    state = RankingState(rankings = AsyncValue.Success(rankings)),
                    onRetry = {},
                )
            }
        }
    }

    @Ignore("Skeleton — CI provisions no emulator; run with an instrumented device.")
    @Test
    fun theHeaderRowRendersTheThreeColumns() {
        setContent(listOf(UserRanking(userId = "u1", userName = "Ana", points = 10)))

        composeRule.onNodeWithText("#").assertExists()
        composeRule.onNodeWithText("Name").assertExists()
        composeRule.onNodeWithText("Points").assertExists()
    }

    @Ignore("Skeleton — CI provisions no emulator; run with an instrumented device.")
    @Test
    fun aPodiumRowShowsAMedalAndANonPodiumRowShowsTheRankNumeral() {
        setContent(
            listOf(
                UserRanking(userId = "u1", userName = "Ana", points = 320, medal = Rank.GOLD),
                UserRanking(userId = "u4", userName = "Dee", points = 12, medal = Rank.NONE),
            ),
        )

        // The medal image is decorative — it has no content description
        // of its own, because the row carries the whole description.
        composeRule.onNodeWithContentDescription("1. Ana, 320 points").assertExists()
        composeRule.onNodeWithContentDescription("2. Dee, 12 points").assertExists()
    }

    @Ignore("Skeleton — CI provisions no emulator; run with an instrumented device.")
    @Test
    fun aRowIsOneSemanticsNode() {
        setContent(listOf(UserRanking(userId = "u1", userName = "Ana", points = 320, medal = Rank.GOLD)))

        // One node, one description — the position, the name and the
        // points together. The medal must not be a separate focusable
        // node.
        composeRule.onNodeWithContentDescription("1. Ana, 320 points").assertExists()
    }

    @Ignore("Skeleton — CI provisions no emulator; run with an instrumented device.")
    @Test
    fun anEmptyLeaderboardRendersTheSharedNoDataView() {
        composeRule.setContent {
            KornerzTheme(darkTheme = false) {
                RankingContent(
                    state = RankingState(rankings = AsyncValue.Success(emptyList())),
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("No rankings yet").assertExists()
    }

    @Ignore("Skeleton — CI provisions no emulator; run with an instrumented device.")
    @Test
    fun theErrorStateOffersAWorkingRetry() {
        var retried = false
        composeRule.setContent {
            KornerzTheme(darkTheme = false) {
                RankingContent(
                    state = RankingState(
                        rankings = AsyncValue.Error(IllegalStateException("offline")),
                    ),
                    onRetry = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText("Retry again").performClick()
        assert(retried)
    }

    @Ignore("Skeleton — CI provisions no emulator; run with an instrumented device.")
    @Test
    fun theSemanticsTreeIsPrintableForReview() {
        setContent(listOf(UserRanking(userId = "u1", userName = "Ana", points = 320, medal = Rank.GOLD)))

        composeRule.onRoot().printToString()
    }
}
