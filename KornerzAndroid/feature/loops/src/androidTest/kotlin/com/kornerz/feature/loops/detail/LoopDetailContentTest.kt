package com.kornerz.feature.loops.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Community
import com.kornerz.core.model.Loop
import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.model.LoopDetailInfo
import com.kornerz.core.model.User
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.feature.loops.components.LoopAddCommentBar
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.Clock
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The loop detail screen's Compose UI cases.
 *
 * **These are skeletons on purpose.** CI provisions no emulator — it
 * builds `assembleDevDebug` only — so the cases are written out and
 * `@Ignore`d rather than left unwritten. They run the moment an
 * emulator is available; nothing about them has to be re-derived.
 */
@RunWith(AndroidJUnit4::class)
class LoopDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(state: LoopDetailState) {
        composeRule.setContent {
            KornerzTheme(darkTheme = false) {
                Scaffold(
                    bottomBar = {
                        LoopAddCommentBar(
                            avatarUrl = null,
                            onClick = {},
                        )
                    },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        LoopDetailContent(
                            state = state,
                            onEvent = {},
                        )
                    }
                }
            }
        }
    }

    private fun loadedState(
        comments: AsyncValue<List<LoopCommentInfo>> = AsyncValue.Success(emptyList()),
    ) = LoopDetailState(
        detail = AsyncValue.Success(
            LoopDetailInfo(
                loop = Loop(
                    id = "l1",
                    title = "How engines work",
                    viewCounts = 1024,
                    commentsCount = 12,
                    likesCount = 8,
                    creationDate = Clock.System.now(),
                ),
                owner = User(id = "u1", email = "u@x.com", displayName = "Ana", nickname = "ana"),
                community = Community(id = "c1", communityId = "c1", name = "Brazil Travel"),
            ),
        ),
        comments = comments,
        likesCount = 8,
        commentsCount = 12,
    )

    @Ignore("Skeleton — CI provisions no emulator; run with an instrumented device.")
    @Test
    fun theTitleRowShowsTheNicknameTheCommunityAndTheTimestamp() {
        setContent(loadedState())

        composeRule.onNodeWithText("ana").assertExists()
        composeRule.onNodeWithText("in").assertExists()
        composeRule.onNodeWithText("Brazil Travel").assertExists()
        composeRule.onNodeWithText("ago", substring = true).assertExists()
    }

    @Ignore("Skeleton — CI provisions no emulator; run with an instrumented device.")
    @Test
    fun theStatsRowShowsTheThreeCounts() {
        setContent(loadedState())

        composeRule.onNodeWithText("1024").assertExists()
        composeRule.onNodeWithText("12").assertExists()
        composeRule.onNodeWithText("8").assertExists()
    }

    @Ignore("Skeleton — CI provisions no emulator; run with an instrumented device.")
    @Test
    fun anEmptyCommentListShowsTheEmptyState() {
        setContent(loadedState())

        composeRule.onNodeWithText("No comments yet. Be the first!").assertExists()
    }

    @Ignore("Skeleton — CI provisions no emulator; run with an instrumented device.")
    @Test
    fun theAddCommentBarStaysVisibleWhileTheContentScrolls() {
        setContent(loadedState())

        composeRule.onNodeWithText("How engines work").performScrollTo()
        composeRule.onNodeWithText("Leave a comment").assertIsDisplayed()
    }
}
