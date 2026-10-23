package com.kornerz.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `UserNotification.deeplink(with:)` — the in-app **document** dialect.
 *
 * iOS's per-case field requirements are honoured exactly, and iOS's
 * `newComment → videoCall` bug is deliberately **not** ported.
 */
class UserNotificationDeepLinkTest {

    /**
     * The model carries the **raw** type string; `deeplinkType` is the
     * parsed view of it.
     */
    private fun notification(
        type: String,
        reference: String? = "ref-1",
        communityId: String? = "community-1",
    ) = UserNotification(
        id = "n1",
        title = "Title",
        description = "Description",
        content = "Content",
        image = null,
        date = null,
        reference = reference,
        type = type,
        userId = "user-1",
        communityId = communityId,
    )

    private val community = Community(
        id = "community-1",
        code = "ABC123",
        communityId = "community-1",
        name = "Design",
    )

    @Test
    fun `newEvent resolves to an event link`() {
        assertEquals(
            DeepLink.NewEvent(eventId = "ref-1", communityId = "community-1"),
            notification("NewEvent").deeplink(community),
        )
    }

    @Test
    fun `newArticle resolves to an article link`() {
        assertEquals(
            DeepLink.NewArticle(articleId = "ref-1", communityId = "community-1"),
            notification("NewArticle").deeplink(community),
        )
    }

    @Test
    fun `newForum resolves to a forum link`() {
        assertEquals(
            DeepLink.NewForum(forumId = "ref-1", communityId = "community-1"),
            notification("NewForum").deeplink(community),
        )
    }

    @Test
    fun `videoCall resolves to a call link`() {
        assertEquals(
            DeepLink.VideoCall(communityId = "community-1"),
            notification("VideoCall", reference = null).deeplink(community),
        )
    }

    @Test
    fun `newComment resolves to a forum comment link and not to a video call`() {
        // iOS `Models/UserNotification.swift:65-67` returns
        // `.videoCall(...)` for `.newComment`; that branch is
        // unreachable in iOS and is NOT ported.
        assertEquals(
            DeepLink.NewComment(forumId = "ref-1"),
            notification("NewComment").deeplink(community),
        )
    }

    @Test
    fun `newLoop resolves to a loop link`() {
        assertEquals(
            DeepLink.NewLoop(communityId = "community-1", loopId = "ref-1"),
            notification("NewLoop").deeplink(community),
        )
    }

    @Test
    fun `loopComment needs no community`() {
        assertEquals(
            DeepLink.NewLoopComment(loopId = "ref-1"),
            notification("LoopComment", communityId = null)
                .deeplink(null),
        )
    }

    @Test
    fun `a missing reference yields null`() {
        assertNull(
            notification("NewEvent", reference = null).deeplink(community),
        )
        assertNull(
            notification("NewComment", reference = null).deeplink(community),
        )
    }

    @Test
    fun `a missing community yields null for the cases that need one`() {
        assertNull(notification("NewEvent").deeplink(null))
        assertNull(notification("NewArticle").deeplink(null))
        assertNull(notification("NewForum").deeplink(null))
        assertNull(notification("VideoCall").deeplink(null))
        assertNull(notification("NewLoop").deeplink(null))
    }

    @Test
    fun `the community id falls back to the legacy id`() {
        val legacyCommunity = Community(id = "legacy-1", code = "ABC123")

        assertEquals(
            DeepLink.NewEvent(eventId = "ref-1", communityId = "legacy-1"),
            notification("NewEvent").deeplink(legacyCommunity),
        )
    }

    @Test
    fun `an unknown type yields null`() {
        assertNull(notification("UNKNOWN").deeplink(community))
    }
}
