package com.example.busiscoming.data.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GeoDistanceCalculator {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun distanceMeters(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double
    ): Int {
        if (!fromLatitude.isFinite() || !fromLongitude.isFinite() ||
            !toLatitude.isFinite() || !toLongitude.isFinite()
        ) {
            return 0
        }
        val startLat = Math.toRadians(fromLatitude)
        val endLat = Math.toRadians(toLatitude)
        val deltaLat = Math.toRadians(toLatitude - fromLatitude)
        val deltaLon = Math.toRadians(toLongitude - fromLongitude)
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(startLat) * cos(endLat) * sin(deltaLon / 2) * sin(deltaLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (EARTH_RADIUS_METERS * c).roundToInt().coerceAtLeast(0)
    }
}
