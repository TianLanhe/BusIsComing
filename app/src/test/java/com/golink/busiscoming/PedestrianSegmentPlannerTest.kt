package com.golink.busiscoming

import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.P2pRouteRecoveryContext
import com.golink.busiscoming.data.model.P2pStopMap
import com.golink.busiscoming.data.model.P2pStopMapStop
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailTransfer
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.RouteDetailWalkingKind
import com.golink.busiscoming.data.model.RouteDetailWalkingSegment
import com.golink.busiscoming.data.repository.PedestrianEndpointFailure
import com.golink.busiscoming.data.repository.PedestrianSegment
import com.golink.busiscoming.data.repository.PedestrianSegmentPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PedestrianSegmentPlannerTest {
    @Test
    fun singleLegPlanCreatesOrderedOriginAndDestinationSegments() {
        val query = query(legs = listOf(planLeg("R1", 1, 5)))
        val detail = detail(legs = listOf(detailLeg("R1", 1, 5)))
        val plan = PedestrianSegmentPlanner.plan(query, stopMap(query, detail), detail)

        assertEquals(listOf("origin", "destination"), plan.segments.map { it.id.value })
        assertTrue(plan.segments.all { it is PedestrianSegment.Requestable })
        assertEquals(120, (plan.segments[0] as PedestrianSegment.Requestable).citybusFallbackDistanceMeters)
        assertEquals(180, (plan.segments[1] as PedestrianSegment.Requestable).citybusFallbackDistanceMeters)
        assertEquals(query.recoveryContext!!.walkingContextKey(), plan.key.endpointContextKey)
        assertEquals(query.plan.fingerprint(), plan.key.planFingerprint)
    }

    @Test
    fun walkingTransferCreatesDirectedRequestEvenWhenStopsAreVeryClose() {
        val query = query(
            legs = listOf(
                planLeg("R1", 1, 5),
                planLeg("R2", 10, 15)
            )
        )
        val first = detailLeg("R1", 1, 5, alightingLatitude = 22.3050000, alightingLongitude = 114.1050000)
        val second = detailLeg("R2", 10, 15, boardingLatitude = 22.3050001, boardingLongitude = 114.1050001)
        val detail = detail(
            legs = listOf(first, second),
            transfers = listOf(
                RouteDetailTransfer(
                    RouteDetailTransferType.WALK_TO_TRANSFER_STOP,
                    RouteDetailWalkingSegment(RouteDetailWalkingKind.TRANSFER, 25)
                )
            )
        )

        val plan = PedestrianSegmentPlanner.plan(query, stopMap(query, detail), detail)

        assertEquals(listOf("origin", "transfer:0", "destination"), plan.segments.map { it.id.value })
        val transfer = plan.segments[1] as PedestrianSegment.Requestable
        assertNotEquals(transfer.request.start, transfer.request.end)
        assertEquals(25, transfer.citybusFallbackDistanceMeters)
    }

    @Test
    fun sameStopTransferIsExplicitAndNeverCreatesARequest() {
        val query = query(legs = listOf(planLeg("R1", 1, 5), planLeg("R2", 10, 15)))
        val detail = detail(
            legs = listOf(detailLeg("R1", 1, 5), detailLeg("R2", 10, 15)),
            transfers = listOf(RouteDetailTransfer(RouteDetailTransferType.SAME_STOP))
        )

        val plan = PedestrianSegmentPlanner.plan(query, stopMap(query, detail), detail)

        assertTrue(plan.segments[1] is PedestrianSegment.SameStop)
        assertEquals(2, plan.segments.count { it is PedestrianSegment.Requestable })
    }

    @Test
    fun missingEndpointOnlyMakesItsOwnSegmentUnavailable() {
        val query = query(legs = listOf(planLeg("R1", 1, 5), planLeg("R2", 10, 15)))
        val detail = detail(
            legs = listOf(detailLeg("R1", 1, 5), detailLeg("R2", 10, 15)),
            transfers = listOf(RouteDetailTransfer(RouteDetailTransferType.WALK_TO_TRANSFER_STOP))
        )
        val mapWithoutSecondBoarding = stopMap(query, detail).copy(
            stops = stopMap(query, detail).stops.filterNot { it.routeVariant == "R2" && it.sequence == 10 }
        )

        val plan = PedestrianSegmentPlanner.plan(query, mapWithoutSecondBoarding, detail)

        assertTrue(plan.segments[0] is PedestrianSegment.Requestable)
        assertEquals(
            PedestrianEndpointFailure.MISSING,
            (plan.segments[1] as PedestrianSegment.Unavailable).reason
        )
        assertTrue(plan.segments[2] is PedestrianSegment.Requestable)
    }

    @Test
    fun exactDetailIdentityCanBackfillInvalidPrimaryCoordinateButStopIdMismatchCannot() {
        val query = query(legs = listOf(planLeg("R1", 1, 5)))
        val detail = detail(legs = listOf(detailLeg("R1", 1, 5)))
        val map = stopMap(query, detail)
        val invalidBoarding = map.stops.first().copy(latitude = Double.NaN)

        val exact = PedestrianSegmentPlanner.plan(
            query,
            map.copy(stops = listOf(invalidBoarding) + map.stops.drop(1)),
            detail
        )
        assertTrue(exact.segments.first() is PedestrianSegment.Requestable)

        val mismatchedDetail = detail.copy(
            legs = listOf(
                detail.legs.single().copy(
                    boardingStop = detail.legs.single().boardingStop.copy(stopId = "OTHER")
                )
            )
        )
        val mismatch = PedestrianSegmentPlanner.plan(
            query,
            map.copy(stops = listOf(invalidBoarding) + map.stops.drop(1)),
            mismatchedDetail
        )
        assertEquals(
            PedestrianEndpointFailure.MISSING,
            (mismatch.segments.first() as PedestrianSegment.Unavailable).reason
        )
    }

    @Test
    fun conflictingExactCitybusCoordinatesAreRejectedInsteadOfAveraged() {
        val query = query(legs = listOf(planLeg("R1", 1, 5)))
        val detail = detail(legs = listOf(detailLeg("R1", 1, 5)))
        val map = stopMap(query, detail)
        val shiftedBoarding = map.stops.first().copy(latitude = map.stops.first().latitude + 0.001)

        val plan = PedestrianSegmentPlanner.plan(
            query,
            map.copy(stops = listOf(shiftedBoarding) + map.stops.drop(1)),
            detail
        )

        assertEquals(
            PedestrianEndpointFailure.SOURCE_CONFLICT,
            (plan.segments.first() as PedestrianSegment.Unavailable).reason
        )
    }

    private fun query(legs: List<P2pRouteLeg>): P2pRouteDetailQuery = P2pRouteDetailQuery(
        rawInfo = "raw",
        generalInfo = "general",
        listId = "list",
        lang = "0",
        plan = P2pRoutePlan(rawInfo = "raw", lang = "0", legs = legs),
        recoveryContext = P2pRouteRecoveryContext(
            originLatitude = 22.299,
            originLongitude = 114.099,
            destinationLatitude = 22.316,
            destinationLongitude = 114.116,
            searchMode = "T"
        )
    )

    private fun planLeg(routeVariant: String, boardingSeq: Int, alightingSeq: Int) = P2pRouteLeg(
        company = "CTB",
        routeVariant = routeVariant,
        route = routeVariant,
        boardingSeq = boardingSeq,
        alightingSeq = alightingSeq,
        bound = "O",
        directionPath = "outbound"
    )

    private fun detail(
        legs: List<RouteDetailLeg>,
        transfers: List<RouteDetailTransfer> = emptyList()
    ) = RouteDetail(
        routeName = legs.joinToString(" → ") { it.route },
        priceHkd = 10.0,
        durationMinutes = 30,
        walkingDistanceMeters = 300,
        legs = legs,
        originWalking = RouteDetailWalkingSegment(RouteDetailWalkingKind.ORIGIN, 120),
        transfers = transfers,
        destinationWalking = RouteDetailWalkingSegment(RouteDetailWalkingKind.DESTINATION, 180)
    )

    private fun detailLeg(
        routeVariant: String,
        boardingSeq: Int,
        alightingSeq: Int,
        boardingLatitude: Double = 22.300,
        boardingLongitude: Double = 114.100,
        alightingLatitude: Double = 22.315,
        alightingLongitude: Double = 114.115
    ) = RouteDetailLeg(
        route = routeVariant,
        routeVariant = routeVariant,
        directionText = null,
        boardingStop = detailStop(
            routeVariant,
            boardingSeq,
            "${routeVariant}-B",
            boardingLatitude,
            boardingLongitude,
            RouteDetailStopRole.BOARDING
        ),
        viaStops = emptyList(),
        alightingStop = detailStop(
            routeVariant,
            alightingSeq,
            "${routeVariant}-A",
            alightingLatitude,
            alightingLongitude,
            RouteDetailStopRole.ALIGHTING
        )
    )

    private fun detailStop(
        routeVariant: String,
        sequence: Int,
        stopId: String,
        latitude: Double,
        longitude: Double,
        role: RouteDetailStopRole
    ) = RouteDetailStop(
        rawName = "站名",
        displayName = "站名",
        stopId = stopId,
        sequence = sequence,
        latitude = latitude,
        longitude = longitude,
        routeVariant = routeVariant,
        role = role
    )

    private fun stopMap(query: P2pRouteDetailQuery, detail: RouteDetail): P2pStopMap {
        val stops = detail.legs.flatMapIndexed { index, leg ->
            listOf(
                mapStop(index, leg.boardingStop),
                mapStop(index, leg.alightingStop)
            )
        }
        return P2pStopMap(query.rawInfo, query.lang, stops)
    }

    private fun mapStop(index: Int, stop: RouteDetailStop) = P2pStopMapStop(
        legIndex = index,
        company = "CTB",
        routeVariant = stop.routeVariant,
        publicRoute = stop.routeVariant,
        bound = "O",
        sequence = stop.sequence,
        stopId = stop.stopId,
        rawName = stop.rawName,
        displayName = stop.displayName,
        latitude = stop.latitude,
        longitude = stop.longitude,
        markerType = ""
    )
}
