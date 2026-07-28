package com.golink.busiscoming

import com.golink.busiscoming.ui.navigation.RouteQueryGeneration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteQueryGenerationTest {
    @Test
    fun `starting a newer query invalidates callbacks from the prior query`() {
        val generation = RouteQueryGeneration()
        val first = generation.begin()
        val second = generation.begin()

        assertFalse(generation.isCurrent(first))
        assertTrue(generation.isCurrent(second))
    }

    @Test
    fun `invalidating a view rejects its active callback`() {
        val generation = RouteQueryGeneration()
        val active = generation.begin()
        generation.invalidate()

        assertFalse(generation.isCurrent(active))
    }

    @Test
    fun `new generation after invalidation accepts only the new callback`() {
        val generation = RouteQueryGeneration()
        val stale = generation.begin()
        generation.invalidate()
        val active = generation.begin()

        assertFalse(generation.isCurrent(stale))
        assertTrue(generation.isCurrent(active))
    }
}
