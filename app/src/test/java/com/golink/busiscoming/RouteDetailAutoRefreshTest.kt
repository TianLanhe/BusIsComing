package com.golink.busiscoming

import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailCompleteness
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.ui.main.RouteDetailDynamicMerger
import com.golink.busiscoming.ui.main.RouteDetailRefreshCycleCoordinator
import com.golink.busiscoming.ui.main.RouteDetailRefreshDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailAutoRefreshTest {
    @Test
    fun `cycle waits for both domains and accepts every domain once`() {
        val cycle = RouteDetailRefreshCycleCoordinator()
        cycle.begin(7)

        val eta = cycle.finish(7, RouteDetailRefreshDomain.FIRST_LEG_ETA, success = true)
        assertTrue(eta.accepted)
        assertFalse(eta.finished)
        assertTrue(eta.anyDomainSucceeded)

        val detail = cycle.finish(7, RouteDetailRefreshDomain.DYNAMIC_DETAIL, success = false)
        assertTrue(detail.accepted)
        assertTrue(detail.finished)
        assertTrue(detail.anyDomainSucceeded)
        assertFalse(cycle.finish(7, RouteDetailRefreshDomain.DYNAMIC_DETAIL, true).accepted)
        assertFalse(cycle.finish(6, RouteDetailRefreshDomain.FIRST_LEG_ETA, true).accepted)
    }

    @Test
    fun `dynamic merge changes only times and fares`() {
        val current = detail()
        val candidate = detail().copy(
            priceHkd = 15.0,
            durationMinutes = 42,
            walkingDistanceMeters = 999,
            plannedDepartureTime = "10:00",
            plannedArrivalTime = "10:42",
            legs = listOf(
                current.legs.single().copy(
                    fareHkd = 15.0,
                    plannedBoardingTime = "10:03",
                    plannedAlightingTime = "10:38"
                )
            )
        )

        val merged = RouteDetailDynamicMerger.merge(current, candidate)
        assertNotNull(merged)
        requireNotNull(merged)

        assertEquals(15.0, merged.priceHkd, 0.0)
        assertEquals(42, merged.durationMinutes)
        assertEquals(100, merged.walkingDistanceMeters)
        assertEquals(current.legs.single().viaStops, merged.legs.single().viaStops)
        assertEquals("10:03", merged.legs.single().plannedBoardingTime)
    }

    @Test
    fun `incomplete or mismatched stable structure is rejected`() {
        val current = detail()

        assertNull(
            RouteDetailDynamicMerger.merge(
                current,
                current.copy(completeness = RouteDetailCompleteness.PARTIAL)
            )
        )
        assertNull(
            RouteDetailDynamicMerger.merge(
                current,
                current.copy(
                    legs = listOf(
                        current.legs.single().copy(
                            boardingStop = current.legs.single().boardingStop.copy(stopId = "other")
                        )
                    )
                )
            )
        )
    }

    private fun detail(): RouteDetail {
        val boarding = stop("a", 1, RouteDetailStopRole.BOARDING)
        val via = stop("b", 2, RouteDetailStopRole.VIA)
        val alighting = stop("c", 3, RouteDetailStopRole.ALIGHTING)
        return RouteDetail(
            routeName = "970X",
            priceHkd = 12.0,
            durationMinutes = 40,
            walkingDistanceMeters = 100,
            legs = listOf(
                RouteDetailLeg(
                    route = "970X",
                    routeVariant = "v1",
                    directionText = "Central",
                    boardingStop = boarding,
                    viaStops = listOf(via),
                    alightingStop = alighting,
                    fareHkd = 12.0
                )
            ),
            completeness = RouteDetailCompleteness.COMPLETE
        )
    }

    private fun stop(id: String, sequence: Int, role: RouteDetailStopRole) = RouteDetailStop(
        rawName = id,
        displayName = id,
        stopId = id,
        sequence = sequence,
        latitude = 22.3 + sequence / 1000.0,
        longitude = 114.1 + sequence / 1000.0,
        routeVariant = "v1",
        role = role
    )
}
