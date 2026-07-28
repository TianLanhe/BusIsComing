package com.golink.busiscoming.ui.main

object RouteListViewportAnchor {
    fun positionOf(items: List<BusRouteListItem>, stableId: String): Int =
        items.indexOfFirst { it.stableId == stableId }
}
