package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.EtaArrival
import com.golink.busiscoming.data.model.EtaUnavailableReason
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.ui.main.RouteCardActionPolicy
import com.golink.busiscoming.ui.main.FirstRunRoutePreview
import com.golink.busiscoming.ui.main.RouteResultCardFormatter
import com.golink.busiscoming.ui.main.TemporaryRouteSaveDialog
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.ui.common.LocalizedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteResultCardFormatterTest {
    @Test
    fun formatsWaitStatusText() {
        assertEquals("等候 4 分鐘", RouteResultCardFormatter.waitStatus(WaitTimeState.Available(4), text))
        assertEquals("候車查詢中", RouteResultCardFormatter.waitStatus(WaitTimeState.Loading, text))
        assertEquals("暫無車輛", RouteResultCardFormatter.waitStatus(WaitTimeState.NoArrivals, text))
        assertEquals(
            "候車暫不可用",
            RouteResultCardFormatter.waitStatus(
                WaitTimeState.Unavailable(EtaUnavailableReason.ETA_REQUEST_FAILED),
                text
            )
        )
    }

    @Test
    fun formatsImmediateAndNextArrivalText() {
        val state = WaitTimeState.Available(
            listOf(
                EtaArrival(sequence = 1, minutes = 0),
                EtaArrival(sequence = 2, minutes = 6)
            )
        )

        assertEquals("即將到站", RouteResultCardFormatter.waitStatus(state, text))
        assertEquals("下一班 6 分鐘 ›", RouteResultCardFormatter.nextArrivalStatus(state, text))
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

        assertEquals("HK$ 20.4 · 耗時 34 分鐘 · 步行 456 米", RouteResultCardFormatter.info(route, text))
    }

    @Test
    fun exposesEtaSheetOnlyWhenMultipleArrivalsExist() {
        assertFalse(RouteCardActionPolicy.canOpenEtaArrivals(WaitTimeState.Available(4)))
        assertFalse(RouteCardActionPolicy.canOpenEtaArrivals(WaitTimeState.Loading))
        assertFalse(RouteCardActionPolicy.canOpenEtaArrivals(WaitTimeState.NoArrivals))
        assertFalse(
            RouteCardActionPolicy.canOpenEtaArrivals(
                WaitTimeState.Unavailable(EtaUnavailableReason.ETA_REQUEST_FAILED)
            )
        )
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
                    waitTimeState = WaitTimeState.Unavailable(EtaUnavailableReason.ETA_REQUEST_FAILED),
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

        assertEquals("共 3 條路線，2 條直達", RouteResultCardFormatter.resultSummary(routes, text))
    }

    @Test
    fun firstRunPreviewUsesRealRouteCardFormatting() {
        val route = FirstRunRoutePreview.route()

        assertEquals("118", route.routeName)
        assertEquals("等候 4 分鐘", RouteResultCardFormatter.waitStatus(route.waitTimeState, text))
        assertEquals("下一班 11 分鐘 ›", RouteResultCardFormatter.nextArrivalStatus(route.waitTimeState, text))
        assertEquals("Chai Wan  →  Central", route.stopPreview?.displayText())
        assertEquals("HK$ 11.8 · 耗時 38 分鐘 · 步行 160 米", RouteResultCardFormatter.info(route, text))
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

    private val text = LocalizedText { resourceId, args ->
        when (resourceId) {
            R.string.price_free -> "免費"
            R.string.price_hkd -> "HK$ %.1f".format(java.util.Locale.US, args[0])
            R.string.eta_due -> "即將到站"
            R.string.eta_wait -> "等候 ${args[0]} 分鐘"
            R.string.eta_loading -> "候車查詢中"
            R.string.eta_unavailable -> "暫無車輛"
            R.string.eta_temporarily_unavailable -> "候車暫不可用"
            R.string.minutes_count -> "${args[0]} 分鐘"
            R.string.eta_next -> "下一班 ${args[0]} ›"
            R.string.route_card_summary -> "${args[0]} · 耗時 ${args[1]} 分鐘 · 步行 ${args[2]} 米"
            R.string.route_results_summary -> "共 ${args[0]} 條路線，${args[1]} 條直達"
            else -> error("Unexpected resource $resourceId")
        }
    }
}
