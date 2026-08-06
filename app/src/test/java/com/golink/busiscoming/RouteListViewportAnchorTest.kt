package com.golink.busiscoming

import com.golink.busiscoming.data.model.SortDirection
import com.golink.busiscoming.data.model.SortField
import com.golink.busiscoming.ui.main.BusRouteListItem
import com.golink.busiscoming.ui.main.RouteListViewportAnchor
import com.golink.busiscoming.ui.main.UnpinnedDividerItem
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteListViewportAnchorTest {
    @Test
    fun `keeps matching stable route after reorder`() {
        val oldItems = listOf(item("a"), item("b"), item("c"))
        val newItems = listOf(item("c"), item("a"), item("b"))

        assertEquals(
            2,
            RouteListViewportAnchor.positionAfterRefresh(oldItems, newItems, "route:b", 1)
        )
    }

    @Test
    fun `uses nearest following position when route disappears`() {
        val oldItems = listOf(item("a"), item("b"), item("c"))
        val newItems = listOf(item("a"), item("c"))

        assertEquals(
            1,
            RouteListViewportAnchor.positionAfterRefresh(oldItems, newItems, "route:b", 1)
        )
    }

    @Test
    fun `clamps fallback and reports empty list`() {
        val oldItems = listOf(item("a"), item("b"), item("c"))

        assertEquals(
            0,
            RouteListViewportAnchor.positionAfterRefresh(oldItems, listOf(item("a")), "route:c", 2)
        )
        assertEquals(
            -1,
            RouteListViewportAnchor.positionAfterRefresh(oldItems, emptyList(), "route:b", 1)
        )
    }

    private fun item(id: String): BusRouteListItem = UnpinnedDividerItem(
        unpinnedCount = 1,
        sortField = SortField.ROUTE,
        sortDirection = SortDirection.ASC,
        stableId = "route:$id"
    )
}
