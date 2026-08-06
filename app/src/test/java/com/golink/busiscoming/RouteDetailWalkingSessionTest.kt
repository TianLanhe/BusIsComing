package com.golink.busiscoming

import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.P2pStopMap
import com.golink.busiscoming.data.model.P2pStopMapStop
import com.golink.busiscoming.data.model.PedestrianRoute
import com.golink.busiscoming.data.model.PedestrianRoutePath
import com.golink.busiscoming.data.model.PedestrianCoordinate
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailWalkingKind
import com.golink.busiscoming.data.model.RouteDetailWalkingSegment
import com.golink.busiscoming.data.model.RouteDetailWalkingState
import com.golink.busiscoming.data.repository.CsdiPedestrianRequest
import com.golink.busiscoming.data.repository.CsdiPedestrianResponse
import com.golink.busiscoming.data.repository.PedestrianRequestPriority
import com.golink.busiscoming.data.repository.PedestrianRequestTrigger
import com.golink.busiscoming.data.repository.PedestrianRouteRequestRuntime
import com.golink.busiscoming.data.repository.PedestrianSubscription
import com.golink.busiscoming.data.repository.RouteDetailWalkingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailWalkingSessionTest {
    @Test
    fun publishesStableInitialTableAndKeepsSuccessfulSegmentsAfterAnotherFails() {
        val runtime = FakeRuntime()
        val snapshots = mutableListOf<Map<String, RouteDetailWalkingState>>()
        val session = RouteDetailWalkingSession(
            query(),
            stopMap(),
            detail(),
            runtime,
            onSnapshot = { snapshots += it }
        )

        session.start()

        assertEquals(setOf("origin", "destination"), snapshots.single().keys)
        assertTrue(snapshots.single().values.all { it == RouteDetailWalkingState.Loading })
        assertTrue(runtime.priorities.all { it == PedestrianRequestPriority.DETAIL })
        assertTrue(runtime.triggers.all { it == PedestrianRequestTrigger.REENTRY })

        runtime.respond(0, CsdiPedestrianResponse.Success(route(100.2)))
        runtime.respond(1, CsdiPedestrianResponse.Failure(com.golink.busiscoming.data.repository.CsdiPedestrianFailureKind.NETWORK))

        assertTrue(snapshots.last().getValue("origin") is RouteDetailWalkingState.CsdiSuccess)
        assertEquals(
            RouteDetailWalkingState.CitybusFallback(40),
            snapshots.last().getValue("destination")
        )
    }

    @Test
    fun closeDetachesAllOutstandingConsumers() {
        val runtime = FakeRuntime()
        val session = RouteDetailWalkingSession(query(), stopMap(), detail(), runtime) {}
        session.start()

        session.close()

        assertEquals(2, runtime.closedCount)
    }

    private class FakeRuntime : PedestrianRouteRequestRuntime {
        val callbacks = mutableListOf<(CsdiPedestrianResponse) -> Unit>()
        val priorities = mutableListOf<PedestrianRequestPriority>()
        val triggers = mutableListOf<PedestrianRequestTrigger>()
        var closedCount = 0

        override fun subscribe(
            request: CsdiPedestrianRequest,
            priority: PedestrianRequestPriority,
            trigger: PedestrianRequestTrigger,
            callback: (CsdiPedestrianResponse) -> Unit
        ): PedestrianSubscription {
            priorities += priority
            triggers += trigger
            callbacks += callback
            return PedestrianSubscription { closedCount += 1 }
        }

        fun respond(index: Int, response: CsdiPedestrianResponse) = callbacks[index](response)
    }

    private fun query() = P2pRouteDetailQuery(
        rawInfo = "raw",
        generalInfo = "general",
        listId = "0",
        lang = "0",
        plan = P2pRoutePlan(
            rawInfo = "raw",
            lang = "0",
            legs = listOf(P2pRouteLeg("CTB", "A", "1", 1, 2, "O", null))
        ),
        recoveryContext = com.golink.busiscoming.data.model.P2pRouteRecoveryContext(
            originLatitude = 22.29,
            originLongitude = 114.09,
            destinationLatitude = 22.32,
            destinationLongitude = 114.12,
            searchMode = "D"
        )
    )

    private fun stopMap() = P2pStopMap(
        rawInfo = "raw",
        lang = "0",
        stops = listOf(
            mappedStop(1, "s1", 22.30, 114.10),
            mappedStop(2, "s2", 22.31, 114.11)
        )
    )

    private fun mappedStop(sequence: Int, id: String, latitude: Double, longitude: Double) = P2pStopMapStop(
        legIndex = 0,
        company = "CTB",
        routeVariant = "A",
        publicRoute = "1",
        bound = "O",
        sequence = sequence,
        stopId = id,
        rawName = id,
        displayName = id,
        latitude = latitude,
        longitude = longitude,
        markerType = ""
    )

    private fun detail() = RouteDetail(
        routeName = "1",
        priceHkd = 5.0,
        durationMinutes = 10,
        walkingDistanceMeters = 70,
        legs = listOf(
            RouteDetailLeg(
                route = "1",
                routeVariant = "A",
                directionText = null,
                boardingStop = detailStop(1, "s1", RouteDetailStopRole.BOARDING, 22.30, 114.10),
                viaStops = emptyList(),
                alightingStop = detailStop(2, "s2", RouteDetailStopRole.ALIGHTING, 22.31, 114.11)
            )
        ),
        originWalking = RouteDetailWalkingSegment(RouteDetailWalkingKind.ORIGIN, 30),
        destinationWalking = RouteDetailWalkingSegment(RouteDetailWalkingKind.DESTINATION, 40)
    )

    private fun detailStop(
        sequence: Int,
        id: String,
        role: RouteDetailStopRole,
        latitude: Double,
        longitude: Double
    ) = RouteDetailStop(id, id, id, sequence, latitude, longitude, "A", role)

    private fun route(distance: Double) = PedestrianRoute(
        distance,
        1.0,
        listOf(PedestrianRoutePath(listOf(PedestrianCoordinate(22.29, 114.09), PedestrianCoordinate(22.30, 114.10))))
    )
}
