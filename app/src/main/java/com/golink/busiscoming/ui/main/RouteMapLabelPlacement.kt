package com.golink.busiscoming.ui.main

enum class RouteMapLabelSide {
    RIGHT,
    LEFT,
    TOP,
    BOTTOM
}

data class RouteMapLabelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun intersectionArea(other: RouteMapLabelRect): Float {
        val width = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0f)
        val height = (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0f)
        return width * height
    }

    fun outsideArea(safe: RouteMapLabelRect): Float {
        val own = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
        return own - intersectionArea(safe)
    }
}

data class RouteMapLabelCandidate(
    val side: RouteMapLabelSide,
    val rect: RouteMapLabelRect
)

object RouteMapLabelPlacementPolicy {
    fun choose(
        candidates: List<RouteMapLabelCandidate>,
        safeRect: RouteMapLabelRect,
        occupied: List<RouteMapLabelRect>,
        critical: Boolean,
        previousSide: RouteMapLabelSide?
    ): RouteMapLabelCandidate? {
        if (candidates.isEmpty()) return null
        val costs = candidates.associateWith { candidate ->
            candidate.rect.outsideArea(safeRect) * OUTSIDE_WEIGHT +
                occupied.sumOf { candidate.rect.intersectionArea(it).toDouble() }.toFloat()
        }
        previousSide?.let { side ->
            candidates.firstOrNull { it.side == side && costs.getValue(it) == 0f }
                ?.let { return it }
        }
        candidates.firstOrNull { costs.getValue(it) == 0f }?.let { return it }
        if (!critical) return null
        return candidates.minBy { costs.getValue(it) }
    }

    private const val OUTSIDE_WEIGHT = 4f
}
