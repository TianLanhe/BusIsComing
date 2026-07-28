package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.RouteFingerprintResolution
import com.golink.busiscoming.data.model.RouteFingerprintFormatter
import com.golink.busiscoming.data.model.SortDirection
import com.golink.busiscoming.data.model.SortField

sealed interface BusRouteListItem {
    val stableId: String
}

data class RouteCardItem(
    val route: BusRouteOption,
    val fingerprintResolution: RouteFingerprintResolution,
    val pinLevel: PinLevel,
    val pinnedAt: Long?,
    override val stableId: String
) : BusRouteListItem {
    val fingerprint: String?
        get() = (fingerprintResolution as? RouteFingerprintResolution.Eligible)?.fingerprint

    val isPinEligible: Boolean
        get() = fingerprint != null
}

data class UnpinnedDividerItem(
    val unpinnedCount: Int,
    val sortField: SortField,
    val sortDirection: SortDirection,
    override val stableId: String
) : BusRouteListItem

object SearchRouteItemProjector {
    fun project(routes: List<BusRouteOption>): List<RouteCardItem> {
        return RouteFingerprintFormatter.resolve(routes).mapIndexed { index, resolution ->
            val strictFingerprint =
                (resolution as? RouteFingerprintResolution.Eligible)?.fingerprint
            RouteCardItem(
                route = routes[index],
                fingerprintResolution = resolution,
                pinLevel = PinLevel.UNPINNED,
                pinnedAt = null,
                stableId = strictFingerprint?.let { "route:$it" }
                    ?: "fallback:${routes[index].resultId}:$index"
            )
        }
    }
}
