package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.EtaArrival
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.P2pRouteRecoveryContext
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
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
                ),
                sessionRef = "opaque-session-reference",
                recoveryContext = P2pRouteRecoveryContext(
                    originLatitude = 22.29361,
                    originLongitude = 114.20056,
                    destinationLatitude = 22.28190,
                    destinationLongitude = 114.15815,
                    searchMode = "F"
                )
            )
        )

        val original = RouteDetailLaunchArgs.fromRoute(
            route = route,
            queryOrigin = Place("北角碼頭", 22.29361, 114.20056),
            queryDestination = Place("中環", 22.28190, 114.15815)
        )
        val restored = requireNotNull(RouteDetailLaunchArgs.fromPrimitiveValues(original.toPrimitiveValues()))

        assertNotSame(original, restored)
        assertEquals(original, restored)
        assertEquals(listOf(6, 14), (restored.waitTimeState as WaitTimeState.Available).arrivals.map { it.minutes })
        assertEquals("北角碼頭", restored.queryOrigin?.name)
        assertEquals(22.29361, restored.queryOrigin?.latitude ?: 0.0, 0.0)
        assertEquals("中環", restored.queryDestination?.name)
        assertEquals(114.15815, restored.queryDestination?.longitude ?: 0.0, 0.0)
        assertEquals("opaque-session-reference", restored.routeDetailQuery?.sessionRef)
        assertEquals("F", restored.routeDetailQuery?.recoveryContext?.searchMode)
        assertEquals(22.29361, restored.routeDetailQuery?.recoveryContext?.originLatitude ?: 0.0, 0.0)
        assertFalse(original.toPrimitiveValues().values.any { it.contains("PHPSESSID") })
    }

    @Test
    fun oldAndPartiallyMissingEndpointSnapshotsRemainCompatible() {
        val oldValues = mapOf(
            "routeName" to "118",
            "routeSegmentCount" to "1",
            "routeSegment.0" to "118",
            "priceHkd" to "12.3",
            "durationMinutes" to "32",
            "walkingDistanceMeters" to "240",
            "wait.type" to "loading"
        )

        val oldRestored = requireNotNull(RouteDetailLaunchArgs.fromPrimitiveValues(oldValues))
        assertNull(oldRestored.queryOrigin)
        assertNull(oldRestored.queryDestination)

        val partialRestored = requireNotNull(
            RouteDetailLaunchArgs.fromPrimitiveValues(
                oldValues + mapOf(
                    "query.origin.present" to "true",
                    "query.origin.name" to "缺少座標",
                    "query.origin.latitude" to "22.3"
                )
            )
        )
        assertNull(partialRestored.queryOrigin)
        assertEquals(oldRestored.routeName, partialRestored.routeName)
    }
}
