package com.golink.busiscoming

import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailTransfer
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.RouteDetailWalkingKind
import com.golink.busiscoming.data.model.RouteDetailWalkingSegment
import com.golink.busiscoming.data.model.EtaUnavailableReason
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.ui.main.RouteDetailUiFormatter
import com.golink.busiscoming.ui.main.RouteDetailUiItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailUiFormatterTest {
    @Test
    fun summaryUsesCompleteWalkingDistanceAndTotalViaStops() {
        val items = RouteDetailUiFormatter.items(detail(), emptySet(), WaitTimeState.Available(6))
        val summary = items.filterIsInstance<RouteDetailUiItem.Summary>().single()

        assertEquals(14, summary.totalViaStops)
        assertEquals(403, summary.walkingDistanceMeters)
        assertTrue(summary.isWalkingDistanceComplete)
        assertEquals("01:21", summary.plannedArrivalTime)
    }

    @Test
    fun eachLegExpandsIndependentlyOutsideItsBusCardAndOnlyFirstLegHasLiveEta() {
        val collapsed = RouteDetailUiFormatter.items(detail(), emptySet(), WaitTimeState.Available(6))
        assertTrue(collapsed.none { it is RouteDetailUiItem.ViaStop })
        assertEquals(2, collapsed.filterIsInstance<RouteDetailUiItem.ViaToggle>().size)

        val expanded = RouteDetailUiFormatter.items(detail(), setOf(1), WaitTimeState.Available(6))
        assertEquals(6, expanded.filterIsInstance<RouteDetailUiItem.ViaStop>().size)
        val busLegs = expanded.filterIsInstance<RouteDetailUiItem.BusLeg>()
        assertEquals(6, (busLegs.first().liveEta as WaitTimeState.Available).minutes)
        assertNull(busLegs[1].liveEta)
        assertFalse(busLegs[0].colorKey == busLegs[1].colorKey)
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
    fun everyWaitStateStaysOnTheFirstLegOnly() {
        val states = buildList<WaitTimeState> {
            add(WaitTimeState.Loading)
            add(WaitTimeState.NoArrivals)
            EtaUnavailableReason.entries.forEach { add(WaitTimeState.Unavailable(it)) }
        }

        states.forEach { state ->
            val legs = RouteDetailUiFormatter.items(detail(), emptySet(), state)
                .filterIsInstance<RouteDetailUiItem.BusLeg>()
            assertEquals(state, legs.first().liveEta)
            assertNull(legs[1].liveEta)
        }
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
}
