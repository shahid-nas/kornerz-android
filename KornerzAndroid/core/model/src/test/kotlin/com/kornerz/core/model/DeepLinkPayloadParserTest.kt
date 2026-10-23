package com.kornerz.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The **push** deep-link dialect — the payload an FCM message carries.
 *
 * It is deliberately distinct from the in-app notification *document*
 * dialect (`UserNotification.deeplink`): the push payload carries
 * `pushType` and the ids the type needs, the document carries
 * `notificationType` / `notificationReferenceId` /
 * `notificationCommunityId`.
 */
class DeepLinkPayloadParserTest {

    @Test
    fun `parses a NewEvent payload`() {
        val link = DeepLinkPayloadParser.parse(
            mapOf(
                "pushType" to "NewEvent",
                "communityId" to "community-1",
                "eventId" to "event-1",
            ),
        )

        assertEquals(
            DeepLink.NewEvent(eventId = "event-1", communityId = "community-1"),
            link,
        )
    }

    @Test
    fun `parses a NewArticle payload`() {
        val link = DeepLinkPayloadParser.parse(
            mapOf(
                "pushType" to "NewArticle",
                "communityId" to "community-1",
                "articleId" to "article-9",
            ),
        )

        assertEquals(
            DeepLink.NewArticle(articleId = "article-9", communityId = "community-1"),
            link,
        )
    }

    @Test
    fun `parses a NewForum payload`() {
        val link = DeepLinkPayloadParser.parse(
            mapOf(
                "pushType" to "NewForum",
                "communityId" to "community-1",
                "forumId" to "forum-3",
            ),
        )

        assertEquals(
            DeepLink.NewForum(forumId = "forum-3", communityId = "community-1"),
            link,
        )
    }

    @Test
    fun `parses a VideoCall payload`() {
        val link = DeepLinkPayloadParser.parse(
            mapOf("pushType" to "VideoCall", "communityId" to "community-1"),
        )

        assertEquals(DeepLink.VideoCall(communityId = "community-1"), link)
    }

    @Test
    fun `parses a NewComment payload`() {
        val link = DeepLinkPayloadParser.parse(
            mapOf("pushType" to "NewComment", "forumId" to "forum-3"),
        )

        assertEquals(DeepLink.NewComment(forumId = "forum-3"), link)
    }

    @Test
    fun `parses a NewLoop payload`() {
        val link = DeepLinkPayloadParser.parse(
            mapOf(
                "pushType" to "NewLoop",
                "communityId" to "community-1",
                "loopId" to "loop-1",
            ),
        )

        assertEquals(
            DeepLink.NewLoop(communityId = "community-1", loopId = "loop-1"),
            link,
        )
    }

    @Test
    fun `parses a LoopComment payload`() {
        val link = DeepLinkPayloadParser.parse(
            mapOf("pushType" to "LoopComment", "loopId" to "loop-1"),
        )

        assertEquals(DeepLink.NewLoopComment(loopId = "loop-1"), link)
    }

    @Test
    fun `the push type is matched case-insensitively`() {
        assertEquals(
            DeepLink.VideoCall(communityId = "community-1"),
            DeepLinkPayloadParser.parse(
                mapOf("pushType" to "videocall", "communityId" to "community-1"),
            ),
        )
    }

    @Test
    fun `a payload with no push type yields null`() {
        assertNull(DeepLinkPayloadParser.parse(mapOf("communityId" to "community-1")))
        assertNull(DeepLinkPayloadParser.parse(emptyMap()))
    }

    @Test
    fun `a payload with an unknown push type yields null`() {
        assertNull(
            DeepLinkPayloadParser.parse(
                mapOf("pushType" to "SomethingElse", "communityId" to "community-1"),
            ),
        )
    }

    @Test
    fun `a payload missing a required id yields null`() {
        assertNull(
            DeepLinkPayloadParser.parse(mapOf("pushType" to "NewEvent", "communityId" to "community-1")),
        )
        assertNull(
            DeepLinkPayloadParser.parse(mapOf("pushType" to "VideoCall")),
        )
        assertNull(
            DeepLinkPayloadParser.parse(mapOf("pushType" to "LoopComment")),
        )
    }
}
