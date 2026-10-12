package com.kornerz.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LoopLikeTest {

    @Test
    fun `required userId and loopId cannot be omitted`() {
        val like = LoopLike(userId = "u1", loopId = "l1")
        assertEquals("u1", like.userId)
        assertEquals("l1", like.loopId)
    }
}
