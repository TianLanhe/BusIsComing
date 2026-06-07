package com.example.busiscoming

import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.WaitTimeState
import com.example.busiscoming.ui.main.RouteResultCardFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteResultCardFormatterTest {
    @Test
    fun formatsWaitStatusText() {
        assertEquals("等候 4 分鐘", RouteResultCardFormatter.waitStatus(WaitTimeState.Available(4)))
        assertEquals("候車查詢中", RouteResultCardFormatter.waitStatus(WaitTimeState.Loading))
        assertEquals("暫無車輛", RouteResultCardFormatter.waitStatus(WaitTimeState.Unavailable))
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
    fun formatsResultSummary() {
        val routes = listOf(
            route("8X", transferCount = 0),
            route("82X \u2192 102", transferCount = 1),
            route("106", transferCount = 0)
        )

        assertEquals("共 3 條路線，2 條直達", RouteResultCardFormatter.resultSummary(routes))
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
}
