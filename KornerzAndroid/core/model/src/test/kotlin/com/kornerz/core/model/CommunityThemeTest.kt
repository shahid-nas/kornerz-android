package com.kornerz.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CommunityTheme.fromRawValue] and the
 * [Community.userIsAdmin] / [Community.coverImageURL] derived
 * properties (P7, task 10.1).
 */
class CommunityThemeTest {

    @Test
    fun `themeId 1 maps to RED`() {
        assertEquals(CommunityTheme.RED, CommunityTheme.fromRawValue(1))
    }

    @Test
    fun `themeId 2 maps to BLUE`() {
        assertEquals(CommunityTheme.BLUE, CommunityTheme.fromRawValue(2))
    }

    @Test
    fun `themeId 3 maps to YELLOW`() {
        assertEquals(CommunityTheme.YELLOW, CommunityTheme.fromRawValue(3))
    }

    @Test
    fun `themeId 4 maps to PURPLE`() {
        assertEquals(CommunityTheme.PURPLE, CommunityTheme.fromRawValue(4))
    }

    @Test
    fun `themeId 5 maps to GREEN`() {
        assertEquals(CommunityTheme.GREEN, CommunityTheme.fromRawValue(5))
    }

    @Test
    fun `themeId 0 maps to DEFAULT`() {
        assertEquals(CommunityTheme.DEFAULT, CommunityTheme.fromRawValue(0))
    }

    @Test
    fun `unknown themeId falls back to DEFAULT`() {
        assertEquals(CommunityTheme.DEFAULT, CommunityTheme.fromRawValue(99))
    }

    @Test
    fun `community userIsAdmin returns true when userCode matches createdByUserId`() {
        val community = Community(code = "ABC", name = "K-pop", createdByUserId = "user-1")
        assertTrue(community.userIsAdmin("user-1"))
    }

    @Test
    fun `community userIsAdmin returns false when userCode differs`() {
        val community = Community(code = "ABC", name = "K-pop", createdByUserId = "user-1")
        assertFalse(community.userIsAdmin("user-2"))
    }

    @Test
    fun `community userIsAdmin returns false for empty userCode`() {
        val community = Community(code = "ABC", name = "K-pop", createdByUserId = "user-1")
        assertFalse(community.userIsAdmin(""))
    }

    @Test
    fun `coverImageURL prefers firebaseImageURL over imageURL`() {
        val community = Community(
            firebaseImageURL = "https://firebase/cover.jpg",
            imageURL = "https://ipfs/Qm...",
        )
        assertEquals("https://firebase/cover.jpg", community.coverImageURL)
    }

    @Test
    fun `coverImageURL falls back to imageURL when firebaseImageURL is null`() {
        val community = Community(imageURL = "https://ipfs/Qm...")
        assertEquals("https://ipfs/Qm...", community.coverImageURL)
    }

    @Test
    fun `coverImageURL returns null when both are null`() {
        val community = Community()
        assertEquals(null, community.coverImageURL)
    }
}
