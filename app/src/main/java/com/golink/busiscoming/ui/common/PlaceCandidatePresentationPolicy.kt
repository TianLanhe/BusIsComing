package com.golink.busiscoming.ui.common

import kotlin.math.min

object PlaceCandidatePresentationPolicy {
    private const val MAX_CANDIDATES = 100
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
        val completeRows = availableHeightPx / rowHeightPx
        val visibleRows = min(itemCount, min(resolvedMaxRows, completeRows))
        return rowHeightPx * visibleRows
    }

    fun editorBootstrapHeightPx(
        availableHeightPx: Int,
        rowHeightPx: Int,
        itemCount: Int
    ): Int {
        if (rowHeightPx <= 0 || itemCount <= 0 || availableHeightPx >= rowHeightPx) return 0
        return rowHeightPx
    }
}
