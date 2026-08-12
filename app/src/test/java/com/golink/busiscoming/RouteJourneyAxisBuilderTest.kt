package com.golink.busiscoming

import com.golink.busiscoming.data.location.RouteJourneyAxisBuilder
import com.golink.busiscoming.data.model.JourneyAxisEdge
import com.golink.busiscoming.data.model.JourneyAxisNodeKind
import com.golink.busiscoming.data.model.RouteGeometryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteJourneyAxisBuilderTest {
    private val builder = RouteJourneyAxisBuilder()

    @Test
    fun buildsOrderedDirectJourneyWithBusAndWalkingEdges() {
        val axis = builder.build(RouteJourneyFixtures.input())

        assertEquals(5, axis.nodes.size)
        assertEquals(JourneyAxisNodeKind.ORIGIN, axis.nodes.first().kind)
        assertEquals(JourneyAxisNodeKind.DESTINATION, axis.nodes.last().kind)
        assertEquals(2, axis.edges.filterIsInstance<JourneyAxisEdge.Bus>().size)
        assertEquals(2, axis.edges.filterIsInstance<JourneyAxisEdge.Walking>().size)
        assertTrue(axis.edges.all { it.matchable })
    }

    @Test
    fun mergesSameStopTransferIntoOneCompositeNode() {
        val detail = RouteJourneyFixtures.transferDetail(sameStop = true)
        val input = RouteJourneyFixtures.input(
            detail = detail,
            walkingSegments = mapOf(
                "origin" to RouteJourneyFixtures.walkingRoute(0.0 to 0.0010),
                "transfer:0" to com.golink.busiscoming.data.model.RouteDetailWalkingState.SameStop,
                "destination" to RouteJourneyFixtures.walkingRoute(0.0060 to 0.0070)
            )
        )

        val axis = builder.build(input)

        val transfers = axis.nodes.filter { it.kind == JourneyAxisNodeKind.SAME_STOP_TRANSFER }
        assertEquals(1, transfers.size)
        assertEquals(setOf("leg-0-alight", "leg-1-board"), transfers.single().timelineTargetIds)
        assertTrue(axis.edges.none { it.summarySegmentId == "walk-transfer-0" })
    }

    @Test
    fun rejectsWholeBusLegWhenStopProjectionRunsBackwards() {
        val detail = RouteJourneyFixtures.directDetail()
        val leg = detail.legs.single()
        val reversed = RouteJourneyFixtures.geometry(leg, reversed = true)

        val axis = builder.build(
            RouteJourneyFixtures.input(detail, mapOf(reversed.key to reversed))
        )

        assertTrue(axis.edges.filterIsInstance<JourneyAxisEdge.Bus>().all { !it.matchable })
    }

    @Test
    fun rejectsParallelGeometryWhenStopsHaveTwoSeparatedNearEqualProjections() {
        val detail = RouteJourneyFixtures.directDetail()
        val leg = detail.legs.single()
        val key = RouteGeometryKey("R1-A", 1, 3)
        val parallel = com.golink.busiscoming.data.model.RouteGeometrySegment(
            key,
            listOf(
                com.golink.busiscoming.data.model.RouteGeometryPoint(
                    "a0",
                    RouteJourneyFixtures.BASE_LATITUDE,
                    RouteJourneyFixtures.BASE_LONGITUDE + 0.0010
                ),
                com.golink.busiscoming.data.model.RouteGeometryPoint(
                    "a1",
                    RouteJourneyFixtures.BASE_LATITUDE,
                    RouteJourneyFixtures.BASE_LONGITUDE + 0.0030
                ),
                com.golink.busiscoming.data.model.RouteGeometryPoint(
                    "turn",
                    RouteJourneyFixtures.BASE_LATITUDE + 0.00008,
                    RouteJourneyFixtures.BASE_LONGITUDE + 0.0030
                ),
                com.golink.busiscoming.data.model.RouteGeometryPoint(
                    "b1",
                    RouteJourneyFixtures.BASE_LATITUDE + 0.00008,
                    RouteJourneyFixtures.BASE_LONGITUDE + 0.0010
                )
            )
        )

        val busEdges = builder.build(
            RouteJourneyFixtures.input(detail, mapOf(key to parallel))
        ).edges.filterIsInstance<JourneyAxisEdge.Bus>()

        assertTrue(busEdges.all { !it.matchable })
    }

    @Test
    fun preservesWalkingSubpathGapWithoutCreatingSyntheticGeometry() {
        val input = RouteJourneyFixtures.input(
            walkingSegments = mapOf(
                "origin" to RouteJourneyFixtures.walkingRoute(0.0 to 0.0004, 0.0007 to 0.0010),
                "destination" to RouteJourneyFixtures.walkingRoute(0.0030 to 0.0040)
            )
        )

        val edge = builder.build(input).edges.filterIsInstance<JourneyAxisEdge.Walking>()
            .single { it.summarySegmentId == "walk-origin" }

        assertEquals(2, edge.paths.size)
        assertEquals(4, edge.paths.sumOf { it.points.size })
        assertTrue(edge.totalPathLengthMeters > 0.0)
    }

    @Test
    fun keepsReliableLegMatchableWhenAnotherLegGeometryIsMissing() {
        val detail = RouteJourneyFixtures.transferDetail(sameStop = false)
        val secondGeometry = RouteJourneyFixtures.geometry(detail.legs[1])
        val walking = mapOf(
            "origin" to RouteJourneyFixtures.walkingRoute(0.0 to 0.0010),
            "transfer:0" to RouteJourneyFixtures.walkingRoute(0.0030 to 0.0040),
            "destination" to RouteJourneyFixtures.walkingRoute(0.0060 to 0.0070)
        )

        val busEdges = builder.build(
            RouteJourneyFixtures.input(
                detail,
                geometries = mapOf(secondGeometry.key to secondGeometry),
                walkingSegments = walking
            )
        ).edges.filterIsInstance<JourneyAxisEdge.Bus>()

        assertTrue(busEdges.filter { it.legIndex == 0 }.all { !it.matchable })
        assertTrue(busEdges.filter { it.legIndex == 1 }.all { it.matchable })
    }

    @Test
    fun returnsSameSnapshotForSameStaticInputButNotForGeometryChange() {
        val input = RouteJourneyFixtures.input()
        val first = builder.build(input)
        val second = builder.build(input)
        val geometryKey = RouteGeometryKey("R1-A", 1, 3)
        val changedGeometry = input.geometries.getValue(geometryKey).copy(
            points = input.geometries.getValue(geometryKey).points +
                com.golink.busiscoming.data.model.RouteGeometryPoint(
                    "extra",
                    RouteJourneyFixtures.BASE_LATITUDE,
                    RouteJourneyFixtures.BASE_LONGITUDE + 0.0031
                )
        )
        val changed = builder.build(
            input.copy(geometries = input.geometries + (geometryKey to changedGeometry))
        )

        assertSame(first, second)
        assertNotSame(second, changed)
        assertEquals(input.pageGeneration, first.identity.pageGeneration)
        assertEquals(input.structureIdentity, first.identity.structureIdentity)
    }

    @Test
    fun reusesSnapshotWhenOnlyDynamicDetailFieldsChange() {
        val input = RouteJourneyFixtures.input()
        val first = builder.build(input)
        val dynamicRefresh = input.detail.copy(
            priceHkd = input.detail.priceHkd + 1.0,
            durationMinutes = input.detail.durationMinutes + 3,
            plannedDepartureTime = "12:34",
            plannedArrivalTime = "12:57",
            legs = input.detail.legs.map { leg ->
                leg.copy(
                    fareHkd = 9.9,
                    plannedBoardingTime = "12:36",
                    plannedAlightingTime = "12:55"
                )
            }
        )

        val refreshed = builder.build(input.copy(detail = dynamicRefresh))

        assertSame(first, refreshed)
    }
}
