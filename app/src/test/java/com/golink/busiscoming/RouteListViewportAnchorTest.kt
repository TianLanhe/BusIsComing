package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.RouteFingerprintResolution
import com.golink.busiscoming.ui.main.RouteCardItem
import com.golink.busiscoming.ui.main.RouteListViewportAnchor
import com.golink.busiscoming.ui.main.RoutePinAction
import com.golink.busiscoming.ui.main.RoutePinViewportMode
import com.golink.busiscoming.ui.main.RoutePinViewportPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteListViewportAnchorTest {
    @Test
    fun `preserve mode restores the same first visible identity after a list move`() {
        val original = (1..40).map(::item)
        val anchor = original[24]
        val moved = listOf(original[34].copy(pinLevel = PinLevel.TEMPORARY, pinnedAt = 99L)) +
            original.filterNot { it.stableId == original[34].stableId }

        val restoredPosition = RouteListViewportAnchor.positionOf(moved, anchor.stableId)

        assertEquals(25, restoredPosition)
        assertEquals(anchor.stableId, moved[restoredPosition].stableId)
    }

    @Test
    fun `only the first temporary pin reveals the pinned top`() {
        assertEquals(
            RoutePinViewportMode.REVEAL_PINNED_TOP,
            RoutePinViewportPolicy.after(RoutePinAction.PIN_TEMPORARY)
        )
        assertEquals(
            RoutePinViewportMode.PRESERVE_ANCHOR,
            RoutePinViewportPolicy.after(RoutePinAction.PIN_PERSISTENT)
        )
        assertEquals(
            RoutePinViewportMode.PRESERVE_ANCHOR,
            RoutePinViewportPolicy.after(RoutePinAction.CANCEL)
        )
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
