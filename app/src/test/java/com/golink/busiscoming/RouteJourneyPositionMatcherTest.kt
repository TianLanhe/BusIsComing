package com.golink.busiscoming

import com.golink.busiscoming.data.location.JourneyLocationFix
import com.golink.busiscoming.data.location.RouteJourneyPositionMatcher
import com.golink.busiscoming.data.location.RouteJourneyPositionStabilizer
import com.golink.busiscoming.data.location.RouteJourneyAxisBuilder
import com.golink.busiscoming.data.model.JourneyPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteJourneyPositionMatcherTest {
    private val now = 100_000L
    private val axis = RouteJourneyAxisBuilder().build(RouteJourneyFixtures.input())
    private val matcher = RouteJourneyPositionMatcher()

    @Test
    fun matchesFreshAccurateFixToUniqueStationNode() {
        val result = matcher.match(
            axis,
            fix(longitudeOffset = 0.0020, accuracy = 12f),
            now
        )

        assertTrue(result is JourneyPosition.AtNode)
        assertEquals("R1 中途站", (result as JourneyPosition.AtNode).nodeLabel)
    }

    @Test
    fun rejectsStaleOrLowAccuracyFixImmediately() {
        val stale = matcher.match(
            axis,
            fix(longitudeOffset = 0.0020, accuracy = 12f, elapsedRealtimeMillis = now - 20_001L),
            now
        )
        val inaccurate = matcher.match(axis, fix(longitudeOffset = 0.0020, accuracy = 75.1f), now)

        assertSame(JourneyPosition.Unreliable, stale)
        assertSame(JourneyPosition.Unreliable, inaccurate)
    }

    @Test
    fun usesAccuracyAwareOffAxisDistanceThreshold() {
        val aboutThirtyFiveMetersNorth = RouteJourneyFixtures.BASE_LATITUDE + 0.000315
        val rejected = matcher.match(
            axis,
            fix(latitude = aboutThirtyFiveMetersNorth, longitudeOffset = 0.0015, accuracy = 20f),
            now
        )
        val accepted = matcher.match(
            axis,
            fix(latitude = aboutThirtyFiveMetersNorth, longitudeOffset = 0.0015, accuracy = 40f),
            now
        )

        assertSame(JourneyPosition.Unreliable, rejected)
        assertTrue(accepted is JourneyPosition.BetweenNodes)
    }

    @Test
    fun matchesBusFixBetweenAdjacentStopsWithoutDistanceProgress() {
        val result = matcher.match(axis, fix(longitudeOffset = 0.0015, accuracy = 8f), now)

        assertTrue(result is JourneyPosition.BetweenNodes)
        result as JourneyPosition.BetweenNodes
        assertEquals("R1 上車站", result.fromLabel)
        assertEquals("R1 中途站", result.toLabel)
        assertEquals(0, result.fromStopIndex)
        assertEquals(2, result.stopEdgeCount)
    }

    @Test
    fun calculatesWalkingProgressFromActualSubpathsOnly() {
        val walkingAxis = RouteJourneyAxisBuilder().build(
            RouteJourneyFixtures.input(
                walkingSegments = mapOf(
                    "origin" to RouteJourneyFixtures.walkingRoute(0.0 to 0.0004, 0.0007 to 0.0010),
                    "destination" to RouteJourneyFixtures.walkingRoute(0.0030 to 0.0040)
                )
            )
        )

        val result = matcher.match(walkingAxis, fix(longitudeOffset = 0.00085, accuracy = 5f), now)

        assertTrue(result is JourneyPosition.WalkingProgress)
        assertEquals(0.75, (result as JourneyPosition.WalkingProgress).progress, 0.06)
    }

    @Test
    fun doesNotMatchSyntheticGapBetweenWalkingSubpaths() {
        val walkingAxis = RouteJourneyAxisBuilder().build(
            RouteJourneyFixtures.input(
                walkingSegments = mapOf(
                    "origin" to RouteJourneyFixtures.walkingRoute(0.0 to 0.0003, 0.0008 to 0.0010),
                    "destination" to RouteJourneyFixtures.walkingRoute(0.0030 to 0.0040)
                )
            )
        )

        val result = matcher.match(walkingAxis, fix(longitudeOffset = 0.00055, accuracy = 10f), now)

        assertSame(JourneyPosition.Unreliable, result)
    }

    @Test
    fun stabilizerRequiresTwoFreshFixesForNonAdjacentJumpAndNeverShowsOldPosition() {
        val stabilizer = RouteJourneyPositionStabilizer()
        val firstRegion = matcher.match(axis, fix(longitudeOffset = 0.0010, accuracy = 6f), now)
        val farRegion = matcher.match(axis, fix(longitudeOffset = 0.0038, accuracy = 6f), now)

        assertTrue(stabilizer.update(axis, firstRegion, fix(longitudeOffset = 0.0010, accuracy = 6f)) is JourneyPosition.AtNode)
        assertSame(
            JourneyPosition.Unreliable,
            stabilizer.update(axis, farRegion, fix(longitudeOffset = 0.0038, accuracy = 6f))
        )
        assertEquals(
            farRegion,
            stabilizer.update(axis, farRegion, fix(longitudeOffset = 0.0038, accuracy = 6f))
        )
    }

    @Test
    fun stabilizerKeepsNodeWithinFifteenMeterExitHysteresisThenAllowsReverseMovement() {
        val stabilizer = RouteJourneyPositionStabilizer()
        val nodeFix = fix(longitudeOffset = 0.0020, accuracy = 5f)
        val node = matcher.match(axis, nodeFix, now)
        stabilizer.update(axis, node, nodeFix)

        val twentyFiveMetersEast = fix(longitudeOffset = 0.00224, accuracy = 5f)
        val localEdge = matcher.match(axis, twentyFiveMetersEast, now)
        val held = stabilizer.update(axis, localEdge, twentyFiveMetersEast)
        val reverseEdgeFix = fix(longitudeOffset = 0.0015, accuracy = 5f)
        val reverseEdge = matcher.match(axis, reverseEdgeFix, now)
        val reversed = stabilizer.update(axis, reverseEdge, reverseEdgeFix)

        assertEquals(node, held)
        assertEquals(reverseEdge, reversed)
    }

    private fun fix(
        latitude: Double = RouteJourneyFixtures.BASE_LATITUDE,
        longitudeOffset: Double,
        accuracy: Float,
        elapsedRealtimeMillis: Long = now
    ): JourneyLocationFix = JourneyLocationFix(
        latitude = latitude,
        longitude = RouteJourneyFixtures.BASE_LONGITUDE + longitudeOffset,
        accuracyMeters = accuracy,
        elapsedRealtimeMillis = elapsedRealtimeMillis
    )
}
