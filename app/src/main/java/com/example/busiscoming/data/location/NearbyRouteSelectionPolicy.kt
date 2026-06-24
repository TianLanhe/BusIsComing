package com.example.busiscoming.data.location

import com.example.busiscoming.data.model.RouteConfig

object NearbyRouteSelectionPolicy {
    fun selectRoute(
        location: CurrentLocationSnapshot?,
        routes: List<RouteConfig>
    ): RouteConfig? {
        if (location == null || routes.size < 2) return null
        val ranked = routes.map { route ->
            route to GeoDistanceCalculator.distanceMeters(
                fromLatitude = location.latitude,
                fromLongitude = location.longitude,
                toLatitude = route.origin.latitude,
                toLongitude = route.origin.longitude
            )
        }.sortedBy { it.second }

        val nearest = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        val accuracy = location.accuracyMeters
        if (accuracy == null || accuracy <= PRECISE_ACCURACY_METERS) {
            return nearest.first
        }
        if (second == null) return nearest.first
        val lead = second.second - nearest.second
        return if (lead >= accuracy) nearest.first else null
    }

    private const val PRECISE_ACCURACY_METERS = 500f
}
