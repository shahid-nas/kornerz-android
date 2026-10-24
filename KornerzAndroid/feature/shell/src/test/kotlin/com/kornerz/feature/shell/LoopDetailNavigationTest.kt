package com.kornerz.feature.shell

import com.kornerz.feature.loops.LoopsRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The loop-detail bottom-bar rule.
 *
 * The loop detail lives inside the Loops tab's nested `NavHost`, so the
 * shell cannot see its route on the root controller — the destination
 * carries a `hideBottomBar` nav argument instead, and the shell lifts
 * it up to the root `Scaffold`.
 */
class LoopDetailNavigationTest {

    @Test
    fun `the loop detail route carries the hide-bottom-bar flag`() {
        assertEquals(
            "loops/l1?hideBottomBar=false",
            LoopsRoute.Detail.build("l1"),
        )
        assertEquals(
            "loops/l1?hideBottomBar=true",
            LoopsRoute.Detail.build("l1", hideBottomBar = true),
        )
    }

    @Test
    fun `the pending target carries the loop id and the flag`() {
        assertEquals(
            LoopDetailTarget(loopId = "l1", hideBottomBar = false),
            LoopDetailTarget("l1"),
        )
        assertTrue(LoopDetailTarget("l1", hideBottomBar = true).hideBottomBar)
    }

    @Test
    fun `the bottom bar stays visible for the loops tab's own list`() {
        assertTrue(
            shouldShowBottomBar(
                currentRoute = ShellTab.Loops.route,
                hidesBottomBarForLoopDetail = false,
            ),
        )
    }

    @Test
    fun `the bottom bar is hidden for a loop detail that asked for it`() {
        assertFalse(
            shouldShowBottomBar(
                currentRoute = ShellTab.Loops.route,
                hidesBottomBarForLoopDetail = true,
            ),
        )
    }

    @Test
    fun `the bottom bar is hidden for the full-screen routes`() {
        listOf(
            "videocall/session-1",
            "notifications/list",
            "profile/view",
            "community/admin/list",
            "subscription/plans",
            "deeplink/resolving",
        ).forEach { route ->
            assertFalse(
                "route $route must hide the bottom bar",
                shouldShowBottomBar(
                    currentRoute = route,
                    hidesBottomBarForLoopDetail = false,
                ),
            )
        }
    }

    @Test
    fun `the Home tab's loop detail hides the bottom bar`() {
        assertTrue(
            hidesBottomBarForLoopDetail(
                currentRoute = ShellTab.Home.route,
                homeHides = true,
                loopsHides = false,
            ),
        )
        assertFalse(
            shouldShowBottomBar(
                currentRoute = ShellTab.Home.route,
                hidesBottomBarForLoopDetail = hidesBottomBarForLoopDetail(
                    currentRoute = ShellTab.Home.route,
                    homeHides = true,
                    loopsHides = false,
                ),
            ),
        )
    }

    @Test
    fun `only the active tab's flag counts`() {
        // A flag left behind by the Loops tab must not hide the bar on
        // the Home tab the user switched to.
        assertFalse(
            hidesBottomBarForLoopDetail(
                currentRoute = ShellTab.Home.route,
                homeHides = false,
                loopsHides = true,
            ),
        )
        assertFalse(
            hidesBottomBarForLoopDetail(
                currentRoute = ShellTab.Kornerz.route,
                homeHides = true,
                loopsHides = true,
            ),
        )
        assertTrue(
            hidesBottomBarForLoopDetail(
                currentRoute = ShellTab.Loops.route,
                homeHides = false,
                loopsHides = true,
            ),
        )
    }
}
