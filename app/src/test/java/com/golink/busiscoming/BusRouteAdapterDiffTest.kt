package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.RouteFingerprintResolution
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.data.model.SortDirection
import com.golink.busiscoming.data.model.SortField
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.model.WalkingDistanceDisplayState
import com.golink.busiscoming.ui.main.BusRouteItemDiff
import com.golink.busiscoming.ui.main.RouteCardItem
import com.golink.busiscoming.ui.main.RouteQueryState
import com.golink.busiscoming.ui.main.SearchRouteItemProjector
import com.golink.busiscoming.ui.main.UnpinnedDividerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusRouteAdapterDiffTest {
    @Test
    fun routeIdentityStaysStableAcrossEtaAndPinMovesWhileContentChanges() {
        val original = card(route("118", WaitTimeState.Loading), PinLevel.UNPINNED)
        val updated = card(
            route("118", WaitTimeState.Available(4)),
            PinLevel.TEMPORARY
        )

        assertTrue(BusRouteItemDiff.areItemsTheSame(original, updated))
        assertFalse(BusRouteItemDiff.areContentsTheSame(original, updated))

        val stopUpdated = original.copy(
            route = original.route.copy(
                stopPreview = RouteCardStopPreview("起點站", "終點站")
            )
        )
        assertTrue(BusRouteItemDiff.areItemsTheSame(original, stopUpdated))
        assertFalse(BusRouteItemDiff.areContentsTheSame(original, stopUpdated))

        val walkingUpdated = original.copy(
            route = original.route.copy(
                walkingDistanceDisplayState = WalkingDistanceDisplayState.CsdiSuccess(88)
            )
        )
        assertTrue(BusRouteItemDiff.areItemsTheSame(original, walkingUpdated))
        assertFalse(BusRouteItemDiff.areContentsTheSame(original, walkingUpdated))
    }

    @Test
    fun dividerUsesScopeIdentityAndUpdatesItsSentenceInputs() {
        val original = UnpinnedDividerItem(
            unpinnedCount = 10,
            sortField = SortField.DURATION,
            sortDirection = SortDirection.ASC,
            stableId = "divider:journey:1"
        )
        val updated = original.copy(unpinnedCount = 9, sortDirection = SortDirection.DESC)
        val anotherQuery = original.copy(stableId = "divider:journey:1:query:2")

        assertTrue(BusRouteItemDiff.areItemsTheSame(original, updated))
        assertFalse(BusRouteItemDiff.areContentsTheSame(original, updated))
        assertFalse(BusRouteItemDiff.areItemsTheSame(original, anotherQuery))
    }

    @Test
    fun searchProjectionKeepsPureCardOrderAndNeverCreatesDividerOrPinState() {
        val routes = listOf(route("8X"), route("118"))

        val items = SearchRouteItemProjector.project(routes)

        assertEquals(routes, items.map { it.route })
        assertTrue(items.all { it.pinLevel == PinLevel.UNPINNED })
        assertTrue(items.none { it.stableId.startsWith("divider:") })
    }

    @Test
    fun searchQuerySortingStillReordersEveryResultBeforePureCardProjection() {
        val state = RouteQueryState()
        val slow = route("slow").copy(durationMinutes = 40)
        val fast = route("fast").copy(durationMinutes = 10)

        state.complete(listOf(slow, fast), preserveSort = false, updatedAtMillis = 1L)
        val items = SearchRouteItemProjector.project(state.results)

        assertEquals(listOf("fast", "slow"), items.map { it.route.routeName })
        assertTrue(items.all { it.pinLevel == PinLevel.UNPINNED })
    }

    private fun card(route: BusRouteOption, level: PinLevel) = RouteCardItem(
        route = route,
        fingerprintResolution = RouteFingerprintResolution.Eligible("v1|stable"),
        pinLevel = level,
        pinnedAt = if (level == PinLevel.UNPINNED) null else 1L,
        stableId = "route:v1|stable"
    )

    private fun route(
        name: String,
        waitTime: WaitTimeState = WaitTimeState.Loading
    ) = BusRouteOption(
        routeName = name,
        routeSegments = listOf(name),
        priceHkd = 10.0,
        durationMinutes = 30,
        arrivalMinutes = 0,
        transferCount = 0,
        walkingDistanceMeters = 100,
        waitTimeState = waitTime
    )
}
