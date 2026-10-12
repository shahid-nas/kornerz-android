package com.kornerz.feature.notifications

import com.kornerz.core.model.UserNotification
import com.kornerz.feature.notifications.data.NotificationRepository
import com.kornerz.feature.notifications.data.network.NotificationFirestoreApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationRepositoryTest {

    @Test
    fun `fetchNextPage returns page and hasMore=true when full page`() = runTest {
        val api = mockk<NotificationFirestoreApi>()
        val items = (1..10).map {
            UserNotification(
                id = "n$it", title = "t", description = "d", content = "c", userId = "u1",
            )
        }
        coEvery { api.fetchPage("u1", null) } returns Result.success(items to mockk(relaxed = true))

        val repo = NotificationRepository(api)
        val (page, hasMore) = repo.fetchNextPage("u1").getOrThrow()
        assertEquals(10, page.size)
        assertEquals(true, hasMore)
    }

    @Test
    fun `fetchNextPage returns hasMore=false when partial page`() = runTest {
        val api = mockk<NotificationFirestoreApi>()
        val items = (1..3).map {
            UserNotification(
                id = "n$it", title = "t", description = "d", content = "c", userId = "u1",
            )
        }
        coEvery { api.fetchPage("u1", null) } returns Result.success(items to null)

        val repo = NotificationRepository(api)
        val (page, hasMore) = repo.fetchNextPage("u1").getOrThrow()
        assertEquals(3, page.size)
        assertEquals(false, hasMore)
    }

    @Test
    fun `pushDataMessage prepends to recent messages`() = runTest {
        val api = mockk<NotificationFirestoreApi>(relaxed = true)
        val repo = NotificationRepository(api)
        val n1 = UserNotification(id = "a", title = "t", description = "d", content = "c", userId = "u1")
        val n2 = UserNotification(id = "b", title = "t", description = "d", content = "c", userId = "u1")
        repo.pushDataMessage(n1)
        repo.pushDataMessage(n2)
        assertEquals(listOf(n2, n1), repo.recentDataMessages.value)
    }
}
