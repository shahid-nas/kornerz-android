package com.kornerz.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.datetime.Instant

class LoopTest {

    @Test
    fun `default Loop has all nulls except id-keyed constructor`() {
        val loop = Loop()
        assertNull(loop.id)
        assertNull(loop.title)
        assertNull(loop.firebaseVideoUrl)
        assertFalse(loop.isVideo)
        assertEquals("#000000", loop.videoColorHex)
    }

    @Test
    fun `thumbnailUrl falls back to firebaseImageUrl when firebaseThumbnailUrl is null`() {
        val loop = Loop(firebaseImageUrl = "https://cdn.example.com/i.jpg")
        assertEquals("https://cdn.example.com/i.jpg", loop.iOsThumbnailUrl)
    }

    @Test
    fun `thumbnailUrl uses thumbnailHash when both Firebase URLs are null`() {
        val loop = Loop(thumbnailHash = "QmThumb")
        assertEquals(Loop.IPFS_BASE_URL + "QmThumb", loop.iOsThumbnailUrl)
    }

    @Test
    fun `thumbnailUrl uses imageHash when thumbnailHash missing`() {
        val loop = Loop(imageHash = "QmImg")
        assertEquals(Loop.IPFS_BASE_URL + "QmImg", loop.iOsThumbnailUrl)
    }

    @Test
    fun `thumbnailUrl returns null when nothing is set`() {
        assertNull(Loop().iOsThumbnailUrl)
    }

    @Test
    fun `imageUrl uses IPFS imageHash fallback`() {
        val loop = Loop(imageHash = "QmImg")
        assertEquals(Loop.IPFS_BASE_URL + "QmImg", loop.iOsImageUrl)
    }

    @Test
    fun `videoUrl uses IPFS videoHash fallback`() {
        val loop = Loop(videoHash = "QmVid")
        assertEquals(Loop.IPFS_BASE_URL + "QmVid", loop.iOsVideoUrl)
    }

    @Test
    fun `isVideo is true only when firebaseVideoUrl non-empty`() {
        assertTrue(Loop(firebaseVideoUrl = "https://x/v.mp4").isVideo)
        assertFalse(Loop().isVideo)
    }

    @Test
    fun `videoColorHex is orange for video, black otherwise`() {
        assertEquals("#FFA500", Loop(firebaseVideoUrl = "https://x/v.mp4").videoColorHex)
        assertEquals("#000000", Loop().videoColorHex)
    }

    @Test
    fun `creationDate can be parsed from Instant parseable string`() {
        val loop = Loop(creationDate = Instant.parse("2025-01-01T00:00:00Z"))
        assertNotNull(loop.creationDate)
    }
}
