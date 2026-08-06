package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.P2pRouteRecoveryContext
import com.golink.busiscoming.data.model.P2pStopMap
import com.golink.busiscoming.data.model.P2pStopMapStop
import com.golink.busiscoming.data.model.PedestrianRoute
import com.golink.busiscoming.data.model.PedestrianRoutePath
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailTransfer
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.WalkingDistanceDisplayState
import com.golink.busiscoming.data.repository.CsdiPedestrianFailureKind
import com.golink.busiscoming.data.repository.CsdiPedestrianRequest
import com.golink.busiscoming.data.repository.CsdiPedestrianResponse
import com.golink.busiscoming.data.repository.PedestrianRequestPriority
import com.golink.busiscoming.data.repository.PedestrianRequestTrigger
import com.golink.busiscoming.data.repository.PedestrianRouteRequestRuntime
import com.golink.busiscoming.data.repository.PedestrianSubscription
import com.golink.busiscoming.data.repository.RouteWalkingCompletionSession
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteWalkingCompletionSessionTest {
    @Test
    fun stopMapAndDetailStartConcurrentlyAndOriginDestinationDoNotWaitForTransferMeaning() {
        val route = route(twoLegs = true)
        val detail = detail(twoLegs = true)
        val bothStarted = CountDownLatch(2)
        val releaseDetail = CountDownLatch(1)
        val subscribed = Collections.synchronizedList(mutableListOf<CsdiPedestrianRequest>())
        val updates = Collections.synchronizedList(mutableListOf<WalkingDistanceDisplayState>())
        val completed = CountDownLatch(1)
        val sourceExecutor = Executors.newFixedThreadPool(2)
        val session = RouteWalkingCompletionSession(
            routes = listOf(route),
            stopMapLoader = {
                bothStarted.countDown()
                stopMap(route, detail)
            },
            detailLoader = {
                bothStarted.countDown()
                check(releaseDetail.await(2, TimeUnit.SECONDS))
                detail
            },
            pedestrianRuntime = immediateRuntime { request ->
                subscribed += request
                CsdiPedestrianResponse.Success(success(request))
            },
            sourceExecutor = sourceExecutor,
            onUpdate = { _, state ->
                updates += state
                if (state is WalkingDistanceDisplayState.CsdiSuccess) completed.countDown()
            }
        )
        try {
            session.start()
            assertTrue(bothStarted.await(2, TimeUnit.SECONDS))
            repeat(20) {
                if (subscribed.size >= 2) return@repeat
                Thread.sleep(10)
            }
            assertEquals(2, subscribed.size)
            assertTrue(updates.isEmpty())

            releaseDetail.countDown()
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals(subscribed.map { it.key.toString() }.toString(), 3, subscribed.size)
            assertEquals(WalkingDistanceDisplayState.CsdiSuccess(302), updates.last())
        } finally {
            releaseDetail.countDown()
            session.close()
            sourceExecutor.shutdownNow()
        }
    }

    @Test
    fun anyRequiredSegmentFailureImmediatelyFallsBackToUnchangedCitybusTotal() {
        val route = route(twoLegs = false)
        val detail = detail(twoLegs = false)
        val update = CountDownLatch(1)
        var delivered: WalkingDistanceDisplayState? = null
        val sourceExecutor = Executors.newFixedThreadPool(2)
        val session = RouteWalkingCompletionSession(
            routes = listOf(route),
            stopMapLoader = { stopMap(route, detail) },
            detailLoader = { detail },
            pedestrianRuntime = immediateRuntime {
                CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.HTTP_4XX)
            },
            sourceExecutor = sourceExecutor,
            onUpdate = { _, state -> delivered = state; update.countDown() }
        )
        try {
            session.start()
            assertTrue(update.await(2, TimeUnit.SECONDS))
            assertEquals(WalkingDistanceDisplayState.CitybusFallback(300), delivered)
            assertEquals(300, route.walkingDistanceMeters)
        } finally {
            session.close()
            sourceExecutor.shutdownNow()
        }
    }

    private fun immediateRuntime(
        result: (CsdiPedestrianRequest) -> CsdiPedestrianResponse
    ) = object : PedestrianRouteRequestRuntime {
        override fun subscribe(
            request: CsdiPedestrianRequest,
            priority: PedestrianRequestPriority,
            trigger: PedestrianRequestTrigger,
            callback: (CsdiPedestrianResponse) -> Unit
        ): PedestrianSubscription {
            callback(result(request))
            return PedestrianSubscription {}
        }
    }

    private fun route(twoLegs: Boolean): BusRouteOption {
        val legs = if (twoLegs) {
            listOf(planLeg("R1", 1, 5), planLeg("R2", 10, 15))
        } else {
            listOf(planLeg("R1", 1, 5))
        }
        val query = P2pRouteDetailQuery(
            rawInfo = "raw",
            generalInfo = "general",
            listId = "list",
            lang = "0",
            plan = P2pRoutePlan("raw", "0", legs),
            recoveryContext = P2pRouteRecoveryContext(22.299, 114.099, 22.316, 114.116, "T")
        )
        return BusRouteOption(
            routeName = legs.joinToString(" → ") { it.route },
            routeSegments = legs.map { it.route },
            priceHkd = 10.0,
            durationMinutes = 30,
            arrivalMinutes = 30,
            transferCount = legs.size - 1,
            walkingDistanceMeters = 300,
            routeDetailQuery = query,
            resultId = "route"
        )
    }

    private fun planLeg(route: String, boarding: Int, alighting: Int) = P2pRouteLeg(
        "CTB", route, route, boarding, alighting, "O", "outbound"
    )

    private fun detail(twoLegs: Boolean): RouteDetail {
        val legs = if (twoLegs) {
            listOf(
                detailLeg("R1", 1, 5, 22.300, 114.100, 22.305, 114.105),
                detailLeg("R2", 10, 15, 22.306, 114.106, 22.315, 114.115)
            )
        } else {
            listOf(detailLeg("R1", 1, 5, 22.300, 114.100, 22.315, 114.115))
        }
        return RouteDetail(
            routeName = "route",
            priceHkd = 10.0,
            durationMinutes = 30,
            walkingDistanceMeters = 300,
            legs = legs,
            transfers = if (twoLegs) {
                listOf(RouteDetailTransfer(RouteDetailTransferType.WALK_TO_TRANSFER_STOP))
            } else {
                emptyList()
            }
        )
    }

    private fun detailLeg(
        route: String,
        boardingSeq: Int,
        alightingSeq: Int,
        boardingLat: Double,
        boardingLon: Double,
        alightingLat: Double,
        alightingLon: Double
    ) = RouteDetailLeg(
        route,
        route,
        null,
        stop(route, boardingSeq, "$route-B", boardingLat, boardingLon, RouteDetailStopRole.BOARDING),
        emptyList(),
        stop(route, alightingSeq, "$route-A", alightingLat, alightingLon, RouteDetailStopRole.ALIGHTING)
    )

    private fun stop(
        route: String,
        sequence: Int,
        stopId: String,
        latitude: Double,
        longitude: Double,
        role: RouteDetailStopRole
    ) = RouteDetailStop("站", "站", stopId, sequence, latitude, longitude, route, role)

    private fun stopMap(route: BusRouteOption, detail: RouteDetail): P2pStopMap = P2pStopMap(
        "raw",
        "0",
        detail.legs.flatMapIndexed { index, leg ->
            listOf(mapStop(index, leg.boardingStop), mapStop(index, leg.alightingStop))
        }
    )

    private fun mapStop(index: Int, stop: RouteDetailStop) = P2pStopMapStop(
        index,
        "CTB",
        stop.routeVariant,
        stop.routeVariant,
        "O",
        stop.sequence,
        stop.stopId,
        stop.rawName,
        stop.displayName,
        stop.latitude,
        stop.longitude,
        ""
    )

    private fun success(request: CsdiPedestrianRequest) = PedestrianRoute(
        100.5,
        1.675,
        listOf(PedestrianRoutePath(listOf(request.start, request.end)))
    )
}
