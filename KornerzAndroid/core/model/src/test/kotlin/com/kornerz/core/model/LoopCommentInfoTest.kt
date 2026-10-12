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
    fun `exposes user and like state when set`() {
        val c = LoopComment(comment = "hi", creationDate = Instant.parse("2025-01-01T00:00:00Z"))
        val user = User(id = "u1", email = "u@x.com", displayName = "User", nickname = "u")
        val info = LoopCommentInfo(comment = c, user = user, isLikedByUser = true, likesCount = 4)
        assertTrue(info.isLikedByUser)
        assertEquals(4, info.likesCount)
        assertEquals(user, info.user)
    }
}
