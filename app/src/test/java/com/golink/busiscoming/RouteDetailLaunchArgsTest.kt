package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.EtaArrival
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class RouteDetailLaunchArgsTest {
    @Test
    fun primitiveSnapshotRoundTripsAllInputsNeededAfterProcessRecreation() {
        val route = BusRouteOption(
            routeName = "N8P → N969",
            routeSegments = listOf("N8P", "N969"),
            priceHkd = 51.2,
            durationMinutes = 49,
            arrivalMinutes = 6,
            transferCount = 1,
            walkingDistanceMeters = 378,
            waitTimeState = WaitTimeState.Available(listOf(EtaArrival(1, 6), EtaArrival(2, 14))),
            firstLegEtaQuery = FirstLegEtaQuery("CTB", "N8P-ISR-1", "N8P", 6, 15, "O", "outbound", "raw", "0"),
            routeDetailQuery = P2pRouteDetailQuery(
                "raw",
                "01:21|*|49",
                "0",
                "0",
                P2pRoutePlan(
                    "raw",
                    "0",
                    listOf(
                        P2pRouteLeg("CTB", "N8P-ISR-1", "N8P", 6, 15, "O", "outbound"),
                        P2pRouteLeg("CTB", "N969-ISR-1", "N969", 10, 17, "O", "outbound")
                    )
                )
            )
        )

        val original = RouteDetailLaunchArgs.fromRoute(route)
        val restored = requireNotNull(RouteDetailLaunchArgs.fromPrimitiveValues(original.toPrimitiveValues()))

        assertNotSame(original, restored)
        assertEquals(original, restored)
        assertEquals(14, restored.estimatedViaStopCount)
        assertEquals(listOf(6, 14), (restored.waitTimeState as WaitTimeState.Available).arrivals.map { it.minutes })
    }
}
