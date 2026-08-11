package com.golink.busiscoming.ui.main

internal object HeadingRotation {
    fun shortestDelta(fromDegrees: Float, toDegrees: Float): Float {
        val from = normalize(fromDegrees)
        val to = normalize(toDegrees)
        return (to - from + 540f) % 360f - 180f
    }

    fun shortestTarget(fromDegrees: Float, toDegrees: Float): Float =
        fromDegrees + shortestDelta(fromDegrees, toDegrees)

    private fun normalize(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f
}
