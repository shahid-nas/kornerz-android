package com.kornerz.feature.shell.deeplink

import com.kornerz.core.model.DeepLink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deep-link manager's queueing contract.
 *
 * A cold start delivers the intent to `MainActivity` long before the
 * shell's `NavHost` exists, so the link has to be held until the shell
 * says it is ready.
 */
class DeepLinkManagerTest {

    @Test
    fun `a link handled before the app is ready is queued and not released`() = runTest {
        val manager = DeepLinkManager()

        manager.handle(DeepLink.NewLoopComment(loopId = "loop-1"))

        assertTrue(manager.hasPending())
        assertEquals(DeepLink.NewLoopComment(loopId = "loop-1"), manager.takeNext())
        assertFalse(manager.hasPending())
    }

    @Test
    fun `notifyAppLaunch releases the queued link`() = runTest {
        val manager = DeepLinkManager()
        manager.handle(DeepLink.NewLoopComment(loopId = "loop-1"))

        manager.notifyAppLaunch()

        assertEquals(DeepLink.NewLoopComment(loopId = "loop-1"), manager.releasedLinks.first())
    }

    @Test
    fun `the queue is released in order`() = runTest {
        val manager = DeepLinkManager()
        manager.handle(DeepLink.NewLoopComment(loopId = "loop-1"))
        manager.handle(DeepLink.NewLoopComment(loopId = "loop-2"))
        manager.handle(DeepLink.NewLoopComment(loopId = "loop-3"))

        manager.notifyAppLaunch()

        val released = buildList {
            repeat(3) { add(manager.releasedLinks.first()) }
        }
        assertEquals(
            listOf("loop-1", "loop-2", "loop-3"),
            released.map { (it as DeepLink.NewLoopComment).loopId },
        )
    }

    @Test
    fun `a second link does not overwrite the first`() = runTest {
        val manager = DeepLinkManager()
        manager.handle(DeepLink.NewLoopComment(loopId = "loop-1"))
        manager.handle(DeepLink.NewLoopComment(loopId = "loop-2"))

        assertEquals(DeepLink.NewLoopComment(loopId = "loop-1"), manager.takeNext())
        assertEquals(DeepLink.NewLoopComment(loopId = "loop-2"), manager.takeNext())
        assertNull(manager.takeNext())
    }

    @Test
    fun `a link handled after the app is ready is released immediately`() = runTest {
        val manager = DeepLinkManager()
        manager.notifyAppLaunch()

        manager.handle(DeepLink.NewLoopComment(loopId = "loop-1"))

        assertEquals(DeepLink.NewLoopComment(loopId = "loop-1"), manager.releasedLinks.first())
    }

    @Test
    fun `taking from an empty queue yields null`() {
        val manager = DeepLinkManager()

        assertNull(manager.takeNext())
        assertFalse(manager.hasPending())
    }
}
