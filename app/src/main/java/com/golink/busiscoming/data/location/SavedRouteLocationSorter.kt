package com.golink.busiscoming.data.location

import com.golink.busiscoming.data.model.RouteConfig

object SavedRouteLocationSorter {
    fun sort(
        routes: List<RouteConfig>,
        location: CurrentLocationSnapshot?
    ): List<RouteConfig> {
        if (location == null || routes.size < 2) return routes

        return routes.withIndex()
            .sortedWith { left, right ->
                compareEntries(location, left, right)
            }
            .map { it.value }
    }

    private fun compareEntries(
        location: CurrentLocationSnapshot,
        left: IndexedValue<RouteConfig>,
        right: IndexedValue<RouteConfig>
    ): Int {
        val distanceComparison = distanceMeters(location, left.value)
            .compareTo(distanceMeters(location, right.value))
        if (distanceComparison != 0) return distanceComparison

        val usageComparison = right.value.usageCount.compareTo(left.value.usageCount)
        if (usageComparison != 0) return usageComparison

        val leftLastUsedAt = left.value.lastUsedAt ?: Long.MIN_VALUE
        val rightLastUsedAt = right.value.lastUsedAt ?: Long.MIN_VALUE
        val lastUsedComparison = rightLastUsedAt.compareTo(leftLastUsedAt)
        if (lastUsedComparison != 0) return lastUsedComparison

        return left.index.compareTo(right.index)
    }

    private fun distanceMeters(
        location: CurrentLocationSnapshot,
        route: RouteConfig
    ): Int {
        return GeoDistanceCalculator.distanceMeters(
            fromLatitude = location.latitude,
            fromLongitude = location.longitude,
            toLatitude = route.origin.latitude,
            toLongitude = route.origin.longitude
        )
    }
}
