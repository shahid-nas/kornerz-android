package com.kornerz.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `communityId` field is a **different value** from the document
 * id and from [Community.id] — iOS resolves a community by the field
 * (`APICommunity.swift:475`), and the P10 deep-link resolver bridges
 * the field to the document id.
 */
class CommunityTest {

    @Test
    fun `communityId defaults to null so no construction site changes`() {
        assertNull(Community().communityId)
        assertNull(Community(id = "doc-1", code = "ABC123").communityId)
    }

    @Test
    fun `communityId is carried alongside id and code`() {
        val community = Community(
            id = "community-1",
            code = "ABC123",
            communityId = "community-1",
            name = "Design",
        )

        assertEquals("community-1", community.communityId)
        // `id` and `code` keep their old meaning.
        assertEquals("community-1", community.id)
        assertEquals("ABC123", community.code)
    }
}
