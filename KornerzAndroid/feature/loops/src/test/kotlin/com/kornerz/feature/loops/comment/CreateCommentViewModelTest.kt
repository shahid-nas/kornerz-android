package com.kornerz.feature.loops.comment

import androidx.lifecycle.SavedStateHandle
import com.kornerz.core.model.LoopComment
import com.kornerz.core.network.gamification.GamificationRepository
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.loops.LoopsRoute
import com.kornerz.feature.loops.data.LoopCommentRepository
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The create-comment sheet's contract: the comment is written under the
 * signed-in user's `codeUser`, and a successful creation awards the
 * `ACCESS_COMMENT` Klouders point without blocking the dismiss.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateCommentViewModelTest {

    private val loopCommentRepository = mockk<LoopCommentRepository>()
    private val gamificationRepository = mockk<GamificationRepository>()
    private val sessionRepository = mockk<SessionRepository>()
    private val loopDetailRepository = mockk<com.kornerz.core.network.loops.LoopDetailRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { sessionRepository.data } returns flowOf(SessionState.Loading)
        coEvery { loopDetailRepository.fetchLoopDetail(any()) } returns Result.failure(Exception("nope"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun session(userCode: String?): SessionState =
        if (userCode == null) {
            SessionState.Anonymous
        } else {
            SessionState.Authenticated(
                com.kornerz.core.model.User(
                    id = "doc-1",
                    email = "a@example.com",
                    displayName = "Ana",
                    nickname = "ana",
                    userCode = userCode,
                ),
            )
        }

    private fun buildViewModel(): CreateCommentViewModel = CreateCommentViewModel(
        savedStateHandle = SavedStateHandle(mapOf(LoopsRoute.Comment.ARG_LOOP_ID to "l1")),
        loopCommentRepository = loopCommentRepository,
        gamificationRepository = gamificationRepository,
        sessionRepository = sessionRepository,
        loopDetailRepository = loopDetailRepository,
    )

    @Test
    fun `the comment is written under the signed-in user's code`() = runTest {
        every { sessionRepository.data } returns flowOf(session("u1"))
        coEvery {
            loopCommentRepository.createComment(loopId = "l1", parentId = null, text = "Hi", userId = "u1")
        } returns Result.success(LoopComment(id = "c1", comment = "Hi", userId = "u1"))
        coEvery { gamificationRepository.addPoints(any(), any(), any()) } returns Result.success(Unit)

        val viewModel = buildViewModel()
        viewModel.onEvent(CreateCommentEvent.TextChanged("Hi"))
        viewModel.onEvent(CreateCommentEvent.SendTapped)

        coVerify(exactly = 1) {
            loopCommentRepository.createComment(loopId = "l1", parentId = null, text = "Hi", userId = "u1")
        }
    }

    @Test
    fun `a successful comment awards an ACCESS_COMMENT point`() = runTest {
        every { sessionRepository.data } returns flowOf(session("u1"))
        coEvery {
            loopCommentRepository.createComment(any(), any(), any(), any())
        } returns Result.success(LoopComment(id = "c1", comment = "Hi", userId = "u1"))
        coEvery { gamificationRepository.addPoints(any(), any(), any()) } returns Result.success(Unit)

        val viewModel = buildViewModel()
        viewModel.onEvent(CreateCommentEvent.TextChanged("Hi"))
        viewModel.onEvent(CreateCommentEvent.SendTapped)

        coVerify(exactly = 1) {
            gamificationRepository.addPoints("ACCESS_COMMENT", 3, "l1")
        }
    }

    @Test
    fun `a failed award does not fail the comment`() = runTest {
        every { sessionRepository.data } returns flowOf(session("u1"))
        coEvery {
            loopCommentRepository.createComment(any(), any(), any(), any())
        } returns Result.success(LoopComment(id = "c1", comment = "Hi", userId = "u1"))
        coEvery { gamificationRepository.addPoints(any(), any(), any()) } returns
            Result.failure(IllegalStateException("offline"))

        val viewModel = buildViewModel()
        viewModel.onEvent(CreateCommentEvent.TextChanged("Hi"))
        viewModel.onEvent(CreateCommentEvent.SendTapped)

        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `a signed-out session cannot comment`() = runTest {
        every { sessionRepository.data } returns flowOf(session(null))
        coEvery {
            loopCommentRepository.createComment(any(), any(), any(), any())
        } returns Result.success(LoopComment(id = "c1"))

        val viewModel = buildViewModel()
        viewModel.onEvent(CreateCommentEvent.TextChanged("Hi"))
        viewModel.onEvent(CreateCommentEvent.SendTapped)

        coVerify(exactly = 0) {
            loopCommentRepository.createComment(any(), any(), any(), any())
        }
        assertTrue(viewModel.state.value.error != null)
    }
}
