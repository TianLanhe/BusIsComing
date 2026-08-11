package com.golink.busiscoming

import com.golink.busiscoming.ui.main.HeadingRotation
import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingRotationTest {
    @Test
    fun rotatesAcrossNorthUsingTheShortestClockwiseArc() {
        assertEquals(2f, HeadingRotation.shortestDelta(359f, 1f), 0.0001f)
    }

    @Test
    fun rotatesAcrossNorthUsingTheShortestCounterClockwiseArc() {
        assertEquals(-2f, HeadingRotation.shortestDelta(1f, 359f), 0.0001f)
    }

    @Test
    fun normalizesUnboundedRenderedRotationBeforeFindingNextTarget() {
        assertEquals(-20f, HeadingRotation.shortestDelta(370f, 350f), 0.0001f)
        assertEquals(361f, HeadingRotation.shortestTarget(359f, 1f), 0.0001f)
    }
}
