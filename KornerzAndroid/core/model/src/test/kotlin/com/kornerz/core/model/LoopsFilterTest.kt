package com.kornerz.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LoopsFilterTest {

    @Test
    fun `exposes All and Your Loops display names`() {
        assertEquals("All", LoopsFilter.ALL.displayName)
        assertEquals("Your Loops", LoopsFilter.YOUR_LOOPS.displayName)
    }

    @Test
    fun `has exactly two cases`() {
        assertEquals(2, LoopsFilter.entries.size)
    }
}
