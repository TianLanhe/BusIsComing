package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.PedestrianCoordinate
import com.golink.busiscoming.data.model.PedestrianRoute
import com.golink.busiscoming.data.model.PedestrianRoutePath
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailTransfer
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.RouteDetailWalkingKind
import com.golink.busiscoming.data.model.RouteDetailWalkingSegment
import com.golink.busiscoming.data.model.RouteDetailWalkingState
import com.golink.busiscoming.data.model.EtaUnavailableReason
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.ui.main.RouteDetailUiFormatter
import com.golink.busiscoming.ui.main.RouteDetailUiItem
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import com.golink.busiscoming.ui.main.RouteDynamicDetailStatus
import com.golink.busiscoming.ui.main.RideStopCountState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailUiFormatterTest {
    @Test
    fun summaryCountsViaAndAlightingStopsWithoutBoardingStops() {
        val items = RouteDetailUiFormatter.items(detail(), emptySet(), WaitTimeState.Available(6))
        val summary = items.filterIsInstance<RouteDetailUiItem.Summary>().single()

        assertEquals(RideStopCountState.Available(16), summary.rideStopCount)
        assertEquals(403, summary.walkingDistanceMeters)
        assertTrue(summary.isWalkingDistanceComplete)
        assertEquals("01:21", summary.plannedArrivalTime)
        assertEquals(6, (summary.firstLegEta as WaitTimeState.Available).minutes)
    }

    @Test
    fun adjacentBoardingAndAlightingStopsContributeOneRideStopPerLeg() {
        val adjacentLegs = detail().copy(legs = listOf(leg("789", 0), leg("11", 0)))

        val summary = RouteDetailUiFormatter.items(adjacentLegs, emptySet(), WaitTimeState.Loading)
            .filterIsInstance<RouteDetailUiItem.Summary>()
            .single()

        assertEquals(RideStopCountState.Available(2), summary.rideStopCount)
    }

    @Test
    fun launchSummaryKeepsStationCountUnknownUntilReliableStructureArrives() {
        val args = RouteDetailLaunchArgs.fromRoute(
            BusRouteOption(
                routeName = "789 → 11",
                routeSegments = listOf("789", "11"),
                priceHkd = 14.0,
                durationMinutes = 28,
                arrivalMinutes = 4,
                transferCount = 1,
                walkingDistanceMeters = 633,
                routeDetailQuery = P2pRouteDetailQuery(
                    rawInfo = "raw",
                    generalInfo = "12:10|*|28",
                    listId = "0",
                    lang = "0",
                    plan = P2pRoutePlan(
                        rawInfo = "raw",
                        lang = "0",
                        legs = listOf(
                            P2pRouteLeg("CTB", "789-A", "789", 1, 2, "O", null),
                            P2pRouteLeg("CTB", "11-A", "11", 7, 8, "O", null)
                        )
                    )
                )
            )
        )

        val loading = RouteDetailUiFormatter.launchSummary(
            args,
            WaitTimeState.Loading,
            RideStopCountState.Loading
        )
        val unavailable = RouteDetailUiFormatter.launchSummary(
            args,
            WaitTimeState.Loading,
            RideStopCountState.Unavailable
        )

        assertEquals(RideStopCountState.Loading, loading.rideStopCount)
        assertEquals(RideStopCountState.Unavailable, unavailable.rideStopCount)
    }

    @Test
    fun eachLegExpandsIndependentlyAndBusLegsDoNotRepeatLiveEta() {
        val collapsed = RouteDetailUiFormatter.items(detail(), emptySet(), WaitTimeState.Available(6))
        assertTrue(collapsed.none { it is RouteDetailUiItem.ViaStop })
        assertEquals(2, collapsed.filterIsInstance<RouteDetailUiItem.ViaToggle>().size)

        val expanded = RouteDetailUiFormatter.items(detail(), setOf(1), WaitTimeState.Available(6))
        assertEquals(6, expanded.filterIsInstance<RouteDetailUiItem.ViaStop>().size)
        val busLegs = expanded.filterIsInstance<RouteDetailUiItem.BusLeg>()
        assertFalse(busLegs[0].colorKey == busLegs[1].colorKey)
        assertEquals(9.7, busLegs[0].fareHkd!!, 0.0)
        assertTrue(expanded.indexOfFirst { it is RouteDetailUiItem.ViaToggle && it.legIndex == 1 } >
            expanded.indexOfFirst { it is RouteDetailUiItem.BusLeg && it.legIndex == 1 })
    }

    @Test
    fun sameStopTransferHasNoWalkingDistanceItem() {
        val value = detail().copy(
            transfers = listOf(RouteDetailTransfer(RouteDetailTransferType.SAME_STOP))
        )
        val items = RouteDetailUiFormatter.items(value, emptySet(), WaitTimeState.NoArrivals)

        assertTrue(items.any { it is RouteDetailUiItem.Transfer && it.type == RouteDetailTransferType.SAME_STOP })
        assertTrue(items.filterIsInstance<RouteDetailUiItem.Walking>().none { it.kind == RouteDetailWalkingKind.TRANSFER })
    }

    @Test
    fun partialWalkingKeepsKnownSegmentsAndUsesCardAggregateWithoutTreatingUnknownAsZero() {
        val value = detail().copy(
            walkingDistanceMeters = 378,
            transfers = listOf(
                RouteDetailTransfer(
                    RouteDetailTransferType.WALK_TO_TRANSFER_STOP,
                    RouteDetailWalkingSegment(RouteDetailWalkingKind.TRANSFER, null)
                )
            )
        )

        val items = RouteDetailUiFormatter.items(value, emptySet(), WaitTimeState.Loading)
        val summary = items.filterIsInstance<RouteDetailUiItem.Summary>().single()
        val walking = items.filterIsInstance<RouteDetailUiItem.Walking>()

        assertEquals(378, summary.walkingDistanceMeters)
        assertFalse(summary.isWalkingDistanceComplete)
        assertEquals(243, walking.single { it.kind == RouteDetailWalkingKind.ORIGIN }.distanceMeters)
        assertNull(walking.single { it.kind == RouteDetailWalkingKind.TRANSFER }.distanceMeters)
        assertEquals(135, walking.single { it.kind == RouteDetailWalkingKind.DESTINATION }.distanceMeters)
    }

    @Test
    fun csdiSegmentsRoundOnlyForDisplayAndSummaryRoundsAfterRawSum() {
        val states = mapOf(
            "origin" to RouteDetailWalkingState.CsdiSuccess(pedestrianRoute(100.2, 1.01)),
            "transfer:0" to RouteDetailWalkingState.SameStop,
            "destination" to RouteDetailWalkingState.CsdiSuccess(pedestrianRoute(100.2, 0.01))
        )

        val items = RouteDetailUiFormatter.items(
            detail().copy(transfers = listOf(RouteDetailTransfer(RouteDetailTransferType.SAME_STOP))),
            emptySet(),
            WaitTimeState.Loading,
            walkingSegments = states
        )
        val summary = items.filterIsInstance<RouteDetailUiItem.Summary>().single()
        val walking = items.filterIsInstance<RouteDetailUiItem.Walking>()

        assertEquals(201, summary.walkingDistanceMeters)
        assertTrue(summary.isWalkingDistanceComplete)
        assertFalse(summary.isWalkingDistanceLoading)
        assertEquals(101, walking.single { it.kind == RouteDetailWalkingKind.ORIGIN }.distanceMeters)
        assertEquals(2, walking.single { it.kind == RouteDetailWalkingKind.ORIGIN }.approximateMinutes)
        assertEquals(1, walking.single { it.kind == RouteDetailWalkingKind.DESTINATION }.approximateMinutes)
        assertTrue(walking.none { it.kind == RouteDetailWalkingKind.TRANSFER })
    }

    @Test
    fun loadingAndFallbackNeverMixSourcesInSummaryButKeepSegmentSuccess() {
        val loadingItems = RouteDetailUiFormatter.items(
            detail(),
            emptySet(),
            WaitTimeState.Loading,
            walkingSegments = mapOf(
                "origin" to RouteDetailWalkingState.CsdiSuccess(pedestrianRoute(100.2, 1.01)),
                "transfer:0" to RouteDetailWalkingState.Loading,
                "destination" to RouteDetailWalkingState.Loading
            )
        )
        val loadingSummary = loadingItems.filterIsInstance<RouteDetailUiItem.Summary>().single()
        assertTrue(loadingSummary.isWalkingDistanceLoading)
        assertEquals(378, loadingSummary.walkingDistanceMeters)

        val fallbackItems = RouteDetailUiFormatter.items(
            detail(),
            emptySet(),
            WaitTimeState.Loading,
            walkingSegments = mapOf(
                "origin" to RouteDetailWalkingState.CsdiSuccess(pedestrianRoute(100.2, 1.01)),
                "transfer:0" to RouteDetailWalkingState.CitybusFallback(null),
                "destination" to RouteDetailWalkingState.Loading
            )
        )
        val fallbackSummary = fallbackItems.filterIsInstance<RouteDetailUiItem.Summary>().single()
        val fallbackTransfer = fallbackItems.filterIsInstance<RouteDetailUiItem.Walking>()
            .single { it.kind == RouteDetailWalkingKind.TRANSFER }

        assertEquals(378, fallbackSummary.walkingDistanceMeters)
        assertFalse(fallbackSummary.isWalkingDistanceComplete)
        assertFalse(fallbackSummary.isWalkingDistanceLoading)
        assertTrue(fallbackTransfer.isUnavailable)
        assertEquals(101, fallbackItems.filterIsInstance<RouteDetailUiItem.Walking>()
            .single { it.kind == RouteDetailWalkingKind.ORIGIN }.distanceMeters)
    }

    @Test
    fun everyWaitStateStaysOnTheSummaryOnly() {
        val states = buildList<WaitTimeState> {
            add(WaitTimeState.Loading)
            add(WaitTimeState.NoArrivals)
            EtaUnavailableReason.entries.forEach { add(WaitTimeState.Unavailable(it)) }
        }

        states.forEach { state ->
            val items = RouteDetailUiFormatter.items(detail(), emptySet(), state)
            assertEquals(
                state,
                items.filterIsInstance<RouteDetailUiItem.Summary>().single().firstLegEta
            )
            assertEquals(2, items.filterIsInstance<RouteDetailUiItem.BusLeg>().size)
        }
    }

    @Test
    fun summarySegmentsAreCompleteStableAndUseAuthoritativeDurations() {
        val states = mapOf(
            "origin" to RouteDetailWalkingState.CsdiSuccess(pedestrianRoute(100.0, 1.01)),
            "transfer:0" to RouteDetailWalkingState.CsdiSuccess(pedestrianRoute(80.0, 2.01)),
            "destination" to RouteDetailWalkingState.CsdiSuccess(pedestrianRoute(60.0, 0.01))
        )

        val summary = RouteDetailUiFormatter.items(
            detail(),
            emptySet(),
            WaitTimeState.Loading,
            walkingSegments = states
        ).filterIsInstance<RouteDetailUiItem.Summary>().single()

        assertEquals(
            listOf(
                "walk-origin",
                "leg-0-card",
                "walk-transfer-0",
                "leg-1-card",
                "walk-destination"
            ),
            summary.segments.map { it.detailTargetId }
        )
        assertEquals(listOf(2, 21, 3, 21, 1), summary.segments.map { it.durationMinutes })
        assertEquals(21, RouteDetailUiFormatter.plannedMinutesBetween("23:52", "00:13"))
        assertNull(RouteDetailUiFormatter.plannedMinutesBetween("--", "00:13"))
    }

    @Test
    fun dynamicRefreshAndFailureAddOnlyTheirLocalStatusRow() {
        val refreshing = RouteDetailUiFormatter.items(
            detail(),
            emptySet(),
            WaitTimeState.Loading,
            RouteDynamicDetailStatus.REFRESHING
        )
        val stale = RouteDetailUiFormatter.items(
            detail(),
            emptySet(),
            WaitTimeState.Loading,
            RouteDynamicDetailStatus.STALE_AFTER_ERROR
        )

        assertEquals(
            RouteDynamicDetailStatus.REFRESHING,
            refreshing.filterIsInstance<RouteDetailUiItem.DynamicStatus>().single().status
        )
        assertEquals(
            RouteDynamicDetailStatus.STALE_AFTER_ERROR,
            stale.filterIsInstance<RouteDetailUiItem.DynamicStatus>().single().status
        )
        assertEquals(2, refreshing.filterIsInstance<RouteDetailUiItem.BusLeg>().size)
        assertEquals(2, stale.filterIsInstance<RouteDetailUiItem.BusLeg>().size)
    }

    private fun detail(): RouteDetail {
        return RouteDetail(
            routeName = "N8P → N969",
            priceHkd = 51.2,
            durationMinutes = 49,
            walkingDistanceMeters = 378,
            legs = listOf(leg("N8P", 8), leg("N969", 6)),
            originWalking = RouteDetailWalkingSegment(RouteDetailWalkingKind.ORIGIN, 243),
            transfers = listOf(
                RouteDetailTransfer(
                    RouteDetailTransferType.WALK_TO_TRANSFER_STOP,
                    RouteDetailWalkingSegment(RouteDetailWalkingKind.TRANSFER, 25)
                )
            ),
            destinationWalking = RouteDetailWalkingSegment(RouteDetailWalkingKind.DESTINATION, 135),
            plannedDepartureTime = "00:32",
            plannedArrivalTime = "01:21",
            originName = "漁灣邨漁進樓",
            destinationName = "港鐵中環站"
        )
    }

    private fun leg(route: String, viaCount: Int): RouteDetailLeg {
        return RouteDetailLeg(
            route = route,
            routeVariant = "$route-VARIANT",
            directionText = "目的地",
            boardingStop = stop("上車站", 1, RouteDetailStopRole.BOARDING),
            viaStops = (1..viaCount).map { stop("途經站$it", it + 1, RouteDetailStopRole.VIA) },
            alightingStop = stop("下車站", viaCount + 2, RouteDetailStopRole.ALIGHTING),
            fareHkd = 9.7,
            plannedBoardingTime = "00:42",
            plannedAlightingTime = "01:03"
        )
    }

    private fun stop(name: String, sequence: Int, role: RouteDetailStopRole) = RouteDetailStop(
        rawName = name,
        displayName = name,
        stopId = sequence.toString(),
        sequence = sequence,
        latitude = 22.0,
        longitude = 114.0,
        routeVariant = "variant",
        role = role
    )

    private fun pedestrianRoute(distance: Double, minutes: Double) = PedestrianRoute(
        rawDistanceMeters = distance,
        rawTimeMinutes = minutes,
        paths = listOf(
            PedestrianRoutePath(
                listOf(
                    PedestrianCoordinate(22.3, 114.1),
                    PedestrianCoordinate(22.31, 114.11)
                )
            )
        )
    )
}
