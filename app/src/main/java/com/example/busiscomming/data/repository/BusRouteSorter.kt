package com.example.busiscomming.data.repository

import com.example.busiscomming.data.model.BusRouteOption
import com.example.busiscomming.data.model.SortDirection
import com.example.busiscomming.data.model.SortField

object BusRouteSorter {
    fun sort(
        routes: List<BusRouteOption>,
        field: SortField,
        direction: SortDirection
    ): List<BusRouteOption> {
        val sorted = when (field) {
            SortField.PRICE -> routes.sortedBy { it.priceHkd }
            SortField.WAIT_TIME -> routes.sortedBy { it.waitMinutes }
        }
        return if (direction == SortDirection.ASC) sorted else sorted.asReversed()
    }
}
