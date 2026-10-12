package com.kornerz.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentLikeTest {

    @Test
    fun `required userId and commentId cannot be omitted`() {
        val like = CommentLike(userId = "u1", commentId = "c1")
        assertEquals("u1", like.userId)
        assertEquals("c1", like.commentId)
    }
}
