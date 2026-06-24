package com.example.busiscoming.ui.common

import kotlin.math.max
import kotlin.math.min

object PlaceCandidatePresentationPolicy {
    private const val MAX_CANDIDATES = 100
    private const val MIN_VISIBLE_ROWS = 3
    private const val MAX_VISIBLE_ROWS = 6

    fun <T> limit(items: List<T>): List<T> = items.take(MAX_CANDIDATES)

    fun heightPx(
        availableHeightPx: Int,
        rowHeightPx: Int,
        itemCount: Int
    ): Int {
        if (availableHeightPx <= 0 || rowHeightPx <= 0 || itemCount <= 0) return 0
        val contentHeight = rowHeightPx * itemCount
        val maxHeight = rowHeightPx * MAX_VISIBLE_ROWS
        val preferredHeight = min(contentHeight, maxHeight)
        val minimumHeight = rowHeightPx * min(itemCount, MIN_VISIBLE_ROWS)
        val completeRowsHeight = rowHeightPx * (availableHeightPx / rowHeightPx).coerceAtLeast(1)
        return when {
            availableHeightPx >= preferredHeight -> preferredHeight
            availableHeightPx >= minimumHeight -> min(contentHeight, completeRowsHeight)
            else -> min(contentHeight, completeRowsHeight)
        }
    }
}
