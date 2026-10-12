package com.kornerz.feature.nooks.list

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Korner
import com.kornerz.core.model.Nook
import com.kornerz.core.model.UpcomingNookItem
import com.kornerz.core.model.User
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.ui.components.header.TabHeaderViewModel
import com.kornerz.feature.nooks.data.NooksRepository
import com.kornerz.feature.nooks.data.NooksUpcomingPage
import com.kornerz.core.network.nooks.NookFirestoreApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.datetime.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NooksViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load populates both sections`() = runTest {
        val repo = mockk<NooksRepository>()
        coEvery { repo.fetchLiveKornerzPage(after = null) } returns flowOf(
            Result.success(
                NookFirestoreApi.KornerPage(
                    items = listOf(sampleKorner("c1")),
                    lastSnapshot = null,
                    hasMore = false,
                ),
            ),
        )
        coEvery { repo.fetchUpcomingPage(after = null, days = 30) } returns flowOf(
            Result.success(
                NooksUpcomingPage(
                    items = listOf(sampleUpcoming("n1", "c1")),
                    lastSnapshot = null,
                    hasMore = false,
                ),
            ),
        )
        val vm = NooksViewModel(repo, mockHeaderVm())
        val state = vm.state.first {
            it.live is AsyncValue.Success && it.upcoming is AsyncValue.Success
        }
        val live = state.live as AsyncValue.Success
        val upcoming = state.upcoming as AsyncValue.Success
        assertEquals(1, live.data.items.size)
        assertEquals("c1", live.data.items.first().id)
        assertEquals(1, upcoming.data.items.size)
        assertEquals("n1", upcoming.data.items.first().nook.id)
    }

    @Test
    fun `card click emits NavigateToNookDetail and live card click emits NavigateToKornerDetail`() = runTest {
        val repo = mockk<NooksRepository>()
        coEvery { repo.fetchLiveKornerzPage(after = null) } returns flowOf(
            Result.success(
                NookFirestoreApi.KornerPage(items = emptyList(), lastSnapshot = null, hasMore = false),
            ),
        )
        coEvery { repo.fetchUpcomingPage(after = null, days = 30) } returns flowOf(
            Result.success(
                NooksUpcomingPage(items = emptyList(), lastSnapshot = null, hasMore = false),
            ),
        )
        val vm = NooksViewModel(repo, mockHeaderVm())
        vm.state.first { it.live is AsyncValue.Success && it.upcoming is AsyncValue.Success }

        vm.onEvent(NooksEvent.CardClicked("n99"))
        assertEquals(NooksNavEvent.NavigateToNookDetail("n99"), vm.navEvents.first())
        vm.onEvent(NooksEvent.LiveCardClicked("c99"))
        assertEquals(NooksNavEvent.NavigateToKornerDetail("c99"), vm.navEvents.first())
    }
}

private fun sampleKorner(id: String) = Korner(id = id, name = "Korner $id", themeColor = "#E27D5A")

private fun mockHeaderVm(): TabHeaderViewModel =
    TabHeaderViewModel(
        mockk<SessionRepository>(relaxed = true),
        mockk<com.kornerz.core.network.gamification.GamificationRepository>(relaxed = true),
    ).also { it.fetchData() }
private fun sampleUpcoming(nookId: String, kornerId: String) = UpcomingNookItem(
    nook = Nook(
        id = nookId,
        communityId = kornerId,
        title = "Nook $nookId",
        startDate = Instant.parse("2026-10-10T19:00:00Z"),
        type = "open",
    ),
    publisher = User(id = "p", email = "p@e.com", displayName = "P", nickname = "p", userCode = "p"),
    parentKorner = Korner(id = kornerId, name = "Korner $kornerId", themeColor = "#E27D5A"),
)
