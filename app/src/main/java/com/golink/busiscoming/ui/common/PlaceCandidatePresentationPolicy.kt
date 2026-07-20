package com.golink.busiscoming.ui.common

import kotlin.math.max
import kotlin.math.min

object PlaceCandidatePresentationPolicy {
    private const val MAX_CANDIDATES = 100
    private const val MIN_VISIBLE_ROWS = 3
    const val DEFAULT_MAX_VISIBLE_ROWS = 6

    fun <T> limit(items: List<T>): List<T> = items.take(MAX_CANDIDATES)

    fun heightPx(
        availableHeightPx: Int,
        rowHeightPx: Int,
        itemCount: Int,
        maxVisibleRows: Int = DEFAULT_MAX_VISIBLE_ROWS
    ): Int {
        if (availableHeightPx <= 0 || rowHeightPx <= 0 || itemCount <= 0) return 0
        val resolvedMaxRows = maxVisibleRows.coerceAtLeast(1)
        val contentHeight = rowHeightPx * itemCount
        val maxHeight = rowHeightPx * resolvedMaxRows
        val preferredHeight = min(contentHeight, maxHeight)
        val minimumHeight = rowHeightPx * min(itemCount, min(MIN_VISIBLE_ROWS, resolvedMaxRows))
        val completeRowsHeight = rowHeightPx * (availableHeightPx / rowHeightPx).coerceAtLeast(1)
        return when {
            availableHeightPx >= preferredHeight -> preferredHeight
            availableHeightPx >= minimumHeight -> min(contentHeight, completeRowsHeight)
            else -> min(contentHeight, completeRowsHeight)
        }
    }
}
