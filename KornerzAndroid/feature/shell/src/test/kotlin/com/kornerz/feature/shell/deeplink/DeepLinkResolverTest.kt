package com.kornerz.feature.shell.deeplink

import com.kornerz.core.model.DeepLink
import com.kornerz.core.network.community.CommunitiesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The routability matrix.
 *
 * Only the loop detail and the Korner detail are live in the Android
 * port. Event Detail, Post Detail and Forum Detail are excluded
 * (`docs/screen-inventory.md:229-237`), so those links are reported as
 * not routable and the shell surfaces the shared failure alert.
 */
class DeepLinkResolverTest {

    private val communitiesRepository = mockk<CommunitiesRepository>()
    private val resolver = DeepLinkResolver(communitiesRepository)

    @Test
    fun `a loop link resolves to the loop detail`() = runTest {
        val resolution = resolver.resolve(DeepLink.NewLoop(communityId = "c1", loopId = "loop-1"))

        assertEquals(
            DeepLinkResolution.Routable(DeepLinkTarget.LoopDetail(loopId = "loop-1")),
            resolution,
        )
    }

    @Test
    fun `a loop comment link resolves to the loop detail`() = runTest {
        val resolution = resolver.resolve(DeepLink.NewLoopComment(loopId = "loop-1"))

        assertEquals(
            DeepLinkResolution.Routable(DeepLinkTarget.LoopDetail(loopId = "loop-1")),
            resolution,
        )
    }

    @Test
    fun `a call link resolves to the Korner detail through the document id lookup`() = runTest {
        coEvery { communitiesRepository.fetchDocumentIdByCommunityId("community-1") } returns "doc-1"

        val resolution = resolver.resolve(DeepLink.VideoCall(communityId = "community-1"))

        assertEquals(
            DeepLinkResolution.Routable(DeepLinkTarget.KornerDetail(kornerId = "doc-1")),
            resolution,
        )
        coVerify(exactly = 1) {
            communitiesRepository.fetchDocumentIdByCommunityId("community-1")
        }
    }

    @Test
    fun `a call link whose community does not resolve is not routable`() = runTest {
        coEvery { communitiesRepository.fetchDocumentIdByCommunityId("missing") } returns null

        val resolution = resolver.resolve(DeepLink.VideoCall(communityId = "missing"))

        assertEquals(DeepLinkResolution.NotRoutable, resolution)
    }

    @Test
    fun `the excluded targets are not routable`() = runTest {
        assertEquals(
            DeepLinkResolution.NotRoutable,
            resolver.resolve(DeepLink.NewEvent(eventId = "e1", communityId = "c1")),
        )
        assertEquals(
            DeepLinkResolution.NotRoutable,
            resolver.resolve(DeepLink.NewArticle(articleId = "a1", communityId = "c1")),
        )
        assertEquals(
            DeepLinkResolution.NotRoutable,
            resolver.resolve(DeepLink.NewForum(forumId = "f1", communityId = "c1")),
        )
        assertEquals(
            DeepLinkResolution.NotRoutable,
            resolver.resolve(DeepLink.NewComment(forumId = "f1")),
        )
    }
}
