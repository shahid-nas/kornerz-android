package com.kornerz.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LoopDetailInfoTest {

    @Test
    fun `carries the loop, owner and community`() {
        val loop = Loop(id = "l1", title = "Trip")
        val owner = User(id = "u1", email = "u@x.com", displayName = "User", nickname = "u")
        val community = Community(id = "c1", communityId = "c1", name = "Brazil Travel")

        val info = LoopDetailInfo(loop = loop, owner = owner, community = community)

        assertEquals(loop, info.loop)
        assertEquals(owner, info.owner)
        assertEquals(community, info.community)
    }

    @Test
    fun `an unresolved owner or community is representable`() {
        val info = LoopDetailInfo(loop = Loop(id = "l1"))
        assertNull(info.owner)
        assertNull(info.community)
    }

    @Test
    fun `the accent colour is a hex string, not a compose color`() {
        val info = LoopDetailInfo(loop = Loop(id = "l1"))
        assertTrue(
            "accentColorHex must match the #RRGGBB shape, was ${info.accentColorHex}",
            info.accentColorHex.matches(Regex("^#[0-9A-Fa-f]{6}$")),
        )
    }

    @Test
    fun `the class carries no compose import`() {
        val source = File("src/main/kotlin/com/kornerz/core/model/LoopDetailInfo.kt").readText()
        assertFalse(
            "core/model must not import Compose",
            source.contains("androidx.compose"),
        )
        assertFalse(
            "core/model must not import android.* types",
            source.contains("import android."),
        )
    }

    @Test
    fun `the palette cycles through the five ios colours in order`() {
        assertEquals(
            listOf("#E27D5A", "#AD598D", "#86BFA9", "#68B6CC", "#EBAA4A"),
            (0 until LoopAccentPalette.SIZE).map { LoopAccentPalette.hexForIndex(it) },
        )
    }

    @Test
    fun `the palette wraps past the last index`() {
        assertEquals(LoopAccentPalette.hexForIndex(0), LoopAccentPalette.hexForIndex(5))
        assertEquals(LoopAccentPalette.hexForIndex(1), LoopAccentPalette.hexForIndex(6))
    }

    @Test
    fun `the palette wraps for a negative index`() {
        assertEquals(LoopAccentPalette.hexForIndex(4), LoopAccentPalette.hexForIndex(-1))
        assertEquals(LoopAccentPalette.hexForIndex(3), LoopAccentPalette.hexForIndex(-2))
    }

    @Test
    fun `the accent colour is stable for the same loop id`() {
        assertEquals(
            LoopAccentPalette.hexForLoopId("l1"),
            LoopAccentPalette.hexForLoopId("l1"),
        )
        assertEquals(
            LoopDetailInfo(loop = Loop(id = "l1")).accentColorHex,
            LoopDetailInfo(loop = Loop(id = "l1")).accentColorHex,
        )
    }

    @Test
    fun `five consecutive loop ids resolve to the five palette entries in order`() {
        val ids = listOf("l1", "l2", "l3", "l4", "l5")
        assertEquals(
            listOf("#E27D5A", "#AD598D", "#86BFA9", "#68B6CC", "#EBAA4A"),
            ids.map { LoopAccentPalette.hexForLoopId(it) },
        )
    }

    @Test
    fun `a loop id without an ordinal still resolves deterministically`() {
        val uuid = "3f2a9c1e-77b4-4a55-9b0f-1c2d3e4f5a6b"
        assertEquals(
            LoopAccentPalette.hexForLoopId(uuid),
            LoopAccentPalette.hexForLoopId(uuid),
        )
        assertTrue(
            LoopAccentPalette.hexForLoopId(uuid) in LoopAccentPalette.hexColors,
        )
    }
}
