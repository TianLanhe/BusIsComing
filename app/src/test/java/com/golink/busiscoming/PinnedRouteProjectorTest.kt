package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.RouteFingerprintResolution
import com.golink.busiscoming.data.model.RoutePinRecord
import com.golink.busiscoming.data.model.SortDirection
import com.golink.busiscoming.data.model.SortField
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.ui.main.PinnedRouteProjector
import com.golink.busiscoming.ui.main.RouteCardItem
import com.golink.busiscoming.ui.main.UnpinnedDividerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedRouteProjectorTest {
    @Test
    fun `projector keeps pins newest first and sorts only unpinned routes`() {
        val routes = listOf(
            route("a", duration = 40),
            route("b", duration = 30),
            route("c", duration = 20),
            route("d", duration = 10)
        )
        val records = listOf(
            pin("a", PinLevel.TEMPORARY, 100L),
            pin("b", PinLevel.PERSISTENT, 200L)
        )

        val items = PinnedRouteProjector.project(
            routes = routes,
            pins = records,
            sortField = SortField.DURATION,
            sortDirection = SortDirection.ASC,
            scopeKey = "journey-1"
        )

        assertEquals(
            listOf("b", "a", "divider", "d", "c"),
            items.map {
                when (it) {
                    is RouteCardItem -> it.route.routeName
                    is UnpinnedDividerItem -> "divider"
                }
            }
        )
        val divider = items.filterIsInstance<UnpinnedDividerItem>().single()
        assertEquals(2, divider.unpinnedCount)
        assertEquals(SortField.DURATION, divider.sortField)
        assertEquals(SortDirection.ASC, divider.sortDirection)
    }

    @Test
    fun `arrival updates reorder unpinned routes without moving pinned routes`() {
        val pinned = route("pinned", waitMinutes = 30)
        val later = route("later", waitMinutes = 20)
        val sooner = route("sooner", waitMinutes = 5)

        val items = PinnedRouteProjector.project(
            routes = listOf(pinned, later, sooner),
            pins = listOf(pin("pinned", PinLevel.TEMPORARY, 10L)),
            sortField = SortField.ARRIVAL,
            sortDirection = SortDirection.ASC,
            scopeKey = "journey-1"
        )

        assertEquals(
            listOf("pinned", "sooner", "later"),
            items.filterIsInstance<RouteCardItem>().map { it.route.routeName }
        )
    }

    @Test
    fun `divider is absent when there are no pins all pins or no results`() {
        val routes = listOf(route("a"), route("b"))

        val noPins = PinnedRouteProjector.project(
            routes,
            emptyList(),
            SortField.DURATION,
            SortDirection.ASC,
            "none"
        )
        val allPins = PinnedRouteProjector.project(
            routes,
            listOf(
                pin("a", PinLevel.TEMPORARY, 2L),
                pin("b", PinLevel.PERSISTENT, 1L)
            ),
            SortField.DURATION,
            SortDirection.ASC,
            "all"
        )
        val empty = PinnedRouteProjector.project(
            emptyList(),
            listOf(pin("dormant", PinLevel.PERSISTENT, 1L)),
            SortField.DURATION,
            SortDirection.ASC,
            "empty"
        )

        assertFalse(noPins.any { it is UnpinnedDividerItem })
        assertFalse(allPins.any { it is UnpinnedDividerItem })
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `dormant records do not create cards or affect result count`() {
        val routes = listOf(route("visible"))

        val items = PinnedRouteProjector.project(
            routes,
            listOf(pin("dormant", PinLevel.PERSISTENT, 99L)),
            SortField.DURATION,
            SortDirection.ASC,
            "journey"
        )

        assertEquals(1, items.filterIsInstance<RouteCardItem>().size)
        assertEquals("visible", items.filterIsInstance<RouteCardItem>().single().route.routeName)
    }

    @Test
    fun `duplicate fingerprints remain visible but are not pin eligible`() {
        val first = route("duplicate", resultId = "first")
        val second = route("duplicate", resultId = "second")

        val cards = PinnedRouteProjector.project(
            listOf(first, second),
            listOf(pin("duplicate", PinLevel.PERSISTENT, 10L)),
            SortField.DURATION,
            SortDirection.ASC,
            "journey"
        ).filterIsInstance<RouteCardItem>()

        assertEquals(2, cards.size)
        assertTrue(cards.all { it.pinLevel == PinLevel.UNPINNED })
        assertTrue(cards.all { it.fingerprintResolution is RouteFingerprintResolution.Duplicate })
        assertEquals(2, cards.map { it.stableId }.distinct().size)
    }

    @Test
    fun `removing pin record returns route to current ordinary sort position`() {
        val routes = listOf(route("slow", duration = 30), route("fast", duration = 10))

        val cards = PinnedRouteProjector.project(
            routes,
            emptyList(),
            SortField.DURATION,
            SortDirection.ASC,
            "journey"
        ).filterIsInstance<RouteCardItem>()

        assertEquals(listOf("fast", "slow"), cards.map { it.route.routeName })
        assertTrue(cards.all { it.pinLevel == PinLevel.UNPINNED })
    }

    private fun pin(route: String, level: PinLevel, pinnedAt: Long): RoutePinRecord =
        RoutePinRecord(fingerprint(route), level, pinnedAt)

    private fun fingerprint(route: String): String {
        val length = route.length
        return "v1|1|3:CTB$length:$route$length:${route}1:O8:outbound1:12:10"
    }

    private fun route(
        route: String,
        duration: Int = 20,
        waitMinutes: Int = 5,
        resultId: String = route
    ): BusRouteOption {
        val leg = P2pRouteLeg(
            company = "CTB",
            routeVariant = route,
            route = route,
            boardingSeq = 1,
            alightingSeq = 10,
            bound = "O",
            directionPath = "outbound"
        )
        return BusRouteOption(
            routeName = route,
            routeSegments = listOf(route),
            priceHkd = 10.0,
            durationMinutes = duration,
            arrivalMinutes = waitMinutes,
            transferCount = 0,
            walkingDistanceMeters = 100,
            waitTimeState = WaitTimeState.Available(waitMinutes),
            routeDetailQuery = P2pRouteDetailQuery(
                rawInfo = "raw-$route",
                generalInfo = "general",
                listId = "list",
                lang = "0",
                plan = P2pRoutePlan(legs = listOf(leg))
            ),
            resultId = resultId
        )
    }
}
