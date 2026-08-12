package com.golink.busiscoming

import com.golink.busiscoming.ui.main.RouteTimelineAnchorBounds
import com.golink.busiscoming.ui.main.RouteTimelineAnchorGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteTimelineAnchorGeometryTest {
    @Test
    fun nodeIndicatorUsesExactRailCenter() {
        val point = RouteTimelineAnchorGeometry.atNode(
            RouteTimelineAnchorBounds(8f, 20f, 48f, 72f)
        )

        assertEquals(28f, point.x, 0f)
        assertEquals(46f, point.y, 0f)
    }

    @Test
    fun betweenNodesUsesFixedVisualMidpoint() {
        val point = RouteTimelineAnchorGeometry.betweenNodes(
            RouteTimelineAnchorBounds(8f, 20f, 48f, 72f),
            RouteTimelineAnchorBounds(8f, 220f, 48f, 272f)
        )

        assertEquals(28f, point.x, 0f)
        assertEquals(146f, point.y, 0f)
    }

    @Test
    fun walkingUsesVisibleRailHeightAndClampedProgress() {
        val bounds = RouteTimelineAnchorBounds(8f, 100f, 48f, 300f)

        assertEquals(150f, RouteTimelineAnchorGeometry.walking(bounds, 0.25).y, 0f)
        assertEquals(300f, RouteTimelineAnchorGeometry.walking(bounds, 2.0).y, 0f)
    }
}
