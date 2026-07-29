package com.golink.busiscoming.ui.main

object RouteListViewportAnchor {
    fun positionOf(items: List<BusRouteListItem>, stableId: String): Int =
        items.indexOfFirst { it.stableId == stableId }
}

enum class RoutePinViewportMode {
    REVEAL_PINNED_TOP,
    PRESERVE_ANCHOR
}

object RoutePinViewportPolicy {
    fun after(action: RoutePinAction): RoutePinViewportMode {
        return if (action == RoutePinAction.PIN_TEMPORARY) {
            RoutePinViewportMode.REVEAL_PINNED_TOP
        } else {
            RoutePinViewportMode.PRESERVE_ANCHOR
        }
    }
}
