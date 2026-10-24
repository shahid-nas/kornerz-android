package com.kornerz.feature.loops.detail

import androidx.lifecycle.SavedStateHandle
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Community
import com.kornerz.core.model.Loop
import com.kornerz.core.model.LoopComment
import com.kornerz.core.model.LoopCommentInfo
import com.kornerz.core.model.LoopDetailInfo
import com.kornerz.core.model.User
import com.kornerz.core.network.gamification.GamificationRepository
import com.kornerz.core.network.loops.CommentLikeRepository
import com.kornerz.core.network.loops.LoopDetailRepository
import com.kornerz.core.network.user.UsersRepository
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.loops.LoopsRoute
import com.kornerz.feature.loops.data.LoopCommentRepository
import com.kornerz.feature.loops.data.LoopLikeRepository
import com.kornerz.feature.loops.data.LoopRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import app.cash.turbine.test
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The loop-detail screen's UDF contract.
 *
 * The three defects this change closes are all asserted here: the like
 * button actually writes (under the **real** signed-in user), the
 * comment engagement is read, and the Korner navigation event carries
 * the **resolved** community.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoopDetailViewModelTest {

    private val loopDetailRepository = mockk<LoopDetailRepository>()
    private val loopRepository = mockk<LoopRepository>()
    private val loopCommentRepository = mockk<LoopCommentRepository>()
    private val loopLikeRepository = mockk<LoopLikeRepository>()
    private val commentLikeRepository = mockk<CommentLikeRepository>()
    private val gamificationRepository = mockk<GamificationRepository>()
    private val sessionRepository = mockk<SessionRepository>()
    private val usersRepository = mockk<UsersRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun session(userCode: String? = "u1"): SessionState =
        if (userCode == null) {
            SessionState.Anonymous
        } else {
            SessionState.Authenticated(
                User(
                    id = "doc-1",
                    email = "a@example.com",
                    displayName = "Ana",
                    nickname = "ana",
                    userCode = userCode,
                ),
            )
        }

    private fun detail(
        loopId: String = "doc-1",
        publisherId: String? = "u1",
        communityId: String? = "doc-c1",
    ): LoopDetailInfo = LoopDetailInfo(
        loop = Loop(
            id = loopId,
            title = "Trip",
            publisherId = publisherId,
            communityId = communityId,
            likesCount = 8,
            commentsCount = 12,
        ),
        owner = User(id = "doc-u1", email = "a@example.com", displayName = "Ana", nickname = "ana"),
        community = Community(id = "doc-c1", communityId = "c1", name = "Brazil Travel"),
    )

    private fun buildViewModel(): LoopDetailViewModel = LoopDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(LoopsRoute.Detail.ARG_LOOP_ID to "l1")),
        loopDetailRepository = loopDetailRepository,
        loopRepository = loopRepository,
        loopCommentRepository = loopCommentRepository,
        loopLikeRepository = loopLikeRepository,
        commentLikeRepository = commentLikeRepository,
        gamificationRepository = gamificationRepository,
        sessionRepository = sessionRepository,
        usersRepository = usersRepository,
    )

    private fun stubHappyPath(detail: LoopDetailInfo = detail()) {
        every { sessionRepository.data } returns flowOf(session())
        coEvery { loopDetailRepository.fetchLoopDetail("l1") } returns Result.success(detail)
        every { loopCommentRepository.observeComments("l1") } returns flowOf(
            Result.success(
                listOf(
                    LoopComment(id = "c1", comment = "Hi", userId = "u1", likesCount = 2, repliesCount = 1),
                ),
            ),
        )
        coEvery { usersRepository.fetchUsersByCodes(listOf("u1")) } returns Result.success(
            mapOf("u1" to User(id = "doc-u1", email = "a@example.com", displayName = "Ana", nickname = "ana")),
        )
        coEvery { commentLikeRepository.fetchLikeCounts(listOf("c1")) } returns Result.success(
            mapOf("c1" to 5),
        )
        coEvery { commentLikeRepository.fetchLikedCommentIds("u1", listOf("c1")) } returns Result.success(
            setOf("c1"),
        )
        coEvery { loopLikeRepository.checkIfUserLiked("u1", "l1") } returns Result.success(false)
        coEvery { gamificationRepository.addPoints(any(), any(), any()) } returns Result.success(Unit)
    }

    @Test
    fun `the load path resolves the detail, the comments and the like state`() = runTest {
        stubHappyPath()

        val viewModel = buildViewModel()

        val detail = viewModel.state.value.detail
        assertTrue(detail is AsyncValue.Success)
        assertEquals("Trip", (detail as AsyncValue.Success).data.loop.title)
        assertEquals("ana", (detail as AsyncValue.Success).data.owner?.nickname)
        assertEquals("Brazil Travel", (detail as AsyncValue.Success).data.community?.name)

        val comments = viewModel.state.value.comments
        assertTrue(comments is AsyncValue.Success)
        val row = (comments as AsyncValue.Success).data.single()
        assertEquals("ana", row.user?.nickname)
        // The live count replaces the denormalized `likesCount` of 2.
        assertEquals(5, row.likesCount)
        assertTrue(row.isLikedByUser)
        assertEquals(1, row.repliesCount)

        assertEquals(8, viewModel.state.value.likesCount)
        assertEquals(12, viewModel.state.value.commentsCount)
    }

    @Test
    fun `a missing loop renders the error state`() = runTest {
        every { sessionRepository.data } returns flowOf(session())
        coEvery { loopDetailRepository.fetchLoopDetail("l1") } returns
            Result.failure(IllegalStateException("not found"))
        every { loopCommentRepository.observeComments("l1") } returns flowOf(Result.success(emptyList()))

        val viewModel = buildViewModel()

        assertTrue(viewModel.state.value.detail is AsyncValue.Error)
    }

    @Test
    fun `the like toggle flips optimistically and reverts on failure`() = runTest {
        stubHappyPath()
        coEvery { loopLikeRepository.likeLoop("u1", "l1") } returns Result.failure(IllegalStateException("offline"))

        val viewModel = buildViewModel()
        viewModel.onEvent(LoopDetailEvent.LikeToggled)

        coVerify(exactly = 1) { loopLikeRepository.likeLoop("u1", "l1") }
        assertFalse(viewModel.state.value.isLikedByUser)
        assertEquals(8, viewModel.state.value.likesCount)
        assertFalse(viewModel.state.value.isLikeInProgress)
    }

    @Test
    fun `the like toggle writes under the signed-in user's code`() = runTest {
        stubHappyPath()
        coEvery { loopLikeRepository.likeLoop("u1", "l1") } returns Result.success(Unit)

        val viewModel = buildViewModel()
        viewModel.onEvent(LoopDetailEvent.LikeToggled)

        coVerify(exactly = 1) { loopLikeRepository.likeLoop("u1", "l1") }
        assertTrue(viewModel.state.value.isLikedByUser)
        assertEquals(9, viewModel.state.value.likesCount)
        assertFalse(viewModel.state.value.isLikeInProgress)
    }

    @Test
    fun `the like toggle is a no-op when signed out`() = runTest {
        stubHappyPath()
        every { sessionRepository.data } returns flowOf(session(userCode = null))
        coEvery { loopLikeRepository.likeLoop(any(), any()) } returns Result.success(Unit)

        val viewModel = buildViewModel()
        viewModel.onEvent(LoopDetailEvent.LikeToggled)

        coVerify(exactly = 0) { loopLikeRepository.likeLoop(any(), any()) }
        assertFalse(viewModel.state.value.isLikedByUser)
        assertEquals(8, viewModel.state.value.likesCount)
    }

    @Test
    fun `the view count is incremented exactly once`() = runTest {
        stubHappyPath()
        coEvery { loopRepository.incrementViewCount(any()) } returns Result.success(Unit)

        val viewModel = buildViewModel()
        viewModel.incrementViewCountOnce()
        viewModel.incrementViewCountOnce()

        coVerify(exactly = 1) { loopRepository.incrementViewCount("doc-1") }
        assertTrue(viewModel.state.value.hasIncrementedViewCount)
    }

    @Test
    fun `opening the loop awards an ACCESS_LOOP point`() = runTest {
        stubHappyPath()
        coEvery { gamificationRepository.addPoints(any(), any(), any()) } returns Result.success(Unit)

        val viewModel = buildViewModel()

        coVerify(exactly = 1) {
            gamificationRepository.addPoints("ACCESS_LOOP", 1, "l1")
        }
    }

    @Test
    fun `opening the video awards an ACCESS_REPLAYS point`() = runTest {
        val videoDetail = detail().copy(
            loop = detail().loop.copy(firebaseVideoUrl = "https://cdn.example.com/v.mp4"),
        )
        stubHappyPath(videoDetail)
        coEvery { gamificationRepository.addPoints(any(), any(), any()) } returns Result.success(Unit)

        val viewModel = buildViewModel()
        viewModel.onEvent(LoopDetailEvent.MediaTapped("l1"))

        coVerify(exactly = 1) {
            gamificationRepository.addPoints("ACCESS_REPLAYS", 1, "https://cdn.example.com/v.mp4")
        }
        assertTrue(viewModel.state.value.isVideoPlayerOpen)
    }

    @Test
    fun `a still-image loop awards no replay point`() = runTest {
        stubHappyPath()
        coEvery { gamificationRepository.addPoints(any(), any(), any()) } returns Result.success(Unit)

        val viewModel = buildViewModel()
        viewModel.onEvent(LoopDetailEvent.MediaTapped("l1"))

        coVerify(exactly = 0) {
            gamificationRepository.addPoints("ACCESS_REPLAYS", any(), any())
        }
        assertFalse(viewModel.state.value.isVideoPlayerOpen)
    }

    @Test
    fun `the comment delete is offered only for the user's own comments`() = runTest {
        stubHappyPath()
        coEvery { loopCommentRepository.deleteComment(any()) } returns Result.success(Unit)

        val viewModel = buildViewModel()
        val own = LoopCommentInfo(
            comment = LoopComment(id = "c1", comment = "Mine", userId = "u1"),
        )
        val other = LoopCommentInfo(
            comment = LoopComment(id = "c2", comment = "Theirs", userId = "u2"),
        )

        assertTrue(viewModel.state.value.canDelete(own))
        assertFalse(viewModel.state.value.canDelete(other))

        viewModel.onEvent(LoopDetailEvent.CommentDeleteConfirmed("c2"))
        coVerify(exactly = 0) { loopCommentRepository.deleteComment("c2") }

        viewModel.onEvent(LoopDetailEvent.CommentDeleteConfirmed("c1"))
        coVerify(exactly = 1) { loopCommentRepository.deleteComment("c1") }
    }

    @Test
    fun `the nav event carries the resolved community identifier`() = runTest {
        stubHappyPath()

        val viewModel = buildViewModel()
        viewModel.navEvents.test {
            viewModel.onEvent(LoopDetailEvent.KornerTapped("c1"))
            val event = awaitItem()
            assertTrue(event is LoopDetailNavEvent.NavigateToKornerDetail)
            assertEquals("doc-c1", (event as LoopDetailNavEvent.NavigateToKornerDetail).communityId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the add-comment event opens the create-comment destination`() = runTest {
        stubHappyPath()

        val viewModel = buildViewModel()
        viewModel.navEvents.test {
            viewModel.onEvent(LoopDetailEvent.AddCommentTapped("l1"))
            val event = awaitItem()
            assertTrue(event is LoopDetailNavEvent.OpenCreateComment)
            assertEquals("l1", (event as LoopDetailNavEvent.OpenCreateComment).loopId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a signed-out session exposes no current user`() = runTest {
        every { sessionRepository.data } returns flowOf(session(userCode = null))
        coEvery { loopDetailRepository.fetchLoopDetail("l1") } returns Result.success(detail())
        every { loopCommentRepository.observeComments("l1") } returns flowOf(Result.success(emptyList()))
        coEvery { commentLikeRepository.fetchLikeCounts(any()) } returns Result.success(emptyMap())
        coEvery { gamificationRepository.addPoints(any(), any(), any()) } returns Result.success(Unit)

        val viewModel = buildViewModel()

        assertNull(viewModel.state.value.currentUserCode)
    }
}
