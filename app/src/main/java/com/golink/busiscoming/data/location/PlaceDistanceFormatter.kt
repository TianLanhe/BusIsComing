package com.golink.busiscoming.data.location

import java.util.Locale
import kotlin.math.roundToInt

object PlaceDistanceFormatter {
    fun compact(distanceMeters: Int): String {
        val meters = distanceMeters.coerceAtLeast(0)
        return if (meters < 1000) {
            "${meters}m"
        } else {
            String.format(Locale.US, "%.1fkm", meters / 1000.0)
        }
    }

    fun compact(distanceMeters: Double): String {
        return compact(distanceMeters.roundToInt())
    }

}
