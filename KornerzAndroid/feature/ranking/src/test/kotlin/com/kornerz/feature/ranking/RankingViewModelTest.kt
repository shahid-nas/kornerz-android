package com.kornerz.feature.ranking.list

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Rank
import com.kornerz.core.model.UserRanking
import com.kornerz.core.network.gamification.GamificationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * The Klouders leaderboard's UDF contract.
 *
 * The leaderboard is capped at the top ten rows — iOS parity, see
 * `RankingViewModel.swift:56` — and the result is exposed as an
 * [AsyncValue] so the screen can pattern-match its three states.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RankingViewModelTest {

    private val gamificationRepository = mockk<GamificationRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the top ten rows on init`() = runTest {
        val rows = List(10) { index ->
            UserRanking(userId = "u$index", userName = "User $index", points = index)
        }
        coEvery { gamificationRepository.getRanking(10) } returns Result.success(rows)

        val viewModel = RankingViewModel(gamificationRepository)

        coVerify(exactly = 1) { gamificationRepository.getRanking(10) }
        val state = viewModel.state.value.rankings
        assertTrue(state is AsyncValue.Success)
        assertEquals(rows, (state as AsyncValue.Success).data)
    }

    @Test
    fun `an empty leaderboard is a success with no rows`() = runTest {
        coEvery { gamificationRepository.getRanking(10) } returns Result.success(emptyList())

        val viewModel = RankingViewModel(gamificationRepository)

        val state = viewModel.state.value.rankings
        assertTrue(state is AsyncValue.Success)
        assertEquals(emptyList<UserRanking>(), (state as AsyncValue.Success).data)
    }

    @Test
    fun `a failed load is an error the screen can retry`() = runTest {
        val failure = IllegalStateException("offline")
        coEvery { gamificationRepository.getRanking(10) } returns Result.failure(failure)

        val viewModel = RankingViewModel(gamificationRepository)

        val state = viewModel.state.value.rankings
        assertTrue(state is AsyncValue.Error)
        assertEquals(failure, (state as AsyncValue.Error).throwable)
    }

    @Test
    fun `retry reloads the leaderboard`() = runTest {
        val rows = listOf(
            UserRanking(userId = "u1", userName = "Ana", points = 320, medal = Rank.GOLD),
        )
        coEvery { gamificationRepository.getRanking(10) } returns Result.success(rows)

        val viewModel = RankingViewModel(gamificationRepository)
        viewModel.loadRankings()

        coVerify(exactly = 2) { gamificationRepository.getRanking(10) }
        assertEquals(rows, (viewModel.state.value.rankings as AsyncValue.Success).data)
    }
}
