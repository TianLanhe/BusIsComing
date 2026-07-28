package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.RouteFingerprintResolution
import com.golink.busiscoming.ui.main.RouteCardItem
import com.golink.busiscoming.ui.main.RouteListViewportAnchor
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteListViewportAnchorTest {
    @Test
    fun `deep list move restores the same first visible identity instead of scrolling top`() {
        val original = (1..40).map(::item)
        val anchor = original[24]
        val moved = listOf(original[34].copy(pinLevel = PinLevel.TEMPORARY, pinnedAt = 99L)) +
            original.filterNot { it.stableId == original[34].stableId }

        val restoredPosition = RouteListViewportAnchor.positionOf(moved, anchor.stableId)

        assertEquals(25, restoredPosition)
        assertEquals(anchor.stableId, moved[restoredPosition].stableId)
    }

    private fun item(index: Int) = RouteCardItem(
        route = BusRouteOption(
            routeName = "R$index",
            routeSegments = listOf("R$index"),
            priceHkd = index.toDouble(),
            durationMinutes = index,
            arrivalMinutes = index,
            transferCount = 0,
            walkingDistanceMeters = index
        ),
        fingerprintResolution = RouteFingerprintResolution.Eligible("v1|$index"),
        pinLevel = PinLevel.UNPINNED,
        pinnedAt = null,
        stableId = "route:v1|$index"
    )
}
