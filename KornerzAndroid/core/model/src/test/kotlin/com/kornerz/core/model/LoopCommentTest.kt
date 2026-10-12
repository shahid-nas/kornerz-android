package com.kornerz.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopCommentTest {

    private val json = Json

    @Test
    fun `parentId is encoded as null when missing`() {
        val c = LoopComment(comment = "hi")
        val s = json.encodeToString(c)
        assertTrue("expected null in encoded JSON, got: $s", s.contains("\"parentId\":null"))
    }

    @Test
    fun `parentId is encoded as string when present`() {
        val c = LoopComment(parentId = "p1", comment = "hi")
        val s = json.encodeToString(c)
        assertTrue("expected parentId value, got: $s", s.contains("\"parentId\":\"p1\""))
    }

    @Test
    fun `comment can be deserialized from JSON`() {
        val raw = """
            {"id":"c1","loopId":"l1","parentId":null,"userId":"u1",
             "active":true,"comment":"hello","creationDate":null,
             "repliesCount":0,"likesCount":2}
        """.trimIndent()
        val c = json.decodeFromString<LoopComment>(raw)
        assertEquals("c1", c.id)
        assertNull(c.parentId)
        assertEquals("hello", c.comment)
    }
}
