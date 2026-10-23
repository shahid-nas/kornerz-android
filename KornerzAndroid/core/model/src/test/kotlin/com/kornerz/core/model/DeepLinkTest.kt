package com.kornerz.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `kornerz://` deep-link grammar.
 *
 * The grammar is the transport form of the [DeepLink] model — iOS
 * passes the enum around in memory, Android has to carry it through an
 * `Intent`, so [DeepLink.toUri] and [DeepLink.parse] are the two ends
 * of that wire and must agree.
 */
class DeepLinkTest {

    @Test
    fun `NewEvent round-trips through its URI`() {
        val link = DeepLink.NewEvent(eventId = "event-1", communityId = "community-1")

        assertEquals("kornerz://community/community-1/event/event-1", link.toUri())
        assertEquals(link, DeepLink.parse(link.toUri()))
    }

    @Test
    fun `NewArticle round-trips through its URI`() {
        val link = DeepLink.NewArticle(articleId = "article-9", communityId = "community-1")

        assertEquals("kornerz://community/community-1/article/article-9", link.toUri())
        assertEquals(link, DeepLink.parse(link.toUri()))
    }

    @Test
    fun `NewForum round-trips through its URI`() {
        val link = DeepLink.NewForum(forumId = "forum-3", communityId = "community-1")

        assertEquals("kornerz://community/community-1/forum/forum-3", link.toUri())
        assertEquals(link, DeepLink.parse(link.toUri()))
    }

    @Test
    fun `VideoCall round-trips through its URI`() {
        val link = DeepLink.VideoCall(communityId = "community-1")

        assertEquals("kornerz://community/community-1/call", link.toUri())
        assertEquals(link, DeepLink.parse(link.toUri()))
    }

    @Test
    fun `NewComment round-trips through its URI`() {
        val link = DeepLink.NewComment(forumId = "forum-3")

        assertEquals("kornerz://forum/forum-3", link.toUri())
        assertEquals(link, DeepLink.parse(link.toUri()))
    }

    @Test
    fun `NewLoopComment round-trips through its URI`() {
        val link = DeepLink.NewLoopComment(loopId = "loop-1")

        assertEquals("kornerz://loop/loop-1", link.toUri())
        assertEquals(link, DeepLink.parse(link.toUri()))
    }

    @Test
    fun `NewLoop collapses onto the loop URI and keeps its loop id`() {
        val link = DeepLink.NewLoop(communityId = "community-1", loopId = "loop-1")

        assertEquals("kornerz://loop/loop-1", link.toUri())

        // The grammar carries one path segment, so the community id
        // cannot survive the round-trip. That loss is documented and
        // harmless: both loop cases resolve to the same destination
        // (the loop detail).
        val parsed = DeepLink.parse(link.toUri())
        assertEquals(DeepLink.NewLoopComment(loopId = "loop-1"), parsed)
    }

    @Test
    fun `an unparseable URI yields null`() {
        assertNull(DeepLink.parse("not a uri"))
        assertNull(DeepLink.parse("https://example.com/loop/loop-1"))
        assertNull(DeepLink.parse("kornerz://unknown/loop-1"))
        assertNull(DeepLink.parse("kornerz://community/community-1/unknown"))
        assertNull(DeepLink.parse("kornerz://community/community-1"))
        assertNull(DeepLink.parse("kornerz://loop/loop-1/extra"))
        assertNull(DeepLink.parse(""))
    }

    @Test
    fun `the scheme is kornerz`() {
        assertEquals("kornerz", DeepLink.SCHEME)
    }

    @Test
    fun `a percent-encoded id survives the round-trip`() {
        val link = DeepLink.NewComment(forumId = "forum with space")

        assertEquals(link, DeepLink.parse(link.toUri()))
    }
}
