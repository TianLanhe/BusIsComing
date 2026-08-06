package com.golink.busiscoming

import com.golink.busiscoming.ui.main.RouteMapLabelCandidate
import com.golink.busiscoming.ui.main.RouteMapLabelPlacementPolicy
import com.golink.busiscoming.ui.main.RouteMapLabelRect
import com.golink.busiscoming.ui.main.RouteMapLabelSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteMapLabelPlacementTest {
    private val safe = RouteMapLabelRect(0f, 0f, 300f, 500f)

    @Test
    fun `keeps previous side while it remains collision free`() {
        val candidates = candidates()

        val chosen = RouteMapLabelPlacementPolicy.choose(
            candidates,
            safe,
            emptyList(),
            critical = true,
            previousSide = RouteMapLabelSide.TOP
        )

        assertEquals(RouteMapLabelSide.TOP, chosen?.side)
    }

    @Test
    fun `tries right left top bottom and hides ordinary stop when all collide`() {
        val candidates = candidates()
        val occupied = candidates.map { it.rect }

        assertNull(
            RouteMapLabelPlacementPolicy.choose(
                candidates,
                safe,
                occupied,
                critical = false,
                previousSide = null
            )
        )
    }

    @Test
    fun `critical stop chooses lowest conflict fallback`() {
        val candidates = candidates()
        val occupied = listOf(
            RouteMapLabelRect(50f, 90f, 130f, 120f),
            RouteMapLabelRect(90f, 55f, 120f, 95f)
        )

        val chosen = RouteMapLabelPlacementPolicy.choose(
            candidates,
            safe,
            occupied,
            critical = true,
            previousSide = null
        )

        assertEquals(RouteMapLabelSide.BOTTOM, chosen?.side)
    }

    private fun candidates() = listOf(
        RouteMapLabelCandidate(RouteMapLabelSide.RIGHT, RouteMapLabelRect(110f, 90f, 190f, 120f)),
        RouteMapLabelCandidate(RouteMapLabelSide.LEFT, RouteMapLabelRect(10f, 90f, 90f, 120f)),
        RouteMapLabelCandidate(RouteMapLabelSide.TOP, RouteMapLabelRect(70f, 50f, 130f, 90f)),
        RouteMapLabelCandidate(RouteMapLabelSide.BOTTOM, RouteMapLabelRect(70f, 120f, 130f, 160f))
    )
}
