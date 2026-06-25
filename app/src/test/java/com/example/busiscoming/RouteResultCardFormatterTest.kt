package com.example.busiscoming

import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.EtaArrival
import com.example.busiscoming.data.model.FirstLegEtaQuery
import com.example.busiscoming.data.model.WaitTimeState
import com.example.busiscoming.ui.main.RouteCardActionPolicy
import com.example.busiscoming.ui.main.FirstRunRoutePreview
import com.example.busiscoming.ui.main.RouteResultCardFormatter
import com.example.busiscoming.ui.main.TemporaryRouteSaveDialog
import com.example.busiscoming.data.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteResultCardFormatterTest {
    @Test
    fun formatsWaitStatusText() {
        assertEquals("等候 4 分鐘", RouteResultCardFormatter.waitStatus(WaitTimeState.Available(4)))
        assertEquals("候車查詢中", RouteResultCardFormatter.waitStatus(WaitTimeState.Loading))
        assertEquals("暫無車輛", RouteResultCardFormatter.waitStatus(WaitTimeState.Unavailable))
    }

    @Test
    fun formatsImmediateAndNextArrivalText() {
        val state = WaitTimeState.Available(
            listOf(
                EtaArrival(sequence = 1, minutes = 0),
                EtaArrival(sequence = 2, minutes = 6)
            )
        )

        assertEquals("即將到站", RouteResultCardFormatter.waitStatus(state))
        assertEquals("下一班 6 分鐘 ›", RouteResultCardFormatter.nextArrivalStatus(state))
    }

    @Test
    fun formatsBottomInfoWithPriceDurationAndWalkingDistance() {
        val route = BusRouteOption(
            routeName = "82X \u2192 102",
            routeSegments = listOf("82X", "102"),
            priceHkd = 20.4,
            durationMinutes = 34,
            arrivalMinutes = 34,
            transferCount = 1,
            walkingDistanceMeters = 456
        )

        assertEquals("HK$ 20.4 · 耗時 34 分鐘 · 步行 456 米", RouteResultCardFormatter.info(route))
    }

    @Test
    fun exposesEtaSheetOnlyWhenMultipleArrivalsExist() {
        assertFalse(RouteCardActionPolicy.canOpenEtaArrivals(WaitTimeState.Available(4)))
        assertFalse(RouteCardActionPolicy.canOpenEtaArrivals(WaitTimeState.Loading))
        assertFalse(RouteCardActionPolicy.canOpenEtaArrivals(WaitTimeState.Unavailable))
        assertTrue(
            RouteCardActionPolicy.canOpenEtaArrivals(
                WaitTimeState.Available(
                    listOf(
                        EtaArrival(sequence = 1, minutes = 4),
                        EtaArrival(sequence = 2, minutes = 8)
                    )
                )
            )
        )
    }

    @Test
    fun monitorBellIsEnabledOnlyForAvailableEtaQuery() {
        assertFalse(RouteCardActionPolicy.canStartMonitor(route("8X", transferCount = 0)))
        assertFalse(
            RouteCardActionPolicy.canStartMonitor(
                route("8X", transferCount = 0).copy(
                    waitTimeState = WaitTimeState.Unavailable,
                    firstLegEtaQuery = etaQuery()
                )
            )
        )
        assertTrue(
            RouteCardActionPolicy.canStartMonitor(
                route("8X", transferCount = 0).copy(firstLegEtaQuery = etaQuery())
            )
        )
    }

    @Test
    fun temporaryRouteSaveDialogUsesStableDefaultName() {
        assertEquals(
            "起點 -> 終點",
            TemporaryRouteSaveDialog.defaultName(
                Place("起點", latitude = 22.1, longitude = 114.1),
                Place("終點", latitude = 22.2, longitude = 114.2)
            )
        )
    }

    @Test
    fun formatsResultSummary() {
        val routes = listOf(
            route("8X", transferCount = 0),
            route("82X \u2192 102", transferCount = 1),
            route("106", transferCount = 0)
        )

        assertEquals("共 3 條路線，2 條直達", RouteResultCardFormatter.resultSummary(routes))
    }

    @Test
    fun firstRunPreviewUsesRealRouteCardFormatting() {
        val route = FirstRunRoutePreview.route()

        assertEquals("118", route.routeName)
        assertEquals("等候 4 分鐘", RouteResultCardFormatter.waitStatus(route.waitTimeState))
        assertEquals("下一班 11 分鐘 ›", RouteResultCardFormatter.nextArrivalStatus(route.waitTimeState))
        assertEquals("柴灣  →  中環", route.stopPreview?.displayText())
        assertEquals("HK$ 11.8 · 耗時 38 分鐘 · 步行 160 米", RouteResultCardFormatter.info(route))
        assertFalse(RouteCardActionPolicy.canStartMonitor(route))
        assertTrue(RouteCardActionPolicy.canOpenEtaArrivals(route.waitTimeState))
    }

    private fun route(name: String, transferCount: Int): BusRouteOption {
        return BusRouteOption(
            routeName = name,
            routeSegments = name.split(" \u2192 "),
            priceHkd = 1.0,
            durationMinutes = 10,
            arrivalMinutes = 10,
            transferCount = transferCount,
            walkingDistanceMeters = 100
        )
    }

    private fun etaQuery(): FirstLegEtaQuery {
        return FirstLegEtaQuery(
            company = "CTB",
            routeVariant = "8X-THR-1",
            route = "8X",
            boardingSeq = 6,
            alightingSeq = 31,
            bound = "O",
            directionPath = "outbound",
            rawInfo = "1|*|CTB||8X-THR-1||6||31||O|*|",
            lang = "0"
        )
    }
}
