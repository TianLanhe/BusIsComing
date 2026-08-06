package com.golink.busiscoming.ui.main

object RouteListViewportAnchor {
    fun positionOf(items: List<BusRouteListItem>, stableId: String): Int =
        items.indexOfFirst { it.stableId == stableId }

    fun positionAfterRefresh(
        oldItems: List<BusRouteListItem>,
        newItems: List<BusRouteListItem>,
        anchorStableId: String?,
        oldPosition: Int
    ): Int {
        if (newItems.isEmpty()) return -1
        anchorStableId?.let { stableId ->
            newItems.indexOfFirst { it.stableId == stableId }
                .takeIf { it >= 0 }
                ?.let { return it }
        }
        val resolvedOldPosition = when {
            oldPosition >= 0 -> oldPosition
            anchorStableId != null -> oldItems.indexOfFirst { it.stableId == anchorStableId }
            else -> 0
        }
        return resolvedOldPosition.coerceIn(0, newItems.lastIndex)
    }
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
