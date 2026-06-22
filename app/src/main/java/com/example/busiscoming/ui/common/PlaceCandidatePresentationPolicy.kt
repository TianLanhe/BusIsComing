package com.example.busiscoming.ui.common

import kotlin.math.max
import kotlin.math.min

object PlaceCandidatePresentationPolicy {
    private const val MAX_CANDIDATES = 100
    private const val VISIBLE_HEIGHT_RATIO = 0.4f
    private const val MIN_VISIBLE_ROWS = 3

    fun <T> limit(items: List<T>): List<T> = items.take(MAX_CANDIDATES)

    fun heightPx(
        visibleHeightPx: Int,
        rowHeightPx: Int,
        itemCount: Int
    ): Int {
        if (visibleHeightPx <= 0 || rowHeightPx <= 0 || itemCount <= 0) return 0
        val contentHeight = rowHeightPx * itemCount
        val ratioLimit = (visibleHeightPx * VISIBLE_HEIGHT_RATIO).toInt()
        val minimumRowsHeight = rowHeightPx * MIN_VISIBLE_ROWS
        return min(contentHeight, max(ratioLimit, minimumRowsHeight))
    }
}
