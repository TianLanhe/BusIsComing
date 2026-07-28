package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.RouteFingerprintFormatter
import com.golink.busiscoming.data.model.RouteFingerprintResolution
import com.golink.busiscoming.data.model.RoutePinRecord
import com.golink.busiscoming.data.model.SortDirection
import com.golink.busiscoming.data.model.SortField
import com.golink.busiscoming.data.repository.BusRouteSorter
import java.util.IdentityHashMap

object PinnedRouteProjector {
    fun project(
        routes: List<BusRouteOption>,
        pins: List<RoutePinRecord>,
        sortField: SortField,
        sortDirection: SortDirection,
        scopeKey: String
    ): List<BusRouteListItem> {
        if (routes.isEmpty()) return emptyList()
        val pinByFingerprint = pins.associateBy { it.fingerprint }
        val resolved = RouteFingerprintFormatter.resolve(routes).mapIndexed { index, resolution ->
            ResolvedRoute(index, routes[index], resolution)
        }
        val pinned = resolved.mapNotNull { route ->
            val fingerprint = (route.resolution as? RouteFingerprintResolution.Eligible)
                ?.fingerprint
                ?: return@mapNotNull null
            pinByFingerprint[fingerprint]?.let { record -> route to record }
        }.sortedByDescending { it.second.pinnedAt }
        val pinnedIndexes = pinned.mapTo(hashSetOf()) { it.first.sourceIndex }
        val unpinned = resolved.filterNot { it.sourceIndex in pinnedIndexes }
        val resolvedByRoute = IdentityHashMap<BusRouteOption, ResolvedRoute>().apply {
            unpinned.forEach { put(it.route, it) }
        }
        val sortedUnpinned = BusRouteSorter.sort(
            unpinned.map { it.route },
            sortField,
            sortDirection
        ).map { resolvedByRoute.getValue(it) }

        return buildList {
            pinned.forEach { (resolvedRoute, record) ->
                add(resolvedRoute.toItem(record.level, record.pinnedAt))
            }
            if (pinned.isNotEmpty() && sortedUnpinned.isNotEmpty()) {
                add(
                    UnpinnedDividerItem(
                        unpinnedCount = sortedUnpinned.size,
                        sortField = sortField,
                        sortDirection = sortDirection,
                        stableId = "divider:$scopeKey"
                    )
                )
            }
            sortedUnpinned.forEach { route ->
                add(route.toItem(PinLevel.UNPINNED, null))
            }
        }
    }

    private data class ResolvedRoute(
        val sourceIndex: Int,
        val route: BusRouteOption,
        val resolution: RouteFingerprintResolution
    ) {
        fun toItem(level: PinLevel, pinnedAt: Long?): RouteCardItem {
            val strictFingerprint =
                (resolution as? RouteFingerprintResolution.Eligible)?.fingerprint
            val stableId = strictFingerprint?.let { "route:$it" }
                ?: "fallback:${route.resultId}:$sourceIndex"
            return RouteCardItem(
                route = route,
                fingerprintResolution = resolution,
                pinLevel = level,
                pinnedAt = pinnedAt,
                stableId = stableId
            )
        }
    }
}
