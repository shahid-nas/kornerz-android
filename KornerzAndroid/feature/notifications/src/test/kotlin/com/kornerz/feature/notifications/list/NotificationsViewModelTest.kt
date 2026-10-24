package com.kornerz.feature.notifications.list

import com.kornerz.core.model.Community
import com.kornerz.core.model.DeepLink
import com.kornerz.core.model.User
import com.kornerz.core.model.UserNotification
import com.kornerz.core.network.community.CommunitiesRepository
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.notifications.data.NotificationRepository
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

/**
 * The notification list's navigation contract.
 *
 * Before P10 the `Open` event carried a `String` — the raw URI — and
 * the shell showed a "Deeplink coming in Phase 10" placeholder. The
 * event now carries a typed [DeepLink], which the shell resolves
 * through the one `DeepLinkResolver`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private val notificationRepository = mockk<NotificationRepository>()
    private val sessionRepository = mockk<SessionRepository>()
    private val communitiesRepository = mockk<CommunitiesRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { sessionRepository.data } returns flowOf(
            SessionState.Authenticated(
                User(
                    id = "user-1",
                    email = "a@example.com",
                    displayName = "Ana",
                    nickname = "Ana",
                ),
            ),
        )
        coEvery { notificationRepository.reset() } returns Unit
        coEvery { notificationRepository.fetchNextPage("user-1") } returns
            Result.success(emptyList<UserNotification>() to false)
        coEvery { communitiesRepository.fetchByName(any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun notification(
        type: String,
        reference: String? = "ref-1",
        communityId: String? = "community-1",
    ) = UserNotification(
        id = "n1",
        title = "Title",
        description = "Description",
        content = "Design",
        image = null,
        date = null,
        reference = reference,
        type = type,
        userId = "user-1",
        communityId = communityId,
    )

    private fun viewModel() = NotificationsViewModel(
        notificationRepository = notificationRepository,
        sessionRepository = sessionRepository,
        communitiesRepository = communitiesRepository,
    )

    @Test
    fun `the Open event carries a typed deep link`() = runTest {
        coEvery { communitiesRepository.fetchById("community-1") } returns Community(
            id = "community-1",
            code = "ABC123",
            communityId = "community-1",
            name = "Design",
        )

        val viewModel = viewModel()
        viewModel.onEvent(
            NotificationsEvent.NotificationOpened(notification(type = "VideoCall", reference = null)),
        )

        assertEquals(
            NotificationsNavEvent.Open(DeepLink.VideoCall(communityId = "community-1")),
            viewModel.navEvents.first(),
        )
    }

    @Test
    fun `a loop notification routes to the loop comment event`() = runTest {
        val viewModel = viewModel()
        viewModel.onEvent(
            NotificationsEvent.NotificationOpened(notification(type = "NewLoop")),
        )

        assertEquals(
            NotificationsNavEvent.OpenLoopComment(loopId = "ref-1"),
            viewModel.navEvents.first(),
        )
    }

    @Test
    fun `a comment notification routes to the forum comment event`() = runTest {
        val viewModel = viewModel()
        viewModel.onEvent(
            NotificationsEvent.NotificationOpened(notification(type = "NewComment")),
        )

        assertEquals(
            NotificationsNavEvent.OpenForumComment(forumId = "ref-1"),
            viewModel.navEvents.first(),
        )
    }

    @Test
    fun `a community that does not resolve surfaces the shared alert`() = runTest {
        coEvery { communitiesRepository.fetchById("community-1") } returns null

        val viewModel = viewModel()
        viewModel.onEvent(
            NotificationsEvent.NotificationOpened(notification(type = "VideoCall", reference = null)),
        )

        assertEquals(
            NotificationsNavEvent.ShowAlert("Community not found"),
            viewModel.navEvents.first(),
        )
    }
}
