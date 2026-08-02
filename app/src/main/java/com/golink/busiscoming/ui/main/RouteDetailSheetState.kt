package com.golink.busiscoming.ui.main

import kotlin.math.roundToInt

enum class RouteDetailSheetDetent {
    SUMMARY,
    HALF,
    FULL
}

data class RouteDetailSheetMetrics(
    val summaryHeight: Int,
    val halfExpandedRatio: Float
)

object RouteDetailSheetPolicy {
    fun metrics(parentHeight: Int, summaryContentHeight: Int): RouteDetailSheetMetrics {
        require(parentHeight > 0) { "Parent height must be positive" }
        val summaryTarget = (parentHeight * SUMMARY_TARGET_RATIO).roundToInt()
        val maxVisibleHeight = (parentHeight * MAX_VISIBLE_RATIO).roundToInt()
        val summaryHeight = maxOf(summaryTarget, summaryContentHeight).coerceAtMost(maxVisibleHeight)
        val halfHeight = maxOf((parentHeight * HALF_TARGET_RATIO).roundToInt(), summaryHeight)
            .coerceAtMost(maxVisibleHeight)
        return RouteDetailSheetMetrics(
            summaryHeight = summaryHeight,
            halfExpandedRatio = halfHeight.toFloat() / parentHeight.toFloat()
        )
    }

    fun onHandleClick(current: RouteDetailSheetDetent): RouteDetailSheetDetent {
        return if (current == RouteDetailSheetDetent.FULL) {
            RouteDetailSheetDetent.SUMMARY
        } else {
            RouteDetailSheetDetent.FULL
        }
    }

    fun onSummaryContentSwipeUp(): RouteDetailSheetDetent = RouteDetailSheetDetent.FULL

    fun onDownwardSettle(current: RouteDetailSheetDetent): RouteDetailSheetDetent {
        return when (current) {
            RouteDetailSheetDetent.FULL -> RouteDetailSheetDetent.HALF
            RouteDetailSheetDetent.HALF,
            RouteDetailSheetDetent.SUMMARY -> RouteDetailSheetDetent.SUMMARY
        }
    }

    private const val SUMMARY_TARGET_RATIO = 0.27f
    private const val HALF_TARGET_RATIO = 0.55f
    private const val MAX_VISIBLE_RATIO = 0.95f
}
