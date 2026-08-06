package com.golink.busiscoming.data.model

import kotlin.math.ceil

data class PedestrianCoordinate(
    val latitude: Double,
    val longitude: Double
) {
    val isValidWgs84: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

data class PedestrianRoutePath(
    val points: List<PedestrianCoordinate>
)

data class PedestrianRoute(
    val rawDistanceMeters: Double,
    val rawTimeMinutes: Double,
    val paths: List<PedestrianRoutePath>
)

sealed interface RouteDetailWalkingState {
    data object Loading : RouteDetailWalkingState
    data class CsdiSuccess(val route: PedestrianRoute) : RouteDetailWalkingState
    data class CitybusFallback(val distanceMeters: Int?) : RouteDetailWalkingState
    data object SameStop : RouteDetailWalkingState
}

object PedestrianRouteRounding {
    fun totalDistanceMeters(rawDistances: List<Double>): Int {
        require(rawDistances.isNotEmpty()) { "At least one distance is required" }
        require(rawDistances.all { it.isFinite() && it > 0.0 }) { "Distances must be finite and positive" }
        return ceil(rawDistances.sum()).toInt()
    }

    fun segmentDistanceMeters(rawDistance: Double): Int {
        require(rawDistance.isFinite() && rawDistance > 0.0) { "Distance must be finite and positive" }
        return ceil(rawDistance).toInt()
    }

    fun segmentMinutes(rawMinutes: Double): Int {
        require(rawMinutes.isFinite() && rawMinutes > 0.0) { "Time must be finite and positive" }
        return maxOf(1, ceil(rawMinutes).toInt())
    }
}
