package com.kornerz.core.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopCommentInfoTest {

    @Test
    fun `defaults to not-liked and zero likes`() {
        val c = LoopComment(comment = "hi")
        val info = LoopCommentInfo(comment = c)
        assertFalse(info.isLikedByUser)
        assertEquals(0, info.likesCount)
        assertNull(info.user)
    }

    @Test
    fun `defaults to zero replies`() {
        val info = LoopCommentInfo(comment = LoopComment(comment = "hi"))
        assertEquals(0, info.repliesCount)
    }

    @Test
    fun `exposes user and like state when set`() {
        val c = LoopComment(comment = "hi", creationDate = Instant.parse("2025-01-01T00:00:00Z"))
        val user = User(id = "u1", email = "u@x.com", displayName = "User", nickname = "u")
        val info = LoopCommentInfo(comment = c, user = user, isLikedByUser = true, likesCount = 4)
        assertTrue(info.isLikedByUser)
        assertEquals(4, info.likesCount)
        assertEquals(user, info.user)
    }

    @Test
    fun `exposes the reply count when set`() {
        val info = LoopCommentInfo(comment = LoopComment(comment = "hi"), repliesCount = 7)
        assertEquals(7, info.repliesCount)
    }

    @Test
    fun `the live like count replaces the denormalized comment count`() {
        val comment = LoopComment(comment = "hi", likesCount = 2)
        val liveCount = 5

        val info = LoopCommentInfo(comment = comment, likesCount = liveCount)

        assertEquals(2, info.comment.likesCount)
        assertEquals(liveCount, info.likesCount)
    }
}
